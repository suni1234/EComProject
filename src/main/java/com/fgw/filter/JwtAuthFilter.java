/*
 * package com.fgw.filter;
 * 
 * import java.io.IOException; import java.util.List; import
 * java.util.stream.Collectors;
 * 
 * import org.slf4j.Logger; import org.slf4j.LoggerFactory; import
 * org.springframework.security.authentication.
 * UsernamePasswordAuthenticationToken; import
 * org.springframework.security.core.authority.SimpleGrantedAuthority; import
 * org.springframework.security.core.context.SecurityContextHolder; import
 * org.springframework.stereotype.Component; import
 * org.springframework.web.filter.OncePerRequestFilter;
 * 
 * import com.fgw.security.JwtTokenValidator;
 * 
 * import io.jsonwebtoken.Claims; import jakarta.servlet.FilterChain; import
 * jakarta.servlet.ServletException; import
 * jakarta.servlet.http.HttpServletRequest; import
 * jakarta.servlet.http.HttpServletResponse; import lombok.extern.slf4j.Slf4j;
 * 
 *//**
	 * Intercepts every request exactly once. Extracts Bearer JWT → validates using
	 * public key from Secrets Manager (jwt-public-key-tuqT71 / KMS 64134bd9-...) →
	 * populates Spring Security context.
	 *//*
		 * @Slf4j
		 * 
		 * @Component public class JwtAuthFilter extends OncePerRequestFilter {
		 * 
		 * private static final Logger log =
		 * LoggerFactory.getLogger(OncePerRequestFilter.class);
		 * 
		 * private final JwtTokenValidator tokenValidator;
		 * 
		 * public JwtAuthFilter(JwtTokenValidator tokenValidator) { this.tokenValidator
		 * = tokenValidator; }
		 * 
		 * @Override protected void doFilterInternal(HttpServletRequest request,
		 * HttpServletResponse response, FilterChain chain) throws ServletException,
		 * IOException {
		 * 
		 * String authHeader = request.getHeader("Authorization");
		 * 
		 * if (authHeader == null || !authHeader.startsWith("Bearer ")) {
		 * chain.doFilter(request, response); return; }
		 * 
		 * String token = authHeader.substring(7);
		 * 
		 * try { Claims claims = tokenValidator.validate(token);
		 * 
		 * List<String> groups = tokenValidator.getGroups(claims);
		 * List<SimpleGrantedAuthority> authorities = groups.stream() .map(g -> new
		 * SimpleGrantedAuthority("ROLE_" + g.toUpperCase()))
		 * .collect(Collectors.toList());
		 * 
		 * String username = tokenValidator.getUsername(claims);
		 * 
		 * UsernamePasswordAuthenticationToken auth = new
		 * UsernamePasswordAuthenticationToken(username, token, authorities);
		 * auth.setDetails(claims);
		 * 
		 * SecurityContextHolder.getContext().setAuthentication(auth);
		 * log.debug("JWT authenticated: user={} groups={}", username, groups);
		 * 
		 * } catch (Exception e) { log.warn("JWT auth failed: {}", e.getMessage());
		 * SecurityContextHolder.clearContext();
		 * response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		 * response.setContentType("application/json"); response.getWriter().write(
		 * "{\"error\":\"Unauthorized\",\"message\":\"" + e.getMessage() + "\"}" );
		 * return; }
		 * 
		 * chain.doFilter(request, response); } }
		 */


package com.fgw.filter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fgw.security.JwtTokenValidator;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Intercepts every request exactly once.
 * Supports BOTH Cognito token types:
 *
 *   idToken     → token_use=id     → has "cognito:username", "email", "given_name"
 *   accessToken → token_use=access → has "username", no email/name fields
 *
 * Use idToken  → for /api/profile, /api/token-info  (needs email/name)
 * Use accessToken → for /api/credentials, /api/admin (only needs sub + groups)
 */
@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtTokenValidator tokenValidator;

    public JwtAuthFilter(JwtTokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = tokenValidator.validate(token);

            // ── Username extraction — handles both token types ────────
            // idToken     → "cognito:username" = "venkata12947"
            // accessToken → "username"         = "venkata12947"
            // fallback    → "sub"              = UUID
            String username = tokenValidator.getUsername(claims);  // reads "cognito:username"
            if (username == null) {
                username = claims.get("username", String.class);   // accessToken fallback
            }
            if (username == null) {
                username = claims.getSubject();                    // final fallback → sub UUID
            }
            // ─────────────────────────────────────────────────────────

            // ── Groups / roles ────────────────────────────────────────
            // Both idToken and accessToken carry "cognito:groups" if user is in a group
            List<String> groups = tokenValidator.getGroups(claims);
            List<SimpleGrantedAuthority> authorities = groups.stream()
                    .map(g -> new SimpleGrantedAuthority("ROLE_" + g.toUpperCase()))
                    .collect(Collectors.toList());
            // ─────────────────────────────────────────────────────────

            // ── Token type logging ────────────────────────────────────
            String tokenUse = claims.get("token_use", String.class);
            log.debug("JWT authenticated: user={} tokenUse={} groups={}", 
                      username, tokenUse, groups);
            // ─────────────────────────────────────────────────────────

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(username, token, authorities);
            auth.setDetails(claims);

            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (Exception e) {
            log.warn("JWT auth failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Unauthorized\",\"message\":\"" + e.getMessage() + "\"}"
            );
            return;
        }

        chain.doFilter(request, response);
    }
}











