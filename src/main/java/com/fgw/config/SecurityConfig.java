package com.fgw.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fgw.filter.JwtAuthFilter;
import com.fgw.filter.RateLimitFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private final JwtAuthFilter jwtAuthFilter;
	private final RateLimitFilter rateLimitFilter;

	public SecurityConfig(JwtAuthFilter jwtAuthFilter, RateLimitFilter rateLimitFilter) {
		this.jwtAuthFilter = jwtAuthFilter;
		this.rateLimitFilter = rateLimitFilter;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth.requestMatchers("/health", "/actuator/**").permitAll()
						.requestMatchers("/api/admin/**").hasRole("ADMIN").requestMatchers("/api/**").authenticated()
						.anyRequest().permitAll())
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterAfter(rateLimitFilter, JwtAuthFilter.class);

		return http.build();
	}
}
