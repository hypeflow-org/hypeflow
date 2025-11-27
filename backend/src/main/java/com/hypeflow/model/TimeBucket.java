package com.hypeflow.model;

import java.time.LocalDate;

public record TimeBucket(
        LocalDate date,
        int count
) {}