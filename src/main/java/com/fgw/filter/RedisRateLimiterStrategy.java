package com.fgw.filter;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import lombok.extern.slf4j.Slf4j;

/**
 * ─────────────────────────────────────────────────────────────
 *  PROD PROFILE ONLY  (@Profile("prod"))
 *  Activated when: SPRING_PROFILES_ACTIVE=prod  (set in ECS task)
 * ─────────────────────────────────────────────────────────────
 *
 *  Uses Bucket4j + Redis (ElastiCache) — buckets stored in Redis,
 *  shared across ALL ECS pods atomically.
 *
 *  Requires env vars in ECS task definition:
 *    SPRING_REDIS_HOST  = <elasticache-cluster-endpoint>
 *    SPRING_REDIS_PORT  = 6379
 *    SPRING_REDIS_SSL   = true
 */
@Slf4j
@Component
@Profile("prod")                           // ← ONLY active on ECS / cloud
public class RedisRateLimiterStrategy implements RateLimiterStrategy {
	private static final Logger log = LoggerFactory.getLogger(RedisRateLimiterStrategy.class);

    private static final String KEY_PREFIX = "rl:";

    private final ProxyManager<byte[]> proxyManager;

    @Value("${rate-limit.requests-per-minute:5}")
    private int requestsPerMinute;

    /**
     * RedisClient is auto-configured by Spring Boot from:
     *   spring.data.redis.host / port / ssl
     * No extra @Bean needed — Spring wires it automatically.
     */
    public RedisRateLimiterStrategy(RedisClient redisClient) {
        StatefulRedisConnection<byte[], byte[]> connection =
                redisClient.connect(ByteArrayCodec.INSTANCE);

        this.proxyManager = LettuceBasedProxyManager
                .builderFor(connection)
                .build();

        log.info("RedisRateLimiterStrategy initialized — distributed rate limit active");
    }

    @Override
    public Bucket resolveBucket(String userId) {
        byte[] key = (KEY_PREFIX + userId).getBytes();

        // Atomically get-or-create bucket in Redis.
        // If pod restarts, the bucket survives in ElastiCache.
        return proxyManager.builder()
                .build(key, this::bucketConfiguration);
    }

    private BucketConfiguration bucketConfiguration() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return BucketConfiguration.builder()
                .addLimit(limit)
                .build();
    }
}