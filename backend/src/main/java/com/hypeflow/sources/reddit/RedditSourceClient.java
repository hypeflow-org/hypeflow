package com.hypeflow.sources.reddit;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypeflow.model.TimeBucket;
import com.hypeflow.model.TimeInterval;
import com.hypeflow.model.TimeSeries;
import com.hypeflow.sources.SourceClient;
import com.hypeflow.sources.SourceClientException;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reddit source client that aggregates post counts per day using /search?sort=new.
 * <p>
 * WARNING / DISCLAIMER:
 * <ul>
 *   <li>This client assumes access to the official Reddit Data API via OAuth
 *       (client_credentials) against {@code https://oauth.reddit.com}.</li>
 *   <li>Actual availability and terms of use are governed by Reddit’s current
 *       policies (Responsible Builder Policy, Developer Terms, Data API Terms, etc.),
 *       which may restrict or prohibit this kind of access. This source MUST NOT be
 *       enabled in any environment that does not have explicit approval from Reddit.</li>
 *   <li>Pagination is limited to {@link #MAX_PAGES} * {@link #PAGE_SIZE} posts
 *       (~ 1000 newest posts per query), so for active topics and long date ranges
 *       older posts will be silently ignored and the time series will be incomplete
 *       and biased towards more recent days.</li>
 *   <li>Aggregation is based on the UTC {@code created_utc} timestamp only; local
 *       time zones, edits and deletions are not taken into account.</li>
 *   <li>This implementation is intended for low-volume, non-commercial analytics
 *       and should not be used as a bulk data export mechanism.</li>
 * </ul>
 */
public class RedditSourceClient implements SourceClient {

    private static final Logger log = LoggerFactory.getLogger(RedditSourceClient.class);
    private static final String SOURCE_ID = "reddit";
    private static final String BASE_URL = "https://oauth.reddit.com/search";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RedditAuthClient redditAuthClient;

    public RedditSourceClient(OkHttpClient httpClient,
                              ObjectMapper objectMapper,
                              RedditAuthClient redditAuthClient) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.redditAuthClient = redditAuthClient;
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

        Map<LocalDate, Integer> counts = new HashMap<>();
        String after = null;
        int page = 0;
        boolean reachedStartDate = false;

        while (page < MAX_PAGES && !reachedStartDate) {
            HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(BASE_URL)).newBuilder()
                    .addQueryParameter("q", topic)
                    .addQueryParameter("sort", "new")
                    .addQueryParameter("limit", String.valueOf(PAGE_SIZE))
                    .addQueryParameter("type", "link");

            if (after != null) {
                urlBuilder.addQueryParameter("after", after);
            }

            Request request = new Request.Builder()
                    .url(urlBuilder.build())
                    .header("Authorization", "bearer " + redditAuthClient.getAccessToken())
                    .header("User-Agent", redditAuthClient.getUserAgent())
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    throw new SourceClientException(
                            SOURCE_ID,
                            "HTTP " + response.code() + " from Reddit search: " + responseBody
                    );
                }

                RedditSearchResponse body = objectMapper.readValue(responseBody, RedditSearchResponse.class);
                if (body == null || body.data() == null || body.data().children() == null || body.data().children().isEmpty()) {
                    break;
                }

                for (RedditChild child : body.data().children()) {
                    if (child == null || child.data() == null) {
                        continue;
                    }
                    long createdUtc = child.data().createdUtc();
                    LocalDate date = Instant.ofEpochSecond(createdUtc).atZone(ZoneOffset.UTC).toLocalDate();

                    if (date.isAfter(endInclusive)) {
                        continue;
                    }

                    if (date.isBefore(startInclusive)) {
                        reachedStartDate = true;
                        break;
                    }

                    counts.merge(date, 1, Integer::sum);
                }

                after = body.data().after();
                page++;

                if (after == null) {
                    break;
                }
            } catch (IOException e) {
                throw new SourceClientException(SOURCE_ID, "I/O error calling Reddit API", e);
            }
        }

        List<TimeBucket> buckets = buildContinuousBuckets(startInclusive, endInclusive, counts);

        return new TimeSeries(
                SOURCE_ID,
                topic,
                TimeInterval.DAY,
                buckets
        );
    }

    private static List<TimeBucket> buildContinuousBuckets(
            LocalDate start, LocalDate end, Map<LocalDate, Integer> counts) {
        List<TimeBucket> buckets = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            buckets.add(new TimeBucket(d, counts.getOrDefault(d, 0)));
        }
        return buckets;
    }

    private record RedditSearchResponse(RedditListingData data) { }

    private record RedditListingData(List<RedditChild> children, String after) { }

    private record RedditChild(RedditPostData data) { }

    private record RedditPostData(@JsonProperty("created_utc") long createdUtc) { }
}
