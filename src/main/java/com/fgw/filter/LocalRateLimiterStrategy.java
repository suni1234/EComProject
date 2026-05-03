package com.fgw.filter;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;

/**
 * ─────────────────────────────────────────────────────────────
 *  LOCAL PROFILE ONLY  (@Profile("local"))
 *  Activated when: SPRING_PROFILES_ACTIVE=local  (default)
 * ─────────────────────────────────────────────────────────────
 *
 *  Uses ConcurrentHashMap — buckets live in JVM memory.
 *  Simple, zero dependencies, perfect for local dev & unit tests.
 *
 *  ⚠ NOT for production — each pod has its own counter.
 *     Use RedisRateLimiterStrategy for ECS / multi-pod deployments.
 */
@Slf4j
@Component
@Profile("local")                          // ← ONLY active for local dev
public class LocalRateLimiterStrategy implements RateLimiterStrategy {

    // ── YOUR ORIGINAL CODE — untouched ───────────────────────────────
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${rate-limit.requests-per-minute:5}")
    private int requestsPerMinute;

    @Override
    public Bucket resolveBucket(String userId) {
        return buckets.computeIfAbsent(userId, id -> createBucket());
    }

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
    // ─────────────────────────────────────────────────────────────────
}