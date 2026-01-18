package com.hypeflow.service;

import com.hypeflow.config.RateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private static final String RATE_LIMIT_PREFIX = "ratelimit:";
    private static final int WINDOW_SECONDS = 60;

    private final RedisTemplate<String, Object> redis;
    private final RateLimitProperties properties;

    public RateLimiterService(RedisTemplate<String, Object> redis, RateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public RateLimitResult checkLimit(String clientId) {
        if (!properties.isEnabled()) {
            return RateLimitResult.ok();
        }

        String key = RATE_LIMIT_PREFIX + clientId;
        int maxRequests = properties.getRequestsPerMinute();

        Long currentCount = redis.opsForValue().increment(key);
        if (currentCount == null) {
            currentCount = 1L;
        }

        // Set expiry only on first request (when count is 1)
        if (currentCount == 1) {
            redis.expire(key, Duration.ofSeconds(WINDOW_SECONDS));
        }

        Long ttl = redis.getExpire(key);
        int retryAfterSeconds = (ttl != null && ttl > 0) ? ttl.intValue() : WINDOW_SECONDS;

        if (currentCount > maxRequests) {
            log.warn("Rate limit exceeded for client: {} (count: {}, max: {})", clientId, currentCount, maxRequests);
            return RateLimitResult.limitExceeded(retryAfterSeconds);
        }

        return RateLimitResult.ok();
    }

}
