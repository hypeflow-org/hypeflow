package com.hypeflow.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;
import java.util.List;

public record TimeseriesRequest(

        @NotBlank(message = "Search word cannot be empty")
        String word,

        @PastOrPresent(message = "Start date must be in the past or today")
        LocalDate startDate,

        @PastOrPresent(message = "End date must be in the past or today")
        LocalDate endDate,

        List<String> sources

) {
    public TimeseriesRequest {
        if (sources == null) sources = List.of("reddit");
       // if (granularity == null) granularity = "daily";
        // Validate date range
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate cannot be after endDate");
        }
    }

}