package com.hypeflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypeflow.api.DailyStatDto;
import com.hypeflow.api.SourceErrorDto;
import com.hypeflow.api.SourceSeriesDto;
import com.hypeflow.api.TimeseriesRequest;
import com.hypeflow.api.TimeseriesResponse;
import com.hypeflow.model.TimeBucket;
import com.hypeflow.model.TimeSeries;
import com.hypeflow.sources.SourceClient;
import com.hypeflow.sources.SourceClientException;
import com.hypeflow.model.SearchHistory;
import com.hypeflow.repo.SearchHistoryRepository;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
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

        Object raw = redis.opsForValue().get(cacheKey);
        if (raw != null) {
            try {
                TimeseriesResponse cached = objectMapper.convertValue(raw, TimeseriesResponse.class);
                log.info("Cache hit: {}", cacheKey);
                List<SourceSeriesDto> perSource = cached.perSource() != null ? cached.perSource() : List.of();
                List<SourceErrorDto> errors = cached.errors() != null ? cached.errors() : List.of();
                return new TimeseriesResponse(
                        cached.word(),
                        cached.startDate(),
                        cached.endDate(),
                        cached.totalMentions(),
                        cached.dailyStatistics(),
                        cached.sources(),
                        true,
                        perSource,
                        errors
                );
            } catch (Exception e) {
                log.error("Failed to convert cached value: {}", e.getMessage());
            }
        }

        log.info("Cache miss: {} -> fetching from external APIs", cacheKey);

        Map<LocalDate, Integer> aggregatedCounts = new HashMap<>();
        List<String> actualSources = new ArrayList<>();
        List<SourceSeriesDto> perSourceList = new ArrayList<>();
        List<SourceErrorDto> errorsList = new ArrayList<>();

        for (String sourceId : requestedSources) {
            SourceClient client = sourceClientsMap.get(sourceId);
            if (client == null) {
                errorsList.add(new SourceErrorDto(
                        sourceId,
                        "UNKNOWN_SOURCE",
                        "Source '" + sourceId + "' is not available"
                ));
                continue;
            }

            try {
                TimeSeries timeSeries = client.fetchDailyTimeSeries(
                        req.word(),
                        req.startDate(),
                        req.endDate()
                );

                actualSources.add(sourceId);

                List<DailyStatDto> sourceDailyStats = new ArrayList<>();
                int sourceTotalMentions = 0;

                for (TimeBucket bucket : timeSeries.buckets()) {
                    aggregatedCounts.merge(bucket.date(), bucket.count(), Integer::sum);
                    sourceDailyStats.add(new DailyStatDto(bucket.date(), bucket.count()));
                    sourceTotalMentions += bucket.count();
                }

                perSourceList.add(new SourceSeriesDto(
                        sourceId,
                        sourceTotalMentions,
                        sourceDailyStats
                ));

            } catch (SourceClientException e) {
                log.warn("Source {} failed: {}", sourceId, e.getMessage());
                errorsList.add(new SourceErrorDto(
                        sourceId,
                        "SOURCE_ERROR",
                        e.getMessage()
                ));
            } catch (Exception e) {
                log.error("Unexpected error from source {}: {}", sourceId, e.getMessage());
                errorsList.add(new SourceErrorDto(
                        sourceId,
                        "UNEXPECTED_ERROR",
                        e.getMessage()
                ));
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
                false,
                perSourceList,
                errorsList
        );

        if (errorsList.isEmpty()) {
            redis.opsForValue().set(cacheKey, response, Duration.ofMinutes(30));
            log.info("Saved result to cache (TTL 30min): {}", cacheKey);
        } else {
            log.info("Not caching response with {} error(s)", errorsList.size());
        }

        return response;
    }
}
