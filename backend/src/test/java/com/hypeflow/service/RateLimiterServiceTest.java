package com.hypeflow.service;

import com.hypeflow.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private RedisTemplate<String, Object> redis;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @Mock
    private RateLimitProperties properties;

    @InjectMocks
    private RateLimiterService service;


    @Test
    void rateLimitingDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        RateLimitResult result = service.checkLimit("clientA");

        assertTrue(result.allowed());
        verify(redis, never()).opsForValue();
    }

    @Test
    void firstRequestShouldSetExpiryAndAllow() {
        when(redis.opsForValue()).thenReturn(valueOps);

        when(properties.isEnabled()).thenReturn(true);
        when(properties.getRequestsPerMinute()).thenReturn(5);

        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redis.getExpire(anyString())).thenReturn(60L);

        RateLimitResult result = service.checkLimit("clientA");

        assertTrue(result.allowed());

        verify(redis).expire(
                eq("ratelimit:clientA"),
                eq(Duration.ofSeconds(60))
        );
    }

    @Test
    void requestWithinLimit() {
        when(redis.opsForValue()).thenReturn(valueOps);

        when(properties.isEnabled()).thenReturn(true);
        when(properties.getRequestsPerMinute()).thenReturn(3);

        when(valueOps.increment(anyString())).thenReturn(2L);
        when(redis.getExpire(anyString())).thenReturn(30L);

        RateLimitResult result = service.checkLimit("clientA");

        assertTrue(result.allowed());
    }

    @Test
    void exceedingLimit() {
        when(redis.opsForValue()).thenReturn(valueOps);

        when(properties.isEnabled()).thenReturn(true);
        when(properties.getRequestsPerMinute()).thenReturn(2);

        when(valueOps.increment(anyString())).thenReturn(3L);
        when(redis.getExpire(anyString())).thenReturn(25L);

        RateLimitResult result = service.checkLimit("clientA");

        assertFalse(result.allowed());
        assertEquals(25, result.retryAfterSeconds());
    }

    @Test
    void ttlMissingShouldFallbackToDefaultWindow() {
        when(redis.opsForValue()).thenReturn(valueOps);

        when(properties.isEnabled()).thenReturn(true);
        when(properties.getRequestsPerMinute()).thenReturn(1);

        when(valueOps.increment(anyString())).thenReturn(2L);
        when(redis.getExpire(anyString())).thenReturn(-1L);

        RateLimitResult result = service.checkLimit("clientA");

        assertFalse(result.allowed());
        assertEquals(60, result.retryAfterSeconds());
    }

    @Test
    void nullIncrementResult() {
        when(redis.opsForValue()).thenReturn(valueOps);

        when(properties.isEnabled()).thenReturn(true);
        when(properties.getRequestsPerMinute()).thenReturn(10);

        when(valueOps.increment(anyString())).thenReturn(null);
        when(redis.getExpire(anyString())).thenReturn(60L);

        RateLimitResult result = service.checkLimit("clientA");

        assertTrue(result.allowed());
    }

    @Test
    void differentClients() {
        when(redis.opsForValue()).thenReturn(valueOps);

        when(properties.isEnabled()).thenReturn(true);
        when(properties.getRequestsPerMinute()).thenReturn(1);

        when(valueOps.increment("ratelimit:clientA")).thenReturn(1L);
        when(valueOps.increment("ratelimit:clientB")).thenReturn(2L);
        when(redis.getExpire(anyString())).thenReturn(60L);

        RateLimitResult a = service.checkLimit("clientA");
        RateLimitResult b = service.checkLimit("clientB");

        assertTrue(a.allowed());
        assertFalse(b.allowed());
    }
}
