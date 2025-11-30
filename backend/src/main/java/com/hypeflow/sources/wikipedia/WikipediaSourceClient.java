package com.hypeflow.sources.wikipedia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypeflow.model.TimeBucket;
import com.hypeflow.model.TimeInterval;
import com.hypeflow.model.TimeSeries;
import com.hypeflow.sources.SourceClient;
import com.hypeflow.sources.SourceClientException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SourceClient implementation for Wikipedia Pageviews API.
 */
public class WikipediaSourceClient implements SourceClient {

    private static final String SOURCE_ID = "wikipedia";
    private static final String BASE_URL = "https://wikimedia.org/api/rest_v1";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String project;
    private final String access;
    private final String agent;
    private final String userAgentHeader;

    public WikipediaSourceClient(OkHttpClient httpClient,
                                 ObjectMapper objectMapper,
                                 String project,
                                 String access,
                                 String agent,
                                 String userAgentHeader) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.project = project;
        this.access = access;
        this.agent = agent;
        this.userAgentHeader = userAgentHeader;
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

        String article = toArticleTitle(topic);
        String start = startInclusive.format(DATE_FORMATTER) + "00";
        String end = endInclusive.format(DATE_FORMATTER) + "00";

        String url = String.format(
                "%s/metrics/pageviews/per-article/%s/%s/%s/%s/daily/%s/%s",
                BASE_URL, project, access, agent, article, start, end
        );

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", userAgentHeader)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 404) {
                List<TimeBucket> buckets = buildContinuousBuckets(
                        startInclusive, endInclusive, Map.of()
                );
                return new TimeSeries(SOURCE_ID, topic, TimeInterval.DAY, buckets);
            }

            if (!response.isSuccessful()) {
                throw new SourceClientException(
                        SOURCE_ID,
                        "HTTP " + response.code() + " from Wikimedia Pageviews"
                );
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            WikiPageviewsResponse body = objectMapper.readValue(
                    responseBody, WikiPageviewsResponse.class
            );

            Map<LocalDate, Integer> counter = new HashMap<>();
            if (body.items() != null) {
                for (WikiPageviewsItem item : body.items()) {
                    LocalDate date = parseTimestamp(item.timestamp());
                    counter.put(date, (int) Math.min(item.views(), Integer.MAX_VALUE));
                }
            }

            List<TimeBucket> buckets = buildContinuousBuckets(
                    startInclusive, endInclusive, counter
            );

            return new TimeSeries(SOURCE_ID, topic, TimeInterval.DAY, buckets);

        } catch (IOException e) {
            throw new SourceClientException(
                    SOURCE_ID, "I/O error calling Wikimedia Pageviews", e
            );
        }
    }

    private static LocalDate parseTimestamp(String timestamp) {
        String datePart = timestamp.substring(0, 8);
        return LocalDate.parse(datePart, DATE_FORMATTER);
    }

    private static List<TimeBucket> buildContinuousBuckets(
            LocalDate start, LocalDate end, Map<LocalDate, Integer> counts) {
        List<TimeBucket> buckets = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            buckets.add(new TimeBucket(d, counts.getOrDefault(d, 0)));
        }
        return buckets;
    }

    private static String toArticleTitle(String topic) {
        String normalized = topic.trim().replace(' ', '_');
        return URLEncoder.encode(normalized, StandardCharsets.UTF_8);
    }
}
