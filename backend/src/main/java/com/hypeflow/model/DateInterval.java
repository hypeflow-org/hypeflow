package com.hypeflow.model;

import java.time.LocalDate;

public record DateInterval(LocalDate start, LocalDate end) {

    public DateInterval {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start must not be after end");
        }
    }
}
