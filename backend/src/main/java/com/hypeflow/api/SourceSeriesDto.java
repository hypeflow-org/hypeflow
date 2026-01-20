package com.hypeflow.api;

import java.util.List;

public record SourceSeriesDto(
        String source,
        int totalMentions,
        List<DailyStatDto> dailyStatistics
) {
    /**
     * Backwards-compatible accessor used by existing tests/clients.
     */
    public List<DailyStatDto> dailyStats() {
        return dailyStatistics;
    }
}
