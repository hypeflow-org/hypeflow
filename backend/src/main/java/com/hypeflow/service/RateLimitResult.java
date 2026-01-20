package com.hypeflow.service;

public record RateLimitResult(boolean allowed, int retryAfterSeconds) {

    public static RateLimitResult ok() {
        return new RateLimitResult(true, 0);
    }

    public static RateLimitResult limitExceeded(int retryAfterSeconds) {
        return new RateLimitResult(false, retryAfterSeconds);
    }

}
