package com.hypeflow.api;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redis;

    public HealthController(DataSource dataSource, RedisTemplate<String, Object> redis) {
        this.dataSource = dataSource;
        this.redis = redis;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        boolean healthy = true;

        try (Connection conn = dataSource.getConnection()) {
            response.put("mysql", Map.of(
                    "status", "UP",
                    "database", conn.getCatalog()
            ));
        } catch (Exception e) {
            response.put("mysql", Map.of(
                    "status", "DOWN",
                    "error", e.getMessage()
            ));
            healthy = false;
        }

        try {
            var factory = redis.getConnectionFactory();
            if (factory == null) {
                throw new IllegalStateException("Redis connection factory is null");
            }
            try (RedisConnection conn = factory.getConnection()) {
                String pong = conn.ping();
                response.put("redis", Map.of(
                        "status", "UP",
                        "ping", pong != null ? pong : "OK"
                ));
            }
        } catch (Exception e) {
            response.put("redis", Map.of(
                    "status", "DOWN",
                    "error", e.getMessage()
            ));
            healthy = false;
        }

        response.put("status", healthy ? "UP" : "DOWN");

        return healthy
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(503).body(response);
    }

}
