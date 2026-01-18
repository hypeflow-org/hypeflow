package com.hypeflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hypeflow.api.TimeseriesRequest;
import com.hypeflow.api.TimeseriesResponse;
import com.hypeflow.config.CacheProperties;
import com.hypeflow.model.TimeBucket;
import com.hypeflow.model.TimeInterval;
import com.hypeflow.model.TimeSeries;
import com.hypeflow.repo.SearchHistoryRepository;
import com.hypeflow.sources.SourceClient;
import com.hypeflow.sources.SourceClientException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class TimeseriesServiceTest {

    private CacheProperties createCacheProperties() {
        CacheProperties props = new CacheProperties();
        props.setTimeseriesTtlHours(12);
        props.setErrorTtlMinutes(5);
        return props;
    }

    @Test
    void testQueryWithMultipleSources() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);

        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);

        SearchHistoryRepository repo = mock(SearchHistoryRepository.class);

        SourceClient newsClient = new MockSourceClient(
                "newsapi",
                List.of(
                        new TimeBucket(LocalDate.of(2025, 11, 1), 10),
                        new TimeBucket(LocalDate.of(2025, 11, 2), 20)
                )
        );

        SourceClient wikiClient = new MockSourceClient(
                "wikipedia",
                List.of(
                        new TimeBucket(LocalDate.of(2025, 11, 1), 100),
                        new TimeBucket(LocalDate.of(2025, 11, 2), 200)
                )
        );

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        TimeseriesService service =
                new TimeseriesService(
                        List.of(newsClient, wikiClient),
                        repo,
                        redis,
                        objectMapper,
                        createCacheProperties()
                );

        TimeseriesRequest request = new TimeseriesRequest(
                "bitcoin",
                LocalDate.of(2025, 11, 1),
                LocalDate.of(2025, 11, 2),
                List.of("newsapi", "wikipedia")
        );

        TimeseriesResponse response = service.query(request);

        assertNotNull(response);
        assertEquals("bitcoin", response.word());
        assertEquals(2, response.dailyStatistics().size());
        assertEquals(330, response.totalMentions());

        assertEquals(110, response.dailyStatistics().get(0).mentions());
        assertEquals(220, response.dailyStatistics().get(1).mentions());

        assertEquals(2, response.perSource().size());
        assertTrue(response.errors().isEmpty());

        verify(repo, times(1)).save(any());
        verify(ops, times(1)).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void testQueryWithSingleSource() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);

        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);

        SearchHistoryRepository repo = mock(SearchHistoryRepository.class);

        SourceClient newsClient = new MockSourceClient(
                "newsapi",
                List.of(new TimeBucket(LocalDate.of(2025, 11, 1), 10))
        );

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        TimeseriesService service =
                new TimeseriesService(
                        List.of(newsClient),
                        repo,
                        redis,
                        objectMapper,
                        createCacheProperties()
                );

        TimeseriesRequest request = new TimeseriesRequest(
                "bitcoin",
                LocalDate.of(2025, 11, 1),
                LocalDate.of(2025, 11, 1),
                List.of("newsapi")
        );

        TimeseriesResponse response = service.query(request);

        assertNotNull(response);
        assertEquals(1, response.dailyStatistics().size());
        assertEquals(10, response.totalMentions());
        assertEquals(1, response.perSource().size());
        assertEquals("newsapi", response.perSource().get(0).source());
        assertEquals(10, response.perSource().get(0).totalMentions());
    }

    @Test
    void testQueryWithSourceFailure() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);

        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);

        SearchHistoryRepository repo = mock(SearchHistoryRepository.class);

        SourceClient workingClient = new MockSourceClient(
                "newsapi",
                List.of(new TimeBucket(LocalDate.of(2025, 11, 1), 10))
        );

        SourceClient failingClient = new FailingSourceClient("failing");

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        TimeseriesService service =
                new TimeseriesService(
                        List.of(workingClient, failingClient),
                        repo,
                        redis,
                        objectMapper,
                        createCacheProperties()
                );

        TimeseriesRequest request = new TimeseriesRequest(
                "bitcoin",
                LocalDate.of(2025, 11, 1),
                LocalDate.of(2025, 11, 1),
                List.of("newsapi", "failing")
        );

        TimeseriesResponse response = service.query(request);

        assertNotNull(response);
        assertEquals(1, response.perSource().size());
        assertEquals("newsapi", response.perSource().get(0).source());

        assertEquals(1, response.errors().size());
        assertEquals("failing", response.errors().get(0).source());
        assertEquals("SOURCE_ERROR", response.errors().get(0).code());

        verify(ops, times(1)).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void testCacheHitReturnsFromCacheTrue() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);

        when(redis.opsForValue()).thenReturn(ops);

        TimeseriesResponse cached = new TimeseriesResponse(
                "bitcoin",
                LocalDate.of(2025, 11, 1),
                LocalDate.of(2025, 11, 1),
                100,
                List.of(),
                List.of("newsapi"),
                false,
                List.of(),
                List.of()
        );

        when(ops.get(anyString())).thenReturn(cached);

        SearchHistoryRepository repo = mock(SearchHistoryRepository.class);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        TimeseriesService service =
                new TimeseriesService(
                        List.of(),
                        repo,
                        redis,
                        objectMapper,
                        createCacheProperties()
                );

        TimeseriesRequest request = new TimeseriesRequest(
                "bitcoin",
                LocalDate.of(2025, 11, 1),
                LocalDate.of(2025, 11, 1),
                List.of("newsapi")
        );

        TimeseriesResponse response = service.query(request);

        assertNotNull(response);
        assertTrue(response.fromCache());
    }

    private static class MockSourceClient implements SourceClient {

        private final String id;
        private final List<TimeBucket> buckets;

        public MockSourceClient(String id, List<TimeBucket> buckets) {
            this.id = id;
            this.buckets = buckets;
        }

        @Override
        public String sourceId() {
            return id;
        }

        @Override
        public TimeSeries fetchDailyTimeSeries(String topic,
                                               LocalDate startInclusive,
                                               LocalDate endInclusive) {
            return new TimeSeries(id, topic, TimeInterval.DAY, buckets);
        }
    }

    private static class FailingSourceClient implements SourceClient {

        private final String id;

        public FailingSourceClient(String id) {
            this.id = id;
        }

        @Override
        public String sourceId() {
            return id;
        }

        @Override
        public TimeSeries fetchDailyTimeSeries(String topic,
                                               LocalDate startInclusive,
                                               LocalDate endInclusive) {
            throw new SourceClientException(id, "API unavailable");
        }
    }
}
