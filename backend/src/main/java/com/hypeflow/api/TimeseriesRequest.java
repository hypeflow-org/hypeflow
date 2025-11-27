package com.hypeflow.api;

import java.time.LocalDate;
import java.util.List;

public record TimeseriesRequest(
        String word,
        LocalDate startDate,
        LocalDate endDate,
        List<String> sources
) {}