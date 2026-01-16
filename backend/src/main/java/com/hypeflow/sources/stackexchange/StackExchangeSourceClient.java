package com.hypeflow.sources.stackexchange;

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

public class StackExchangeSourceClient implements SourceClient {

    private static final Logger log = LoggerFactory.getLogger(StackExchangeSourceClient.class);
    private static final String SOURCE_ID = "stackexchange";
    private static final int MAX_RANGE_DAYS = 30;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String site;

    public StackExchangeSourceClient(OkHttpClient httpClient,
                                     ObjectMapper objectMapper,
                                     String baseUrl,
                                     String site) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.site = site;
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

        // +1 because between() is exclusive, but our range is inclusive on both ends
        long daysInclusive = ChronoUnit.DAYS.between(startInclusive, endInclusive) + 1;
        if (daysInclusive > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException(
                    "Date range exceeds maximum of " + MAX_RANGE_DAYS + " days for StackExchange"
            );
        }

        Map<LocalDate, Integer> counts = new HashMap<>();

        for (LocalDate date = startInclusive; !date.isAfter(endInclusive); date = date.plusDays(1)) {
            long fromDate = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
            long toDate = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

            String url = String.format(
                    "%s/2.3/search/advanced?site=%s&q=%s&fromdate=%d&todate=%d&pagesize=1",
                    baseUrl,
                    site,
                    URLEncoder.encode(topic, StandardCharsets.UTF_8),
                    fromDate,
                    toDate
            );

            log.debug("StackExchange request for {}: {}", date, url);

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    log.error("StackExchange Error (HTTP {}): {}", response.code(), responseBody);
                    throw new SourceClientException(
                            SOURCE_ID,
                            "HTTP " + response.code() + " from StackExchange"
                    );
                }

                JsonNode root = objectMapper.readTree(responseBody);

                int backoffSec = root.path("backoff").asInt(0);
                if (backoffSec > 0) {
                    log.warn("StackExchange backoff requested: {} seconds", backoffSec);
                    try {
                        Thread.sleep(backoffSec * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new SourceClientException(SOURCE_ID, "Interrupted during backoff", e);
                    }
                }

                if (root.hasNonNull("error_id")) {
                    String errorName = root.path("error_name").asText("unknown_error");
                    String errorMessage = root.path("error_message").asText("");
                    throw new SourceClientException(SOURCE_ID, errorName + ": " + errorMessage);
                }

                int total = root.path("total").asInt(-1);
                if (total < 0) {
                    JsonNode items = root.path("items");
                    total = items.isArray() ? items.size() : 0;
                }
                counts.put(date, total);

                log.debug("StackExchange {} -> {} questions", date, total);

            } catch (IOException e) {
                throw new SourceClientException(SOURCE_ID,
                        "StackExchange failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
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
