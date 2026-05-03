/*
 * package com.fgw.filter;
 * 
 * import java.io.IOException; import java.time.Duration; import java.util.Map;
 * import java.util.concurrent.ConcurrentHashMap;
 * 
 * import org.slf4j.Logger; import org.slf4j.LoggerFactory; import
 * org.springframework.beans.factory.annotation.Value; import
 * org.springframework.security.core.Authentication; import
 * org.springframework.security.core.context.SecurityContextHolder; import
 * org.springframework.stereotype.Component; import
 * org.springframework.web.filter.OncePerRequestFilter;
 * 
 * import io.github.bucket4j.Bandwidth; import io.github.bucket4j.Bucket; import
 * io.jsonwebtoken.Claims; import jakarta.servlet.FilterChain; import
 * jakarta.servlet.ServletException; import
 * jakarta.servlet.http.HttpServletRequest; import
 * jakarta.servlet.http.HttpServletResponse; import lombok.extern.slf4j.Slf4j;
 * 
 * @Slf4j
 * 
 * @Component public class RateLimitFilter extends OncePerRequestFilter {
 * 
 * private static final Logger log =
 * LoggerFactory.getLogger(RateLimitFilter.class);
 * 
 * // One bucket per user — lives in JVM memory private final Map<String,
 * Bucket> buckets = new ConcurrentHashMap<>();
 * 
 * @Value("${rate-limit.requests-per-minute:3}") private int requestsPerMinute;
 * 
 * @Override protected void doFilterInternal(HttpServletRequest request,
 * HttpServletResponse response, FilterChain chain) throws ServletException,
 * IOException {
 * 
 * Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 * 
 * // Skip rate limit for unauthenticated requests if (auth == null ||
 * !auth.isAuthenticated() || auth.getDetails() == null) {
 * chain.doFilter(request, response); return; }
 * 
 * // Get user identity from JWT claims Claims claims = (Claims)
 * auth.getDetails(); String userId = claims.getSubject(); // "sub" from JWT
 * 
 * // Get or create bucket for this user Bucket bucket =
 * buckets.computeIfAbsent(userId, id -> createBucket());
 * 
 * // Try to consume 1 token if (bucket.tryConsume(1)) { // ALLOWED long
 * remaining = bucket.getAvailableTokens();
 * response.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
 * response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
 * log.debug("Rate limit OK: user={} remaining={}", userId, remaining);
 * chain.doFilter(request, response); } else { // BLOCKED — 429
 * log.warn("Rate limit EXCEEDED: user={}", userId); response.setStatus(429);
 * response.setContentType("application/json");
 * response.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
 * response.setHeader("X-RateLimit-Remaining", "0");
 * response.setHeader("Retry-After", "60"); response.getWriter().write(""" {
 * "error": "RATE_LIMIT_EXCEEDED", "userId": "%s", "limit": %d, "message":
 * "Too many requests. Retry after 60 seconds." } """.formatted(userId,
 * requestsPerMinute)); } }
 * 
 * // Each user gets a fresh bucket: // requestsPerMinute tokens max, refills
 * every 1 minute private Bucket createBucket() { Bandwidth limit =
 * Bandwidth.builder() .capacity(requestsPerMinute)
 * .refillGreedy(requestsPerMinute, Duration.ofMinutes(1)) .build(); return
 * Bucket.builder() .addLimit(limit) .build(); } }
 */
package com.fgw.filter;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Rate-limit filter — profile-agnostic.
 *
 * Delegates bucket resolution to RateLimiterStrategy:
 *   local profile → LocalRateLimiterStrategy  (in-memory)
 *   prod  profile → RedisRateLimiterStrategy  (ElastiCache)
 *
 * This class has ZERO if/else for environment — Spring injects
 * the correct strategy automatically based on active profile.
 */
@Slf4j
@Component

public class RateLimitFilter extends OncePerRequestFilter {
	private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // Spring injects LocalRateLimiterStrategy or RedisRateLimiterStrategy
    // depending on SPRING_PROFILES_ACTIVE
    private final RateLimiterStrategy rateLimiterStrategy;

    @Value("${rate-limit.requests-per-minute:5}")
    private int requestsPerMinute;
    
    @Autowired  // ← add this explicitly to force Spring injection
    public RateLimitFilter(RateLimiterStrategy rateLimiterStrategy) {
        this.rateLimiterStrategy = rateLimiterStrategy;
        log.info("RateLimitFilter → strategy: {}",
                  rateLimiterStrategy.getClass().getSimpleName());
    }
    
    

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Skip rate limit for unauthenticated requests (same as before)
        if (auth == null || !auth.isAuthenticated() || !(auth.getDetails() instanceof Claims) ||auth.getDetails() == null) {
            chain.doFilter(request, response);
            return;
        }

        // Get user identity from JWT claims (same as before)
        Claims claims = (Claims) auth.getDetails();
        String userId = claims.getSubject();                  // "sub" from JWT

        // Strategy resolves the bucket (local = HashMap, prod = Redis)
        Bucket bucket = rateLimiterStrategy.resolveBucket(userId);

        // tryConsumeAndReturnRemaining gives exact retry wait time
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            // ── ALLOWED ──────────────────────────────────────────────
            long remaining = probe.getRemainingTokens();
            response.setHeader("X-RateLimit-Limit",     String.valueOf(requestsPerMinute));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            log.debug("Rate limit OK: user={} remaining={}", userId, remaining);
            chain.doFilter(request, response);

        } else {
            // ── BLOCKED — 429 ────────────────────────────────────────
            long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000L;
            log.warn("Rate limit EXCEEDED: user={} retryAfterSec={}", userId, retryAfterSeconds);

            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("X-RateLimit-Limit",     String.valueOf(requestsPerMinute));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("Retry-After",           String.valueOf(retryAfterSeconds));
            response.getWriter().write("""
                    {
                        "error": "RATE_LIMIT_EXCEEDED",
                        "userId": "%s",
                        "limit": %d,
                        "retryAfterSeconds": %d,
                        "message": "Too many requests. Retry after %d seconds."
                    }
                    """.formatted(userId, requestsPerMinute, retryAfterSeconds, retryAfterSeconds));
        }
    }
}

