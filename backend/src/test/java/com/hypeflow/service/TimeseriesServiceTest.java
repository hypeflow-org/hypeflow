package com.hypeflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypeflow.api.TimeseriesRequest;
import com.hypeflow.api.TimeseriesResponse;
import com.hypeflow.model.TimeBucket;
import com.hypeflow.model.TimeInterval;
import com.hypeflow.model.TimeSeries;
import com.hypeflow.repo.SearchHistoryRepository;
import com.hypeflow.sources.SourceClient;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TimeseriesServiceTest {

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

        TimeseriesService service =
                new TimeseriesService(
                        List.of(newsClient, wikiClient),
                        repo,
                        redis,
                        new ObjectMapper()
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

        verify(repo, times(1)).save(any());
        verify(ops, times(1)).set(anyString(), any());
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

        TimeseriesService service =
                new TimeseriesService(
                        List.of(newsClient),
                        repo,
                        redis,
                        new ObjectMapper()
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
}
