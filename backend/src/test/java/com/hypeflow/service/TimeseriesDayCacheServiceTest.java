package com.hypeflow.service;

import com.hypeflow.config.CacheProperties;
import com.hypeflow.model.DateInterval;
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
class TimeseriesDayCacheServiceTest {

    private RedisTemplate<String, Object> redis;
    private HashOperations<String, Object, Object> hashOps;
    private ValueOperations<String, Object> valueOps;
    private CacheProperties cacheProperties;
    private TimeseriesDayCacheService cacheService;

    // simple in-memory stores to simulate Redis
    private final Map<String, Map<String, Object>> hashStore = new HashMap<>();
    private final Set<String> keyStore = new HashSet<>();

    @BeforeEach
    void setUp() {
        redis = mock(RedisTemplate.class);
        hashOps = mock(HashOperations.class);
        valueOps = mock(ValueOperations.class);

        when(redis.opsForHash()).thenReturn(hashOps);
        when(redis.opsForValue()).thenReturn(valueOps);

        when(redis.hasKey(anyString())).thenAnswer(inv ->
                keyStore.contains(inv.getArgument(0))
        );

        doAnswer(inv -> {
            keyStore.add(inv.getArgument(0));
            return null;
        }).when(valueOps).set(anyString(), any(), anyLong(), any(TimeUnit.class));

        doAnswer(inv -> {
            String key = inv.getArgument(0);
            List<Object> fields = inv.getArgument(1);
            Map<String, Object> map = hashStore.getOrDefault(key, Map.of());
            List<Object> result = new ArrayList<>();
            for (Object f : fields) {
                result.add(map.get(f.toString()));
            }
            return result;
        }).when(hashOps).multiGet(anyString(), anyList());

        doAnswer(inv -> {
            String key = inv.getArgument(0);
            Map<String, Object> entries = inv.getArgument(1);
            hashStore.computeIfAbsent(key, k -> new HashMap<>()).putAll(entries);
            keyStore.add(key);
            return null;
        }).when(hashOps).putAll(anyString(), anyMap());

        cacheProperties = new CacheProperties();
        cacheProperties.setDayCacheTtlDays(7);
        cacheProperties.setErrorTtlMinutes(5);

        cacheService = new TimeseriesDayCacheService(redis, cacheProperties);
    }

    @Test
    void writeReadTest() {
        Map<LocalDate, Integer> days = Map.of(
                LocalDate.of(2026, 1, 10), 100,
                LocalDate.of(2026, 1, 11), 200
        );

        cacheService.writeDays("sourceA", "bitcoin", days);

        Map<LocalDate, Integer> read = cacheService.readDays(
                "sourceA", "bitcoin",
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 11)
        );

        assertEquals(2, read.size());
        assertEquals(100, read.get(LocalDate.of(2026, 1, 10)));
        assertEquals(200, read.get(LocalDate.of(2026, 1, 11)));
    }

    @Test
    void readDaysWithNoCacheShouldReturnEmptyMap() {
        Map<LocalDate, Integer> read = cacheService.readDays(
                "sourceA", "bitcoin",
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 12)
        );

        assertTrue(read.isEmpty());
    }

    @Test
    void findMissingDays() {
        Map<LocalDate, Integer> cached = Map.of(
                LocalDate.of(2026, 1, 10), 10,
                LocalDate.of(2026, 1, 12), 30
        );

        List<LocalDate> missing = cacheService.findMissingDays(
                cached,
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 13)
        );

        assertEquals(
                List.of(
                        LocalDate.of(2026, 1, 11),
                        LocalDate.of(2026, 1, 13)
                ),
                missing
        );
    }

    @Test
    void multipleIntervalsGeneralTest() {
        List<LocalDate> days = List.of(
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 11),
                LocalDate.of(2026, 1, 13),
                LocalDate.of(2026, 1, 14),
                LocalDate.of(2026, 1, 16)
        );

        List<DateInterval> intervals = cacheService.toIntervals(days);

        assertEquals(3, intervals.size());

        assertEquals(
                new DateInterval(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11)),
                intervals.get(0)
        );
        assertEquals(
                new DateInterval(LocalDate.of(2026, 1, 13), LocalDate.of(2026, 1, 14)),
                intervals.get(1)
        );
        assertEquals(
                new DateInterval(LocalDate.of(2026, 1, 16), LocalDate.of(2026, 1, 16)),
                intervals.get(2)
        );
    }

    @Test
    void toIntervalsEmptyList() {
        List<DateInterval> intervals = cacheService.toIntervals(List.of());
        assertTrue(intervals.isEmpty());
    }

    @Test
    void toIntervalsSingleDay() {
        List<DateInterval> intervals = cacheService.toIntervals(
                List.of(LocalDate.of(2026, 1, 10))
        );

        assertEquals(1, intervals.size());
        assertEquals(
                new DateInterval(
                        LocalDate.of(2026, 1, 10),
                        LocalDate.of(2026, 1, 10)
                ),
                intervals.get(0)
        );
    }

    @Test
    void toIntervalsAllConsecutiveDays() {
        List<LocalDate> days = List.of(
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 11),
                LocalDate.of(2026, 1, 12),
                LocalDate.of(2026, 1, 13)
        );

        List<DateInterval> intervals = cacheService.toIntervals(days);

        assertEquals(1, intervals.size());
        assertEquals(
                new DateInterval(
                        LocalDate.of(2026, 1, 10),
                        LocalDate.of(2026, 1, 13)
                ),
                intervals.get(0)
        );
    }

    @Test
    void toIntervalsNoConsecutiveDays() {
        List<LocalDate> days = List.of(
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 12),
                LocalDate.of(2026, 1, 14)
        );

        List<DateInterval> intervals = cacheService.toIntervals(days);

        assertEquals(3, intervals.size());

        assertEquals(
                new DateInterval( LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10) ),
                intervals.get(0)
        );
        assertEquals(
                new DateInterval( LocalDate.of(2026, 1, 12), LocalDate.of(2026, 1, 12) ),
                intervals.get(1)
        );
        assertEquals(
                new DateInterval( LocalDate.of(2026, 1, 14), LocalDate.of(2026, 1, 14) ),
                intervals.get(2)
        );
    }

    @Test
    void toIntervalsUnsortedInputShouldStillWork() {
        List<LocalDate> days = List.of(
                LocalDate.of(2026, 1, 12),
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 11)
        );

        List<DateInterval> intervals = cacheService.toIntervals(days);

        assertEquals(1, intervals.size());
        assertEquals(
                new DateInterval(
                        LocalDate.of(2026, 1, 10),
                        LocalDate.of(2026, 1, 12)
                ),
                intervals.get(0)
        );
    }

    @Test
    void cacheErrorSimpleTest() {
        assertFalse(cacheService.hasRecentError(
                "sourceA", "bitcoin",
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 12)
        ));

        cacheService.cacheError(
                "sourceA", "bitcoin",
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 12)
        );

        assertTrue(cacheService.hasRecentError(
                "sourceA", "bitcoin",
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 12)
        ));
    }

    @Test
    void writeDaysWithEmptyMapShouldDoNothing() {
        cacheService.writeDays("sourceA", "bitcoin", Map.of());

        verify(hashOps, never()).putAll(anyString(), anyMap());
    }

    @Test
    void readDaysShouldIgnoreMissingFields() {
        cacheService.writeDays("sourceA", "bitcoin", Map.of(
                LocalDate.of(2026, 1, 10), 1000
        ));

        Map<LocalDate, Integer> read = cacheService.readDays(
                "sourceA", "bitcoin",
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 12)
        );

        assertEquals(1, read.size());
        assertTrue(read.containsKey(LocalDate.of(2026, 1, 10)));
        assertEquals(1000, read.get(LocalDate.of(2026, 1, 10)));
    }
}
