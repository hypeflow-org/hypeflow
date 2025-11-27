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
        boolean fromCache
) {}