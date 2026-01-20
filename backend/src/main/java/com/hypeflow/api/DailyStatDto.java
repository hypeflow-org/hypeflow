package com.hypeflow.api;

import java.time.LocalDate;

public record DailyStatDto(
        LocalDate date,
        int mentions
) {}