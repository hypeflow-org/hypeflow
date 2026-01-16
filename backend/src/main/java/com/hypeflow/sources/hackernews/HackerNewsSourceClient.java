package com.hypeflow.sources.hackernews;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypeflow.model.TimeBucket;
import com.hypeflow.model.TimeInterval;
import com.hypeflow.model.TimeSeries;
import com.hypeflow.sources.SourceClient;
import com.hypeflow.sources.SourceClientException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HackerNewsSourceClient implements SourceClient {

    private static final Logger log = LoggerFactory.getLogger(HackerNewsSourceClient.class);
    private static final String SOURCE_ID = "hackernews";
    private static final int MAX_RANGE_DAYS = 31;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public HackerNewsSourceClient(OkHttpClient httpClient,
                                  ObjectMapper objectMapper,
                                  String baseUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public String sourceId() {
        return SOURCE_ID;
    }

    @Override
    public TimeSeries fetchDailyTimeSeries(String topic,
                                           LocalDate startInclusive,
                                           LocalDate endInclusive) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic cannot be null or blank");
        }
        if (startInclusive.isAfter(endInclusive)) {
            throw new IllegalArgumentException("startInclusive cannot be after endInclusive");
        }

        long daysInclusive = ChronoUnit.DAYS.between(startInclusive, endInclusive) + 1;
        if (daysInclusive > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException(
                    "Date range exceeds maximum of " + MAX_RANGE_DAYS + " days for HackerNews"
            );
        }

        Map<LocalDate, Integer> counts = new HashMap<>();

        for (LocalDate date = startInclusive; !date.isAfter(endInclusive); date = date.plusDays(1)) {
            long dayStart = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
            long dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

            String url = String.format(
                    "%s/search_by_date?query=%s&tags=story&numericFilters=created_at_i>=%d,created_at_i<%d",
                    baseUrl,
                    URLEncoder.encode(topic, StandardCharsets.UTF_8),
                    dayStart,
                    dayEnd
            );

            log.debug("HackerNews request for {}: {}", date, url);

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    log.error("HackerNews Error (HTTP {}): {}", response.code(), responseBody);
                    throw new SourceClientException(
                            SOURCE_ID,
                            "HTTP " + response.code() + " from HackerNews Algolia"
                    );
                }

                JsonNode root = objectMapper.readTree(responseBody);
                int nbHits = root.path("nbHits").asInt(0);
                counts.put(date, nbHits);

                log.debug("HackerNews {} -> {} hits", date, nbHits);

            } catch (IOException e) {
                throw new SourceClientException(SOURCE_ID, "I/O error calling HackerNews", e);
            }
        }

        List<TimeBucket> buckets = buildContinuousBuckets(startInclusive, endInclusive, counts);
        return new TimeSeries(SOURCE_ID, topic, TimeInterval.DAY, buckets);
    }

    private static List<TimeBucket> buildContinuousBuckets(
            LocalDate start, LocalDate end, Map<LocalDate, Integer> counts) {
        List<TimeBucket> buckets = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            buckets.add(new TimeBucket(d, counts.getOrDefault(d, 0)));
        }
        return buckets;
    }
}
