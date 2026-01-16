package com.hypeflow.api;

import java.util.List;

public record SourceSeriesDto(
        String source,
        int totalMentions,
        List<DailyStatDto> dailyStatistics
) {}
