package com.hypeflow.sources.gdelt;

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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GdeltSourceClient implements SourceClient {

    private static final Logger log = LoggerFactory.getLogger(GdeltSourceClient.class);
    private static final String SOURCE_ID = "gdelt";
    private static final DateTimeFormatter GDELT_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public GdeltSourceClient(OkHttpClient httpClient,
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

        String startDt = startInclusive.atStartOfDay(ZoneOffset.UTC).format(GDELT_DATE_FMT);
        String endDt = endInclusive.atTime(23, 59, 59).atOffset(ZoneOffset.UTC).format(GDELT_DATE_FMT);

        String url = String.format(
                "%s?query=%s&mode=timelinevolraw&format=json&startdatetime=%s&enddatetime=%s",
                baseUrl,
                URLEncoder.encode(topic, StandardCharsets.UTF_8),
                startDt,
                endDt
        );

        log.debug("GDELT request URL: {}", url);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("GDELT Error (HTTP {}): {}", response.code(), responseBody);
                throw new SourceClientException(
                        SOURCE_ID,
                        "HTTP " + response.code() + " from GDELT"
                );
            }

            Map<LocalDate, Integer> counts = parseGdeltResponse(responseBody);
            List<TimeBucket> buckets = buildContinuousBuckets(startInclusive, endInclusive, counts);

            return new TimeSeries(SOURCE_ID, topic, TimeInterval.DAY, buckets);

        } catch (IOException e) {
            throw new SourceClientException(SOURCE_ID, "I/O error calling GDELT", e);
        }
    }

    private Map<LocalDate, Integer> parseGdeltResponse(String json) throws IOException {
        Map<LocalDate, Integer> counts = new HashMap<>();

        if (json == null || json.isBlank()) {
            return counts;
        }

        JsonNode root = objectMapper.readTree(json);
        JsonNode timeline = root.path("timeline");

        if (timeline.isArray()) {
            for (JsonNode series : timeline) {
                JsonNode data = series.path("data");
                if (data.isArray()) {
                    for (JsonNode point : data) {
                        String dateStr = point.path("date").asText();
                        int value = point.path("value").asInt(0);

                        if (dateStr != null && dateStr.length() >= 8) {
                            try {
                                LocalDate date = LocalDate.parse(
                                        dateStr.substring(0, 8),
                                        DateTimeFormatter.BASIC_ISO_DATE
                                );
                                counts.merge(date, value, Integer::sum);
                            } catch (Exception e) {
                                log.warn("Failed to parse GDELT date: {}", dateStr);
                            }
                        }
                    }
                }
            }
        }

        return counts;
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
