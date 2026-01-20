package com.hypeflow.model;

public record SourceDescriptor(
        String id,
        String title,
        String description,
        String category,
        String unit,
        Integer maxRangeDays,
        String rateLimitNote,
        String docsUrl,
        Double defaultWeight,
        boolean enabled
) {}
