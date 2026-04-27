package com.fgw.config;

/*
 * FILE: src/main/java/com/fgw/config/SecurityConfig.java
 * ─────────────────────────────────────────────────────────────────────────
 * CHANGES FROM YOUR ORIGINAL:
 *   1. Import MdcLoggingFilter added
 *   2. MdcLoggingFilter field + constructor parameter added
 *   3. .addFilterBefore(mdcLoggingFilter, ...) line added
 *
 * Everything else is IDENTICAL to your original SecurityConfig.java
 * ─────────────────────────────────────────────────────────────────────────
 */

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fgw.filter.JwtAuthFilter;
import com.fgw.filter.MdcLoggingFilter; // ← ADDED (import for new filter)
import com.fgw.filter.RateLimitFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter    jwtAuthFilter;
    private final RateLimitFilter  rateLimitFilter;
    private final MdcLoggingFilter mdcLoggingFilter; // ← ADDED

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          RateLimitFilter rateLimitFilter,
                          MdcLoggingFilter mdcLoggingFilter) { // ← ADDED parameter
        this.jwtAuthFilter    = jwtAuthFilter;
        this.rateLimitFilter  = rateLimitFilter;
        this.mdcLoggingFilter = mdcLoggingFilter; // ← ADDED
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/actuator/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())

                // ── Filter order: ────────────────────────────────────────
                // 1. MdcLoggingFilter  → sets traceId/httpPath/clientIp in MDC
                //    (runs first so ALL subsequent logs carry these fields)
                // 2. JwtAuthFilter     → your original JWT validation
                // 3. RateLimitFilter   → your original rate limiting
                .addFilterBefore(mdcLoggingFilter, UsernamePasswordAuthenticationFilter.class) // ← ADDED
                .addFilterBefore(jwtAuthFilter,    UsernamePasswordAuthenticationFilter.class) // ← your original
                .addFilterAfter(rateLimitFilter,   JwtAuthFilter.class);                       // ← your original

        return http.build();
    }
}