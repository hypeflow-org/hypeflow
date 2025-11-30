package com.hypeflow.service;

import com.hypeflow.api.DailyStatDto;
import com.hypeflow.api.TimeseriesRequest;
import com.hypeflow.api.TimeseriesResponse;
import com.hypeflow.model.TimeBucket;
import com.hypeflow.model.TimeInterval;
import com.hypeflow.model.TimeSeries;
import com.hypeflow.sources.SourceClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimeseriesServiceTest {

    @Test
    void testQueryWithMultipleSources() {
        SourceClient newsClient = new MockSourceClient(
                "newsapi",
                "bitcoin",
                List.of(
                        new TimeBucket(LocalDate.of(2025, 11, 1), 10),
                        new TimeBucket(LocalDate.of(2025, 11, 2), 20)
                )
        );

        SourceClient wikiClient = new MockSourceClient(
                "wikipedia",
                "bitcoin",
                List.of(
                        new TimeBucket(LocalDate.of(2025, 11, 1), 100),
                        new TimeBucket(LocalDate.of(2025, 11, 2), 200)
                )
        );

        TimeseriesService service = new TimeseriesService(List.of(newsClient, wikiClient));

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

        DailyStatDto stat1 = response.dailyStatistics().get(0);
        assertEquals(LocalDate.of(2025, 11, 1), stat1.date());
        assertEquals(110, stat1.mentions());

        DailyStatDto stat2 = response.dailyStatistics().get(1);
        assertEquals(LocalDate.of(2025, 11, 2), stat2.date());
        assertEquals(220, stat2.mentions());
    }

    @Test
    void testQueryWithSingleSource() {
        SourceClient newsClient = new MockSourceClient(
                "newsapi",
                "bitcoin",
                List.of(
                        new TimeBucket(LocalDate.of(2025, 11, 1), 10)
                )
        );

        TimeseriesService service = new TimeseriesService(List.of(newsClient));

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

    /**
     * Mock implementation of SourceClient for testing.
     */
    private static class MockSourceClient implements SourceClient {
        private final String sourceId;
        private final String expectedTopic;
        private final List<TimeBucket> buckets;

        public MockSourceClient(String sourceId, String expectedTopic, List<TimeBucket> buckets) {
            this.sourceId = sourceId;
            this.expectedTopic = expectedTopic;
            this.buckets = buckets;
        }

        @Override
        public String sourceId() {
            return sourceId;
        }

        @Override
        public TimeSeries fetchDailyTimeSeries(String topic,
                                               LocalDate startInclusive,
                                               LocalDate endInclusive) {
            return new TimeSeries(sourceId, topic, TimeInterval.DAY, buckets);
        }
    }
}
