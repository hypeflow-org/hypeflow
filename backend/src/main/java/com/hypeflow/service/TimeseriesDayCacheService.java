package com.hypeflow.service;

import com.hypeflow.config.CacheProperties;
import com.hypeflow.model.DateInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class TimeseriesDayCacheService {

    private static final Logger log = LoggerFactory.getLogger(TimeseriesDayCacheService.class);
    private static final String DAY_CACHE_PREFIX = "ts:day:";
    private static final String ERROR_CACHE_PREFIX = "ts:err:";

    private final RedisTemplate<String, Object> redis;
    private final CacheProperties cacheProperties;

    public TimeseriesDayCacheService(RedisTemplate<String, Object> redis, CacheProperties cacheProperties) {
        this.redis = redis;
        this.cacheProperties = cacheProperties;
    }

    public Map<LocalDate, Integer> readDays(String sourceId, String wordKey, LocalDate start, LocalDate end) {
        String hashKey = buildHashKey(sourceId, wordKey);
        Map<LocalDate, Integer> result = new LinkedHashMap<>();

        List<String> fields = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            fields.add(d.toString());
        }

        List<Object> values = redis.opsForHash().multiGet(hashKey, new ArrayList<>(fields));
        if (values.isEmpty()) {
            return result;
        }

        for (int i = 0; i < fields.size(); i++) {
            Object val = values.get(i);
            if (val != null) {
                LocalDate date = LocalDate.parse(fields.get(i));
                int count = val instanceof Number ? ((Number) val).intValue() : Integer.parseInt(val.toString());
                result.put(date, count);
            }
        }

        log.debug("Read {} cached days for {}:{} in [{}, {}]", result.size(), sourceId, wordKey, start, end);
        return result;
    }

    public void writeDays(String sourceId, String wordKey, Map<LocalDate, Integer> days) {
        if (days.isEmpty()) {
            return;
        }

        String hashKey = buildHashKey(sourceId, wordKey);
        Map<String, Object> entries = new HashMap<>();
        for (var entry : days.entrySet()) {
            entries.put(entry.getKey().toString(), entry.getValue());
        }

        redis.opsForHash().putAll(hashKey, entries);
        redis.expire(hashKey, cacheProperties.getDayCacheTtlDays(), TimeUnit.DAYS);

        log.debug("Wrote {} days to cache for {}:{}", days.size(), sourceId, wordKey);
    }

    public boolean hasRecentError(String sourceId, String wordKey, LocalDate start, LocalDate end) {
        String errorKey = buildErrorKey(sourceId, wordKey, start, end);
        return redis.hasKey(errorKey);
    }

    public void cacheError(String sourceId, String wordKey, LocalDate start, LocalDate end) {
        String errorKey = buildErrorKey(sourceId, wordKey, start, end);
        redis.opsForValue().set(errorKey, "1", cacheProperties.getErrorTtlMinutes(), TimeUnit.MINUTES);
        log.debug("Cached error for {}:{} [{} to {}] (TTL {}min)",
                sourceId, wordKey, start, end, cacheProperties.getErrorTtlMinutes());
    }

    public List<LocalDate> findMissingDays(Map<LocalDate, Integer> cached, LocalDate start, LocalDate end) {
        List<LocalDate> missing = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!cached.containsKey(d)) {
                missing.add(d);
            }
        }
        return missing;
    }

    public List<DateInterval> toIntervals(List<LocalDate> sortedDays) {
        if (sortedDays.isEmpty()) {
            return List.of();
        }

        List<DateInterval> intervals = new ArrayList<>();
        List<LocalDate> sorted = new ArrayList<>(sortedDays);
        Collections.sort(sorted);

        LocalDate intervalStart = sorted.get(0);
        LocalDate intervalEnd = sorted.get(0);

        for (int i = 1; i < sorted.size(); i++) {
            LocalDate current = sorted.get(i);
            if (current.equals(intervalEnd.plusDays(1))) {
                intervalEnd = current;
            } else {
                intervals.add(new DateInterval(intervalStart, intervalEnd));
                intervalStart = current;
                intervalEnd = current;
            }
        }
        intervals.add(new DateInterval(intervalStart, intervalEnd));

        return intervals;
    }

    private String buildHashKey(String sourceId, String wordKey) {
        return DAY_CACHE_PREFIX + sourceId + ":" + hashWord(wordKey);
    }

    private String buildErrorKey(String sourceId, String wordKey, LocalDate start, LocalDate end) {
        return ERROR_CACHE_PREFIX + sourceId + ":" + hashWord(wordKey) + ":" + start + ":" + end;
    }

    private String hashWord(String word) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(word.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return word.replaceAll("[^a-zA-Z0-9]", "_");
        }
    }
}
