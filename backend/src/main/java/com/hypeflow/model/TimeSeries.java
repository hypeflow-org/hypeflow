package com.hypeflow.model;

import java.util.List;

public record TimeSeries(
        String source,
        String topic,
        TimeInterval granularity,
        List<TimeBucket> buckets
) {}