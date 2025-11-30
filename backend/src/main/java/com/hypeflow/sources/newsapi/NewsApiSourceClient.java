package com.hypeflow.sources.newsapi;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WARNING: This client uses NewsAPI /v2/everything with sortBy=publishedAt and a small pageSize.
 * That means we only see the most recent N articles in the [from..to] window, not the full coverage.
 * Time series is therefore approximate and biased towards the latest days in the range.
 */
public class NewsApiSourceClient implements SourceClient {

    private static final Logger log = LoggerFactory.getLogger(NewsApiSourceClient.class);

    private static final String SOURCE_ID = "newsapi";
    private static final String BASE_URL = "https://newsapi.org/v2/everything";
    private static final int PAGE_SIZE = 10;  // Maximum allowed by NewsAPI
    private static final int MAX_PAGES = 1;    // Free tier: only 1 page to save daily quota (100 requests/day)

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String language;

    public NewsApiSourceClient(OkHttpClient httpClient,
                               ObjectMapper objectMapper,
                               String apiKey,
                               String language) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.language = language;
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

        Map<LocalDate, Integer> counter = new HashMap<>();

        int page = 1;
        while (page <= MAX_PAGES) {
            String url = buildUrl(topic, startInclusive, endInclusive, page);
            Request request = new Request.Builder()
                    .url(url)
                    .header("X-Api-Key", apiKey)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    // HTTP 426 means we've hit the free tier limit - stop pagination gracefully
                    if (response.code() == 426) {
                        log.warn("NewsAPI free tier limit reached at page {}, stopping pagination", page);
                        break;
                    }

                    log.error("NewsAPI Error (HTTP {}): {}", response.code(), responseBody);
                    throw new SourceClientException(
                            SOURCE_ID,
                            "HTTP " + response.code() + " from NewsAPI: " + responseBody
                    );
                }

                NewsApiResponse body = objectMapper.readValue(responseBody, NewsApiResponse.class);

                if (!"ok".equals(body.status())) {
                    throw new SourceClientException(
                            SOURCE_ID,
                            "NewsAPI error: " + body.code() + " - " + body.message()
                    );
                }

                List<NewsApiArticle> articles = body.articles();
                if (articles == null || articles.isEmpty()) {
                    break;
                }

                log.debug("NewsAPI returned {} articles for page {}", articles.size(), page);

                for (NewsApiArticle article : articles) {
                    if (article.publishedAt() != null) {
                        LocalDate date = parsePublishedAt(article.publishedAt());
                        log.trace("Article {} -> parsed as {}", article.publishedAt(), date);
                        if (!date.isBefore(startInclusive) && !date.isAfter(endInclusive)) {
                            counter.merge(date, 1, Integer::sum);
                        } else {
                            log.trace("Skipping article outside range [{} to {}]", startInclusive, endInclusive);
                        }
                    }
                }

                if (articles.size() < PAGE_SIZE) {
                    break;
                }

                page++;
            } catch (IOException e) {
                throw new SourceClientException(SOURCE_ID, "I/O error calling NewsAPI", e);
            }
        }

        List<TimeBucket> buckets = buildContinuousBuckets(startInclusive, endInclusive, counter);

        return new TimeSeries(
                SOURCE_ID,
                topic,
                TimeInterval.DAY,
                buckets
        );
    }

    private String buildUrl(String topic, LocalDate from, LocalDate to, int page) {
        String encodedTopic = URLEncoder.encode(topic, StandardCharsets.UTF_8);
        return String.format(
                "%s?q=%s&from=%s&to=%s&language=%s&sortBy=publishedAt&pageSize=%d&page=%d",
                BASE_URL,
                encodedTopic,
                from,
                to,
                language,
                PAGE_SIZE,
                page
        );
    }

    private static LocalDate parsePublishedAt(String publishedAt) {
        Instant instant = Instant.parse(publishedAt);
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC).toLocalDate();
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
