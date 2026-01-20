package com.hypeflow.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SearchHistoryDto(
        String word,
        LocalDate startDate,
        LocalDate endDate,
        List<String> sources,
        Integer totalMentions,
        LocalDateTime searchedAt
) {
}
