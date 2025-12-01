package com.hypeflow.service;

import com.hypeflow.api.dto.SearchRequest;
import com.hypeflow.api.dto.SearchResponse;
import com.hypeflow.api.dto.StatisticPoint;
import com.hypeflow.model.SearchHistory;
import com.hypeflow.repo.SearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.hypeflow.model.Granularity;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SearchHistoryRepository historyRepository;
    private final RedisTemplate<String, SearchResponse> redisTemplate;

    private static final String CACHE_PREFIX = "search:";

    @Override
    public SearchResponse search(SearchRequest request) {

        String cacheKey = CACHE_PREFIX + request.word() + ":" + request.startDate() + ":" +
                request.endDate() + ":" + request.granularity();

        // 1. Проверяем кеш
        SearchResponse cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            cached.setFromCache(true);
            cached.setCacheAgeSeconds(0L); // можно вычислять возраст, если нужно
            return cached;
        }

        // 2. Получаем статистику — список StatisticPoint
        List<StatisticPoint> points = fetchStatistics(request);

        // 3. Суммарные упоминания
        int totalMentions = points.stream()
                .mapToInt(StatisticPoint::getMentions)
                .sum();

        // 4. Агрегация totalBySource (сложить по каждому источнику)
        Map<String, Integer> totalBySource = points.stream()
                .flatMap(p -> p.getBySource().entrySet().stream())
                .collect(Collectors.groupingBy(
                        e -> e.getKey(),
                        Collectors.summingInt(Map.Entry::getValue)
                ));

        // 5. Сохраняем агрегат в БД
        SearchHistory history = SearchHistory.builder()
                .word(request.word())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .granularity(Granularity.valueOf(request.granularity().toUpperCase()))
                .sources(String.join(",", request.sources()))
                .totalMentions(totalMentions)
                .searchedAt(LocalDateTime.now())
                .build();

        historyRepository.save(history);

        // 6. Собираем Response
        SearchResponse response = SearchResponse.builder()
                .word(request.word())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .granularity(request.granularity())
                .sources(request.sources())

                .totalMentions(totalMentions)
                .totalBySource(totalBySource)

                .statistics(points)

                .fromCache(false)
                .cacheAgeSeconds(0L)

                .warnings(List.of()) // позже можно добавить предупреждения

                .build();

        // 7. Кладем в Redis на 1 час
        redisTemplate.opsForValue().set(cacheKey, response, Duration.ofHours(1));

        return response;
    }



    @Override
    public List<SearchHistory> getLastSearches(int limit) {
        return historyRepository
                .findAllByOrderBySearchedAtDesc(PageRequest.of(0, limit));
    }

    @Override
    public List<String> getMostSearchedWords(int limit) {
        List<Object[]> results = historyRepository.findMostSearchedWords();
        return results.stream()
                .limit(limit)
                .map(r -> (String) r[0])
                .collect(Collectors.toList());
    }

    private List<StatisticPoint> fetchStatistics(SearchRequest request) {
        // реализовать получение статистики с источников
        return List.of();
    }
}
