package com.fgw.filter;

/*
 * FILE: src/main/java/com/fgw/filter/MdcLoggingFilter.java
 * ─────────────────────────────────────────────────────────────────────────
 * WHY THIS FILE EXISTS:
 *   Splunk is most powerful when every log line carries request context
 *   so you can search and correlate across all your classes.
 *
 *   Without this filter, a log from JwtAuthFilter has no connection to
 *   a log from StsCredentialService — you can't tell they're the same request.
 *
 *   With this filter, EVERY log line from EVERY class automatically gets:
 *     traceId    — unique ID for this request (16-char hex)
 *     requestId  — unique per HTTP call
 *     httpMethod — GET, POST, etc.
 *     httpPath   — /api/profile, /api/credentials, etc.
 *     clientIp   — real client IP (reads X-Forwarded-For from ALB)
 *
 * HOW IT WORKS:
 *   SLF4J MDC (Mapped Diagnostic Context) is a per-thread key-value store.
 *   When you put values into MDC, the log encoder picks them up automatically.
 *   logback-spring.xml (File 3) is configured with includeMdc=true.
 *
 * SPLUNK SEARCH EXAMPLES after this is deployed:
 *   index=fgw-service traceId="a3f2b19c44d81e00"
 *     → shows every log line for one request, across all classes
 *
 *   index=fgw-service httpPath="/api/admin/dashboard" level=WARN
 *     → shows all warnings on the admin endpoint
 *
 *   index=fgw-service clientIp="1.2.3.4" | timechart count
 *     → shows request rate from one IP over time
 *
 * FILTER ORDER (set in SecurityConfig.java — File 5 of 6):
 *   1. MdcLoggingFilter   ← THIS FILE — runs first, sets traceId
 *   2. JwtAuthFilter      ← your existing filter
 *   3. RateLimitFilter    ← your existing filter
 * ─────────────────────────────────────────────────────────────────────────
 */

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1) // Run before JwtAuthFilter and RateLimitFilter
public class MdcLoggingFilter extends OncePerRequestFilter {

	private static final String TRACE_HEADER = "X-Trace-Id";
	private static final String REQUEST_HEADER = "X-Request-Id";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String traceId = resolveTraceId(request);
		String requestId = UUID.randomUUID().toString();

		try {
			// Put values into MDC — every log line in this thread gets them
			MDC.put("traceId", traceId);
			MDC.put("requestId", requestId);
			MDC.put("httpMethod", request.getMethod());
			MDC.put("httpPath", request.getRequestURI());
			MDC.put("clientIp", resolveClientIp(request));

			// Echo traceId back in response headers so caller can correlate
			response.setHeader(TRACE_HEADER, traceId);
			response.setHeader(REQUEST_HEADER, requestId);

			filterChain.doFilter(request, response);

		} finally {
			// IMPORTANT: always clear MDC — prevents data leaking to next request
			// Tomcat reuses threads, so without this the next request would see
			// the previous request's traceId
			MDC.clear();
		}
	}

	/**
	 * Use traceId from incoming header if present (e.g. ALB or upstream service
	 * already assigned one), otherwise generate a fresh 16-char hex ID.
	 */
	private String resolveTraceId(HttpServletRequest request) {
		String header = request.getHeader(TRACE_HEADER);
		if (header != null && !header.isBlank())
			return header;
		return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
	}

	/**
	 * ALB sets X-Forwarded-For header with the real client IP. Without this you'd
	 * always see the ALB's internal IP instead.
	 */
	private String resolveClientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim(); // first IP = real client
		}
		return request.getRemoteAddr();
	}
}
