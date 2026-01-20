package com.hypeflow.api;

public record SourceErrorDto(
        String source,
        String code,
        String message
) {}
