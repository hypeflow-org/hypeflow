package com.hypeflow.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class StatisticPoint {

    private LocalDateTime timestamp;       // Время точки (hourly/daily/weekly)
    private Integer mentions;              // Кол-во упоминаний за этот период
    private Map<String, Integer> bySource; // Подробно по каждому источнику

}
