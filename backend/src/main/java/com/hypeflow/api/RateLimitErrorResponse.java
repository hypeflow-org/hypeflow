package com.hypeflow.api;

public record RateLimitErrorResponse(
        String error,
        String message,
        int retryAfterSeconds
) {
}
