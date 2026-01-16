package com.hypeflow.api;

import java.time.LocalDate;
import java.util.List;

public record TimeseriesResponse(
        String word,
        LocalDate startDate,
        LocalDate endDate,
        int totalMentions,
        List<DailyStatDto> dailyStatistics,
        List<String> sources,
        boolean fromCache,
        List<SourceSeriesDto> perSource,
        List<SourceErrorDto> errors
) {
    /**
     * Backward-compatible constructor for existing code.
     */
    public TimeseriesResponse(
            String word,
            LocalDate startDate,
            LocalDate endDate,
            int totalMentions,
            List<DailyStatDto> dailyStatistics,
            List<String> sources,
            boolean fromCache
    ) {
        this(word, startDate, endDate, totalMentions, dailyStatistics,
                sources, fromCache, List.of(), List.of());
    }
}
