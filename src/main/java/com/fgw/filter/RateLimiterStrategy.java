package com.fgw.filter;

import io.github.bucket4j.Bucket;

/**
 * Strategy interface — two implementations:
 *   LocalRateLimiterStrategy  → active on profile "local"  (in-memory)
 *   RedisRateLimiterStrategy  → active on profile "prod"   (ElastiCache)
 *
 * RateLimitFilter injects whichever one Spring activates.
 * No code change needed when switching environments — just set
 * SPRING_PROFILES_ACTIVE=prod in ECS task definition.
 */
public interface RateLimiterStrategy {
    Bucket resolveBucket(String userId);
}