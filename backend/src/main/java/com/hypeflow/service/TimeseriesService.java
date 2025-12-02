package com.hypeflow.service;

import com.hypeflow.api.DailyStatDto;
import com.hypeflow.api.TimeseriesRequest;
import com.hypeflow.api.TimeseriesResponse;
import com.hypeflow.model.SearchHistory;
import com.hypeflow.model.TimeBucket;
import com.hypeflow.model.TimeSeries;
import com.hypeflow.repo.SearchHistoryRepository;
import com.hypeflow.sources.SourceClient;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimeseriesService {

    private static final Logger log = LoggerFactory.getLogger(TimeseriesService.class);

    private final Map<String, SourceClient> sourceClientsMap;
    private final SearchHistoryRepository searchHistoryRepository;

    public TimeseriesService(List<SourceClient> sourceClients, SearchHistoryRepository searchHistoryRepository) {
        this.sourceClientsMap = sourceClients.stream()
                .collect(Collectors.toMap(
                        SourceClient::sourceId,
                        client -> client
                ));
        this.searchHistoryRepository = searchHistoryRepository;
    }

    public TimeseriesResponse query(TimeseriesRequest req) {
        List<String> requestedSources = req.sources();
        if (requestedSources == null || requestedSources.isEmpty()) {
            requestedSources = new ArrayList<>(sourceClientsMap.keySet());
        }

        Map<LocalDate, Integer> aggregatedCounts = new HashMap<>();
        List<String> actualSources = new ArrayList<>();

        for (String sourceId : requestedSources) {
            SourceClient client = sourceClientsMap.get(sourceId);
            if (client == null) {
                continue;
            }

            actualSources.add(sourceId);

            TimeSeries timeSeries = client.fetchDailyTimeSeries(
                    req.word(),
                    req.startDate(),
                    req.endDate()
            );

            if (log.isDebugEnabled()) {
                long nonZeroDays = timeSeries.buckets().stream().filter(b -> b.count() > 0).count();
                log.debug("Source {} returned {} days with mentions", sourceId, nonZeroDays);
            }

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
               // .granularity(req.granularity()) // если есть в запросе
                .sources(String.join(",", actualSources)) // объединяем список источников в строку
                .totalMentions(totalMentions)
                .searchedAt(java.time.LocalDateTime.now())
                .build();

        searchHistoryRepository.save(history);

        return new TimeseriesResponse(
                req.word(),
                req.startDate(),
                req.endDate(),
                totalMentions,
                dailyStats,
                actualSources,
                false
        );
    }
}
