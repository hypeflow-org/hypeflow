package com.hypeflow.api.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public record SearchRequest(

        @NotBlank(message = "Search word cannot be empty")
        String word,

        @PastOrPresent(message = "Start date must be in the past or today")
        LocalDate startDate,

        @PastOrPresent(message = "End date must be in the past or today")
        LocalDate endDate,

        List<String> sources, // ["reddit", "twitter"]

        @Pattern(regexp = "daily|weekly|hourly", message = "granularity must be daily, weekly, or hourly")
        String granularity

) {
    public SearchRequest {
        if (sources == null) sources = List.of("reddit");
        if (granularity == null) granularity = "daily";

        // Validate date range
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate cannot be after endDate");
        }
    }

}
