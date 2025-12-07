package com.hypeflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypeflow.api.DailyStatDto;
import com.hypeflow.api.TimeseriesRequest;
import com.hypeflow.api.TimeseriesResponse;
import com.hypeflow.model.TimeBucket;
import com.hypeflow.model.TimeSeries;
import com.hypeflow.sources.SourceClient;
import com.hypeflow.model.SearchHistory;
import com.hypeflow.repo.SearchHistoryRepository;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimeseriesService {

    private static final Logger log = LoggerFactory.getLogger(TimeseriesService.class);

    private final SearchHistoryRepository searchHistoryRepository;
    private final Map<String, SourceClient> sourceClientsMap;
    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper objectMapper;

    public TimeseriesService(
            List<SourceClient> sourceClients,
            SearchHistoryRepository searchHistoryRepository,
            RedisTemplate<String, Object> redis,
            ObjectMapper objectMapper
    ) {
        this.sourceClientsMap = sourceClients.stream()
                .collect(Collectors.toMap(SourceClient::sourceId, c -> c));

        this.searchHistoryRepository = searchHistoryRepository;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public TimeseriesResponse query(TimeseriesRequest req) {

        List<String> requestedSources = req.sources() == null || req.sources().isEmpty()
                ? new ArrayList<>(sourceClientsMap.keySet())
                : req.sources();

        String cacheKey = String.format(
                "timeseries:%s:%s:%s:%s",
                req.word(),
                req.startDate(),
                req.endDate(),
                String.join("-", requestedSources)
        );

        //  ЧИТАЕМ ИЗ КЭША
        Object raw = redis.opsForValue().get(cacheKey);
        if (raw != null) {
            try {
                TimeseriesResponse cached = objectMapper.convertValue(raw, TimeseriesResponse.class);
                log.info("Cache hit: {}", cacheKey);
                return cached;
            } catch (Exception e) {
                log.error("Failed to convert cached value: {}", e.getMessage());
            }
        }

        log.info("Cache miss: {} -> fetching from external APIs", cacheKey);

        Map<LocalDate, Integer> aggregatedCounts = new HashMap<>();
        List<String> actualSources = new ArrayList<>();

        for (String sourceId : requestedSources) {
            SourceClient client = sourceClientsMap.get(sourceId);
            if (client == null) continue;

            actualSources.add(sourceId);

            TimeSeries timeSeries = client.fetchDailyTimeSeries(
                    req.word(),
                    req.startDate(),
                    req.endDate()
            );

            for (TimeBucket bucket : timeSeries.buckets()) {
                aggregatedCounts.merge(bucket.date(), bucket.count(), Integer::sum);
            }
        }

        List<DailyStatDto> dailyStats = aggregatedCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new DailyStatDto(e.getKey(), e.getValue()))
                .toList();

        int totalMentions = aggregatedCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        SearchHistory history = SearchHistory.builder()
                .word(req.word())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .sources(String.join(",", actualSources))
                .totalMentions(totalMentions)
                .searchedAt(java.time.LocalDateTime.now())
                .build();

        searchHistoryRepository.save(history);

        log.info("SearchHistory saved successfully: id={}, searchedAt={}",
                history.getId(),
                history.getSearchedAt()
        );

        TimeseriesResponse response = new TimeseriesResponse(
                req.word(),
                req.startDate(),
                req.endDate(),
                totalMentions,
                dailyStats,
                actualSources,
                false
        );

        // КЛАДЁМ В РЕДИС
        redis.opsForValue().set(cacheKey, response);
        log.info("Saved result to cache: {}", cacheKey);

        return response;
    }
}
