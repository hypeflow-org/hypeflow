package com.hypeflow.service;

import com.hypeflow.api.DailyStatDto;
import com.hypeflow.api.SourceErrorDto;
import com.hypeflow.api.SourceSeriesDto;
import com.hypeflow.api.TimeseriesRequest;
import com.hypeflow.api.TimeseriesResponse;
import com.hypeflow.model.DateInterval;
import com.hypeflow.model.SearchHistory;
import com.hypeflow.model.SourceDescriptor;
import com.hypeflow.model.TimeBucket;
import com.hypeflow.model.TimeSeries;
import com.hypeflow.repo.SearchHistoryRepository;
import com.hypeflow.sources.SourceClient;
import com.hypeflow.sources.SourceClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class TimeseriesService {

    private static final Logger log = LoggerFactory.getLogger(TimeseriesService.class);

    private final SearchHistoryRepository searchHistoryRepository;
    private final Map<String, SourceClient> sourceClientsMap;
    private final TimeseriesDayCacheService dayCache;
    private final SourceRegistry sourceRegistry;

    public TimeseriesService(
            List<SourceClient> sourceClients,
            SearchHistoryRepository searchHistoryRepository,
            TimeseriesDayCacheService dayCache,
            SourceRegistry sourceRegistry
    ) {
        this.sourceClientsMap = sourceClients.stream()
                .collect(Collectors.toMap(SourceClient::sourceId, c -> c));
        this.searchHistoryRepository = searchHistoryRepository;
        this.dayCache = dayCache;
        this.sourceRegistry = sourceRegistry;
    }

    public TimeseriesResponse query(TimeseriesRequest req) {
        List<String> requestedSources = req.sources() == null || req.sources().isEmpty()
                ? new ArrayList<>(sourceClientsMap.keySet())
                : new ArrayList<>(req.sources());
        requestedSources.sort(String::compareTo);

        String normalizedWord = req.word().trim();
        LocalDate start = req.startDate();
        LocalDate end = req.endDate();
        long requestedDays = ChronoUnit.DAYS.between(start, end) + 1;

        Map<LocalDate, Integer> aggregatedCounts = new HashMap<>();
        List<String> actualSources = new ArrayList<>();
        List<SourceSeriesDto> perSourceList = new ArrayList<>();
        List<SourceErrorDto> errorsList = new ArrayList<>();
        boolean anyExternalCalls = false;

        for (String sourceId : requestedSources) {
            SourceClient client = sourceClientsMap.get(sourceId);
            if (client == null) {
                errorsList.add(new SourceErrorDto(sourceId, "UNKNOWN_SOURCE",
                        "Source '" + sourceId + "' is not available"));
                continue;
            }

            SourceDescriptor descriptor = sourceRegistry.get(sourceId);
            if (descriptor != null && descriptor.maxRangeDays() != null && requestedDays > descriptor.maxRangeDays()) {
                errorsList.add(new SourceErrorDto(sourceId, "RANGE_TOO_LARGE",
                        "Max range for " + sourceId + " is " + descriptor.maxRangeDays() + " days"));
                continue;
            }

            try {
                SourceResult result = fetchSourceData(client, sourceId, normalizedWord, req.word(), start, end);

                if (result.dayCounts == null || result.dayCounts.isEmpty()) {
                    if (result.cachedError != null) {
                        log.info("Source {} skipped due to cached error", sourceId);
                        errorsList.add(new SourceErrorDto(sourceId, "SOURCE_ERROR", result.cachedError.getMessage()));
                    }
                    continue;
                }

                if (result.madeExternalCalls) {
                    anyExternalCalls = true;
                }

                if (result.cachedError != null) {
                    log.info("Source {} returned partial data with error: {}", sourceId, result.cachedError.getMessage());
                    errorsList.add(new SourceErrorDto(sourceId, "PARTIAL_DATA", result.cachedError.getMessage()));
                }

                actualSources.add(sourceId);

                List<DailyStatDto> sourceDailyStats = new ArrayList<>();
                int sourceTotalMentions = 0;

                for (var entry : result.dayCounts.entrySet()) {
                    aggregatedCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
                    sourceDailyStats.add(new DailyStatDto(entry.getKey(), entry.getValue()));
                    sourceTotalMentions += entry.getValue();
                }

                sourceDailyStats.sort(Comparator.comparing(DailyStatDto::date));
                perSourceList.add(new SourceSeriesDto(sourceId, sourceTotalMentions, sourceDailyStats));

            } catch (SourceClientException e) {
                log.warn("Source {} failed: {}", sourceId, e.getMessage());
                errorsList.add(new SourceErrorDto(sourceId, "SOURCE_ERROR", e.getMessage()));
            } catch (Exception e) {
                log.error("Unexpected error from source {}: {}", sourceId, e.getMessage());
                errorsList.add(new SourceErrorDto(sourceId, "UNEXPECTED_ERROR", e.getMessage()));
            }
        }

        List<DailyStatDto> dailyStats = aggregatedCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new DailyStatDto(e.getKey(), e.getValue()))
                .toList();

        int totalMentions = aggregatedCounts.values().stream().mapToInt(Integer::intValue).sum();
        boolean fromCache = !anyExternalCalls && !actualSources.isEmpty();

        SearchHistory history = SearchHistory.builder()
                .word(req.word())
                .startDate(start)
                .endDate(end)
                .sources(String.join(",", actualSources))
                .totalMentions(totalMentions)
                .searchedAt(LocalDateTime.now())
                .build();
        searchHistoryRepository.save(history);

        log.info("Query completed: word={}, sources={}, fromCache={}, externalCalls={}",
                normalizedWord, actualSources, fromCache, anyExternalCalls);

        return new TimeseriesResponse(
                req.word(),
                start,
                end,
                totalMentions,
                dailyStats,
                actualSources,
                fromCache,
                perSourceList,
                errorsList
        );
    }

    private SourceResult fetchSourceData(
            SourceClient client,
            String sourceId,
            String normalizedWord,
            String originalWord,
            LocalDate start,
            LocalDate end
    ) throws SourceClientException {
        String wordKey = sourceId.equals("wikipedia")
                ? normalizedWord
                : normalizedWord.toLowerCase(Locale.ROOT);

        Map<LocalDate, Integer> cached = dayCache.readDays(sourceId, wordKey, start, end);
        List<LocalDate> missing = dayCache.findMissingDays(cached, start, end);

        if (missing.isEmpty()) {
            log.info("Full cache hit for {}:{} [{} to {}]", sourceId, wordKey, start, end);
            return new SourceResult(cached, false, null);
        }

        List<DateInterval> intervals = dayCache.toIntervals(missing);
        log.info("Partial cache: {}:{} has {}/{} days cached, fetching {} interval(s)",
                sourceId, wordKey, cached.size(), ChronoUnit.DAYS.between(start, end) + 1, intervals.size());

        Map<LocalDate, Integer> allDays = new LinkedHashMap<>(cached);
        Map<LocalDate, Integer> freshDays = new LinkedHashMap<>();
        String skippedError = null;

        for (DateInterval interval : intervals) {
            if (dayCache.hasRecentError(sourceId, wordKey, interval.start(), interval.end())) {
                log.info("Skipping interval [{} to {}] for {}:{} - recent error cached",
                        interval.start(), interval.end(), sourceId, wordKey);
                skippedError = "Some intervals skipped due to recent errors, retry later";
                continue;
            }

            try {
                TimeSeries ts = client.fetchDailyTimeSeries(originalWord, interval.start(), interval.end());
                for (TimeBucket bucket : ts.buckets()) {
                    freshDays.put(bucket.date(), bucket.count());
                    allDays.put(bucket.date(), bucket.count());
                }

                for (LocalDate d = interval.start(); !d.isAfter(interval.end()); d = d.plusDays(1)) {
                    if (!freshDays.containsKey(d)) {
                        freshDays.put(d, 0);
                        allDays.put(d, 0);
                    }
                }
            } catch (SourceClientException e) {
                dayCache.cacheError(sourceId, wordKey, interval.start(), interval.end());
                if (!allDays.isEmpty()) {
                    log.warn("Source {} failed for interval [{} to {}], returning partial data from cache",
                            sourceId, interval.start(), interval.end());
                    if (!freshDays.isEmpty()) {
                        dayCache.writeDays(sourceId, wordKey, freshDays);
                    }
                    return new SourceResult(allDays, true, e);
                }
                throw e;
            }
        }

        if (!freshDays.isEmpty()) {
            dayCache.writeDays(sourceId, wordKey, freshDays);
        }

        if (skippedError != null) {
            if (!allDays.isEmpty()) {
                return new SourceResult(allDays, !freshDays.isEmpty(),
                        new SourceClientException(sourceId, skippedError));
            }
            return new SourceResult(null, false,
                    new SourceClientException(sourceId, "Source recently failed, retry later"));
        }

        return new SourceResult(allDays, true, null);
    }

    private record SourceResult(
            Map<LocalDate, Integer> dayCounts,
            boolean madeExternalCalls,
            SourceClientException cachedError
    ) {}
}
