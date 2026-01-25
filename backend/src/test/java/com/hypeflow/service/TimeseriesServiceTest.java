package com.hypeflow.service;

import com.hypeflow.api.TimeseriesRequest;
import com.hypeflow.api.TimeseriesResponse;
import com.hypeflow.config.CacheProperties;
import com.hypeflow.model.SourceDescriptor;
import com.hypeflow.model.TimeBucket;
import com.hypeflow.model.TimeInterval;
import com.hypeflow.model.TimeSeries;
import com.hypeflow.repo.SearchHistoryRepository;
import com.hypeflow.sources.SourceClient;
import com.hypeflow.sources.SourceClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class TimeseriesServiceTest {

    private RedisTemplate<String, Object> redis;
    private HashOperations<String, Object, Object> hashOps;
    private ValueOperations<String, Object> valueOps;
    private SearchHistoryRepository repo;
    private SourceRegistry sourceRegistry;
    private CacheProperties cacheProperties;

    @BeforeEach
    void setUp() {
        redis = mock(RedisTemplate.class);
        hashOps = mock(HashOperations.class);
        valueOps = mock(ValueOperations.class);
        repo = mock(SearchHistoryRepository.class);

        when(redis.opsForHash()).thenReturn(hashOps);
        when(redis.opsForValue()).thenReturn(valueOps);

        final Set<String> redisKeys = new HashSet<>();
        when(redis.hasKey(anyString())).thenAnswer(inv -> redisKeys.contains(inv.getArgument(0, String.class)));
        doAnswer(inv -> {
            redisKeys.add(inv.getArgument(0, String.class));
            return null;
        }).when(valueOps).set(anyString(), any(), anyLong(), any(TimeUnit.class));

        final Map<String, Map<String, Object>> redisHashStore = new HashMap<>();

        doAnswer(inv -> {
            String key = inv.getArgument(0);
            List<Object> fields = inv.getArgument(1);
            Map<String, Object> hash = redisHashStore.getOrDefault(key, new HashMap<>());
            List<Object> result = new ArrayList<>();
            for (Object f : fields) {
                result.add(hash.get(f.toString()));
            }
            return result;
        }).when(hashOps).multiGet(anyString(), anyList());

        doAnswer(inv -> {
            String key = inv.getArgument(0);
            Map<String, Object> entries = inv.getArgument(1);
            redisHashStore.computeIfAbsent(key, k -> new HashMap<>()).putAll(entries);
            return null;
        }).when(hashOps).putAll(anyString(), anyMap());

        cacheProperties = new CacheProperties();
        cacheProperties.setDayCacheTtlDays(7);
        cacheProperties.setErrorTtlMinutes(5);

        sourceRegistry = mock(SourceRegistry.class);
        when(sourceRegistry.get("sourceA")).thenReturn(new SourceDescriptor(
                "sourceA", "Source A", "Test source A", "cat", "unit", null, null, "url", 1.0, true));
        when(sourceRegistry.get("sourceB")).thenReturn(new SourceDescriptor(
                "sourceB", "Source B", "Test source B", "cat", "unit", null, null, "url", 1.0, true));
        when(sourceRegistry.get("limited")).thenReturn(new SourceDescriptor(
                "limited", "Limited", "Test limited source", "cat", "unit", 3, null, "url", 1.0, true));
    }

    @Test
    void multiSourceThenSingleSourceShouldHitCache() {
        TrackingSourceClient clientA = new TrackingSourceClient("sourceA", List.of(
                new TimeBucket(LocalDate.of(2026, 1, 10), 100),
                new TimeBucket(LocalDate.of(2026, 1, 11), 200)
        ));
        TrackingSourceClient clientB = new TrackingSourceClient("sourceB", List.of(
                new TimeBucket(LocalDate.of(2026, 1, 10), 50),
                new TimeBucket(LocalDate.of(2026, 1, 11), 60)
        ));

        TimeseriesDayCacheService dayCache = new TimeseriesDayCacheService(redis, cacheProperties);
        TimeseriesService service = new TimeseriesService(
                List.of(clientA, clientB), repo, dayCache, sourceRegistry);

        TimeseriesRequest req1 = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11),
                List.of("sourceA", "sourceB"));

        TimeseriesResponse resp1 = service.query(req1);
        assertFalse(resp1.fromCache());
        assertEquals(410, resp1.totalMentions());
        assertEquals(1, clientA.callCount);
        assertEquals(1, clientB.callCount);

        TimeseriesRequest req2 = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11),
                List.of("sourceA"));

        TimeseriesResponse resp2 = service.query(req2);
        assertTrue(resp2.fromCache());
        assertEquals(300, resp2.totalMentions());
        assertEquals(1, clientA.callCount);
        assertEquals(1, clientB.callCount);
    }

    @Test
    void singleSourceThenTwoSourcesSameRange() {
        TrackingSourceClient clientA = new TrackingSourceClient("sourceA", List.of(
                new TimeBucket(LocalDate.of(2026, 1, 10), 100),
                new TimeBucket(LocalDate.of(2026, 1, 11), 200)
        ));
        TrackingSourceClient clientB = new TrackingSourceClient("sourceB", List.of(
                new TimeBucket(LocalDate.of(2026, 1, 10), 50),
                new TimeBucket(LocalDate.of(2026, 1, 11), 60)
        ));

        TimeseriesDayCacheService dayCache = new TimeseriesDayCacheService(redis, cacheProperties);
        TimeseriesService service = new TimeseriesService(
                List.of(clientA, clientB), repo, dayCache, sourceRegistry);

        TimeseriesRequest req1 = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11),
                List.of("sourceA"));

        TimeseriesResponse resp1 = service.query(req1);
        assertFalse(resp1.fromCache());
        assertEquals(300, resp1.totalMentions());
        assertEquals(1, clientA.callCount);
        assertEquals(0, clientB.callCount);

        TimeseriesRequest req2 = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11),
                List.of("sourceA", "sourceB"));

        TimeseriesResponse resp2 = service.query(req2);
        assertFalse(resp2.fromCache());
        assertEquals(410, resp2.totalMentions());
        assertEquals(1, clientA.callCount);
        assertEquals(1, clientB.callCount);
    }

    @Test
    void yearThenMonthShouldHitCache() {
        List<TimeBucket> yearBuckets = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            int daysInMonth = LocalDate.of(2026, month, 1).lengthOfMonth();
            for (int day = 1; day <= daysInMonth; day++) {
                yearBuckets.add(new TimeBucket(LocalDate.of(2026, month, day), 10));
            }
        }

        TrackingSourceClient client = new TrackingSourceClient("sourceA", yearBuckets);

        TimeseriesDayCacheService dayCache = new TimeseriesDayCacheService(redis, cacheProperties);
        TimeseriesService service = new TimeseriesService(
                List.of(client), repo, dayCache, sourceRegistry);

        TimeseriesRequest yearReq = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                List.of("sourceA"));

        TimeseriesResponse yearResp = service.query(yearReq);
        assertFalse(yearResp.fromCache());
        assertEquals(1, client.callCount);

        TimeseriesRequest monthReq = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                List.of("sourceA"));

        TimeseriesResponse monthResp = service.query(monthReq);
        assertTrue(monthResp.fromCache());
        assertEquals(310, monthResp.totalMentions());
        assertEquals(1, client.callCount);
    }

    @Test
    void partialCoverageShouldFetchOnlyMissingIntervals() {
        TrackingSourceClient client = new TrackingSourceClient("sourceA", List.of(
                new TimeBucket(LocalDate.of(2026, 1, 10), 10),
                new TimeBucket(LocalDate.of(2026, 1, 11), 20),
                new TimeBucket(LocalDate.of(2026, 1, 12), 30),
                new TimeBucket(LocalDate.of(2026, 1, 13), 40),
                new TimeBucket(LocalDate.of(2026, 1, 14), 50)
        ));

        TimeseriesDayCacheService dayCache = new TimeseriesDayCacheService(redis, cacheProperties);
        TimeseriesService service = new TimeseriesService(
                List.of(client), repo, dayCache, sourceRegistry);

        TimeseriesRequest req1 = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 12),
                List.of("sourceA"));
        service.query(req1);
        assertEquals(1, client.callCount);

        client.callCount = 0;

        TimeseriesRequest req2 = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 14),
                List.of("sourceA"));

        TimeseriesResponse resp = service.query(req2);
        assertFalse(resp.fromCache());
        assertEquals(1, client.callCount);
        assertEquals(LocalDate.of(2026, 1, 13), client.lastRequestedStart);
        assertEquals(LocalDate.of(2026, 1, 14), client.lastRequestedEnd);
        assertEquals(150, resp.totalMentions());
    }

    @Test
    void fillingTheCacheWithMultipleRequests() {
        TrackingSourceClient client = new TrackingSourceClient("sourceA", List.of(
                new TimeBucket(LocalDate.of(2026, 1, 10), 10),
                new TimeBucket(LocalDate.of(2026, 1, 11), 20),
                new TimeBucket(LocalDate.of(2026, 1, 12), 30),
                new TimeBucket(LocalDate.of(2026, 1, 13), 40),
                new TimeBucket(LocalDate.of(2026, 1, 14), 50),
                new TimeBucket(LocalDate.of(2026, 1, 15), 60)
        ));

        TimeseriesDayCacheService dayCache = new TimeseriesDayCacheService(redis, cacheProperties);
        TimeseriesService service = new TimeseriesService(
                List.of(client), repo, dayCache, sourceRegistry);

        TimeseriesRequest req1 = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11),
                List.of("sourceA"));
        TimeseriesResponse resp = service.query(req1);
        assertEquals(1, client.callCount);
        assertFalse(resp.fromCache());


        TimeseriesRequest req2 = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 14), LocalDate.of(2026, 1, 15),
                List.of("sourceA"));
        resp = service.query(req2);
        assertFalse(resp.fromCache());
        assertEquals(2, client.callCount);

        TimeseriesRequest req3 = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 12), LocalDate.of(2026, 1, 13),
                List.of("sourceA"));
        resp = service.query(req3);
        assertFalse(resp.fromCache());
        assertEquals(3, client.callCount);

        TimeseriesRequest req4 = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 15),
                List.of("sourceA"));
        resp = service.query(req4);
        assertTrue(resp.fromCache());
        assertEquals(3, client.callCount);

        assertEquals(210, resp.totalMentions());
    }

    @Test
    void partialCacheMultipleIntervals() {
        TrackingSourceClient client = new TrackingSourceClient("sourceA", List.of(
                new TimeBucket(LocalDate.of(2026, 1, 10), 10),
                new TimeBucket(LocalDate.of(2026, 1, 11), 20),
                new TimeBucket(LocalDate.of(2026, 1, 12), 30),
                new TimeBucket(LocalDate.of(2026, 1, 13), 40),
                new TimeBucket(LocalDate.of(2026, 1, 14), 50)
        ));

        TimeseriesDayCacheService dayCache = new TimeseriesDayCacheService(redis, cacheProperties);
        TimeseriesService service = new TimeseriesService(
                List.of(client), repo, dayCache, sourceRegistry);

        TimeseriesRequest req1 = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 11),
                List.of("sourceA"));
        TimeseriesResponse resp = service.query(req1);
        assertEquals(1, client.callCount);
        assertFalse(resp.fromCache());

        TimeseriesRequest req2 = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 13), LocalDate.of(2026, 1, 13),
                List.of("sourceA"));
        resp = service.query(req2);
        assertEquals(2, client.callCount);
        assertFalse(resp.fromCache());


        TimeseriesRequest req3 = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 14),
                List.of("sourceA"));
        resp = service.query(req3);
        assertFalse(resp.fromCache());
        assertEquals(5, client.callCount);

        assertEquals(LocalDate.of(2026, 1, 14), client.lastRequestedStart);
        assertEquals(LocalDate.of(2026, 1, 14), client.lastRequestedEnd);

        assertEquals(150, resp.totalMentions());
    }

    @Test
    void fromCacheTrueOnlyWhenNoExternalCalls() {
        TrackingSourceClient client = new TrackingSourceClient("sourceA", List.of(
                new TimeBucket(LocalDate.of(2026, 1, 10), 100)
        ));

        TimeseriesDayCacheService dayCache = new TimeseriesDayCacheService(redis, cacheProperties);
        TimeseriesService service = new TimeseriesService(
                List.of(client), repo, dayCache, sourceRegistry);

        TimeseriesRequest req = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10),
                List.of("sourceA"));

        TimeseriesResponse resp1 = service.query(req);
        assertFalse(resp1.fromCache());

        TimeseriesResponse resp2 = service.query(req);
        assertTrue(resp2.fromCache());
        assertEquals(1, client.callCount);
    }

    @Test
    void rangeTooLargeShouldReturnErrorAndSkipFetch() {
        TrackingSourceClient limitedClient = new TrackingSourceClient("limited", List.of(
                new TimeBucket(LocalDate.of(2026, 1, 10), 10),
                new TimeBucket(LocalDate.of(2026, 1, 11), 20),
                new TimeBucket(LocalDate.of(2026, 1, 12), 30),
                new TimeBucket(LocalDate.of(2026, 1, 13), 40),
                new TimeBucket(LocalDate.of(2026, 1, 14), 50)
        ));

        TimeseriesDayCacheService dayCache = new TimeseriesDayCacheService(redis, cacheProperties);
        TimeseriesService service = new TimeseriesService(
                List.of(limitedClient), repo, dayCache, sourceRegistry);

        TimeseriesRequest req = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 14),
                List.of("limited"));

        TimeseriesResponse resp = service.query(req);

        assertEquals(0, limitedClient.callCount);
        assertEquals(1, resp.errors().size());
        assertEquals("RANGE_TOO_LARGE", resp.errors().get(0).code());
        assertTrue(resp.errors().get(0).message().contains("3"));
        assertTrue(resp.sources().isEmpty());
    }

    @Test
    void rangeTooLargeButWithCacheShouldWork() {
        TrackingSourceClient limitedClient = new TrackingSourceClient("limited", List.of(
                new TimeBucket(LocalDate.of(2026, 1, 10), 10),
                new TimeBucket(LocalDate.of(2026, 1, 11), 20),
                new TimeBucket(LocalDate.of(2026, 1, 12), 30),
                new TimeBucket(LocalDate.of(2026, 1, 13), 40),
                new TimeBucket(LocalDate.of(2026, 1, 14), 50)
        ));

        TimeseriesDayCacheService dayCache = new TimeseriesDayCacheService(redis, cacheProperties);
        TimeseriesService service = new TimeseriesService(
                List.of(limitedClient), repo, dayCache, sourceRegistry);

        TimeseriesRequest req = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 12),
                List.of("limited"));
        TimeseriesResponse resp = service.query(req);
        assertEquals(1, limitedClient.callCount);
        assertEquals(0, resp.errors().size());

        req = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 14),
                List.of("limited"));
        resp = service.query(req);
        assertEquals(2, limitedClient.callCount);
        assertEquals(0, resp.errors().size());

        assertEquals(150, resp.totalMentions());
    }

    @Test
    void largeRangeButWithFullCacheHit() {
        TrackingSourceClient limitedClient = new TrackingSourceClient("limited", List.of(
                new TimeBucket(LocalDate.of(2026, 1, 10), 10),
                new TimeBucket(LocalDate.of(2026, 1, 11), 20),
                new TimeBucket(LocalDate.of(2026, 1, 12), 30),
                new TimeBucket(LocalDate.of(2026, 1, 13), 40),
                new TimeBucket(LocalDate.of(2026, 1, 14), 50),
                new TimeBucket(LocalDate.of(2026, 1, 14), 60)
        ));

        TimeseriesDayCacheService dayCache = new TimeseriesDayCacheService(redis, cacheProperties);
        TimeseriesService service = new TimeseriesService(
                List.of(limitedClient), repo, dayCache, sourceRegistry);

        TimeseriesRequest req = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 12),
                List.of("limited"));
        TimeseriesResponse resp = service.query(req);
        assertEquals(1, limitedClient.callCount);
        assertEquals(0, resp.errors().size());

        req = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 13), LocalDate.of(2026, 1, 15),
                List.of("limited"));
        resp = service.query(req);
        assertEquals(2, limitedClient.callCount);
        assertEquals(0, resp.errors().size());

        req = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 15),
                List.of("limited"));
        resp = service.query(req);
        assertEquals(3, limitedClient.callCount);
        assertEquals(0, resp.errors().size());

        assertEquals(210, resp.totalMentions());
    }

    @Test
    void sourceFailureShouldAddErrorAndContinue() {
        TrackingSourceClient workingClient = new TrackingSourceClient("sourceA", List.of(
                new TimeBucket(LocalDate.of(2026, 1, 10), 100)
        ));
        FailingSourceClient failingClient = new FailingSourceClient("sourceB");

        TimeseriesDayCacheService dayCache = new TimeseriesDayCacheService(redis, cacheProperties);
        TimeseriesService service = new TimeseriesService(
                List.of(workingClient, failingClient), repo, dayCache, sourceRegistry);

        TimeseriesRequest req = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10),
                List.of("sourceA", "sourceB"));

        TimeseriesResponse resp = service.query(req);

        assertEquals(100, resp.totalMentions());
        assertEquals(1, resp.perSource().size());
        assertEquals("sourceA", resp.perSource().get(0).source());
        assertEquals(1, resp.errors().size());
        assertEquals("sourceB", resp.errors().get(0).source());
        assertEquals("SOURCE_ERROR", resp.errors().get(0).code());
    }

    @Test
    void partialCacheWithCachedErrorShouldReturnPartialData() {
        TrackingSourceClient client = new TrackingSourceClient("sourceA", List.of(
                new TimeBucket(LocalDate.of(2026, 1, 10), 10),
                new TimeBucket(LocalDate.of(2026, 1, 11), 20),
                new TimeBucket(LocalDate.of(2026, 1, 12), 30)
        ));

        TimeseriesDayCacheService dayCache = new TimeseriesDayCacheService(redis, cacheProperties);
        TimeseriesService service = new TimeseriesService(
                List.of(client), repo, dayCache, sourceRegistry);

        TimeseriesRequest req1 = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11),
                List.of("sourceA"));
        service.query(req1);
        assertEquals(1, client.callCount);

        dayCache.cacheError("sourceA", "bitcoin", LocalDate.of(2026, 1, 12), LocalDate.of(2026, 1, 13));

        TimeseriesRequest req2 = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 13),
                List.of("sourceA"));

        TimeseriesResponse resp = service.query(req2);

        assertTrue(resp.fromCache());
        assertEquals(30, resp.totalMentions());
        assertEquals(1, resp.perSource().size());
        assertEquals("sourceA", resp.perSource().get(0).source());
        assertEquals(2, resp.perSource().get(0).dailyStats().size());

        assertEquals(1, resp.errors().size());
        assertEquals("sourceA", resp.errors().get(0).source());
        assertEquals("PARTIAL_DATA", resp.errors().get(0).code());

        assertEquals(1, client.callCount);
    }

    @Test
    void wikipediaShouldBeCaseSensitive() {
        TrackingSourceClient wikiClient = new TrackingSourceClient("wikipedia", List.of(
                new TimeBucket(LocalDate.of(2026, 1, 10), 100)
        ));

        when(sourceRegistry.get("wikipedia")).thenReturn(new SourceDescriptor(
                "wikipedia", "Wikipedia", "Test", "cat", "unit", null, null, "url", 1.0, true));

        TimeseriesDayCacheService dayCache = new TimeseriesDayCacheService(redis, cacheProperties);
        TimeseriesService service = new TimeseriesService(
                List.of(wikiClient), repo, dayCache, sourceRegistry);

        TimeseriesRequest req1 = new TimeseriesRequest("Bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10),
                List.of("wikipedia"));
        service.query(req1);
        assertEquals(1, wikiClient.callCount);

        TimeseriesRequest req2 = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10),
                List.of("wikipedia"));
        TimeseriesResponse resp = service.query(req2);
        assertEquals(2, wikiClient.callCount);
        assertFalse(resp.fromCache());
    }

    @Test
    void nonWikipediaSourcesShouldBeCaseInsensitive() {
        TrackingSourceClient newsClient = new TrackingSourceClient("sourceA", List.of(
                new TimeBucket(LocalDate.of(2026, 1, 10), 100)
        ));

        TimeseriesDayCacheService dayCache = new TimeseriesDayCacheService(redis, cacheProperties);
        TimeseriesService service = new TimeseriesService(
                List.of(newsClient), repo, dayCache, sourceRegistry);

        TimeseriesRequest req1 = new TimeseriesRequest("Bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10),
                List.of("sourceA"));
        service.query(req1);
        assertEquals(1, newsClient.callCount);

        TimeseriesRequest req2 = new TimeseriesRequest("bitcoin",
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10),
                List.of("sourceA"));
        TimeseriesResponse resp = service.query(req2);
        assertEquals(1, newsClient.callCount);
        assertTrue(resp.fromCache());
    }

    private static class TrackingSourceClient implements SourceClient {
        private final String id;
        private final List<TimeBucket> buckets;
        int callCount = 0;
        LocalDate lastRequestedStart;
        LocalDate lastRequestedEnd;

        TrackingSourceClient(String id, List<TimeBucket> buckets) {
            this.id = id;
            this.buckets = buckets;
        }

        @Override
        public String sourceId() {
            return id;
        }

        @Override
        public TimeSeries fetchDailyTimeSeries(String topic, LocalDate start, LocalDate end) {
            callCount++;
            lastRequestedStart = start;
            lastRequestedEnd = end;

            List<TimeBucket> filtered = buckets.stream()
                    .filter(b -> !b.date().isBefore(start) && !b.date().isAfter(end))
                    .toList();
            return new TimeSeries(id, topic, TimeInterval.DAY, filtered);
        }
    }

    private static class FailingSourceClient implements SourceClient {
        private final String id;

        FailingSourceClient(String id) {
            this.id = id;
        }

        @Override
        public String sourceId() {
            return id;
        }

        @Override
        public TimeSeries fetchDailyTimeSeries(String topic, LocalDate start, LocalDate end) {
            throw new SourceClientException(id, "API unavailable");
        }
    }
}
