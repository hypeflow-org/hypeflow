package com.hypeflow.api.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Singular;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
public class SearchResponse {

    private String word;
    private LocalDate startDate;
    private LocalDate endDate;
    private String granularity;

    private Integer totalMentions;
    private Map<String, Integer> totalBySource;

    @Singular("statisticPoint")
    private List<StatisticPoint> statistics;    // Точки графика (время + mentions + по источникам)

    private List<String> sources;         // Источники, которые были задействованы
    private Boolean fromCache;            // Данные из кэша или свежие
    private Long cacheAgeSeconds;         // Сколько секунд данные лежали в кэше
    private List<String> warnings;        // Опциональные предупреждения (ошибки источников)
    private Double trendScore;            // Индекс популярности для фронта (0-100)
}




/*{
  "word": "bitcoin",
  "startDate": "2024-11-20",
  "endDate": "2024-11-27",
  "granularity": "daily",
  "totalMentions": 342,
  "totalBySource": {"Reddit": 120, "Twitter": 100, "NewsAPI": 122},
  "statistics": [
    {
      "timestamp": "2024-11-20T00:00:00",
      "mentions": 45,
      "bySource": {"Reddit": 20, "Twitter": 15, "NewsAPI": 10}
    },
    {
      "timestamp": "2024-11-21T00:00:00",
      "mentions": 52,
      "bySource": {"Reddit": 20, "Twitter": 20, "NewsAPI": 12}
    }
  ],
  "sources": ["Reddit", "Twitter", "NewsAPI"],
  "fromCache": false,
  "cacheAgeSeconds": 0,
  "warnings": [],
  "trendScore": 78.5
}
*/







