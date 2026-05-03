/*
 * package com.fgw.controller;
 * 
 * import com.fgw.security.JwtTokenValidator; import
 * com.fgw.service.StsCredentialService; import io.jsonwebtoken.Claims; import
 * lombok.extern.slf4j.Slf4j; import org.springframework.http.ResponseEntity;
 * import org.springframework.security.access.prepost.PreAuthorize; import
 * org.springframework.security.core.Authentication; import
 * org.springframework.web.bind.annotation.*;
 * 
 * import java.util.List; import java.util.Map;
 * 
 *//**
	 * FGW Service REST API — all routes protected by JwtAuthFilter.
	 *
	 * GET /health → public GET /api/profile → any valid JWT GET /api/token-info →
	 * any valid JWT — shows decoded claims GET /api/credentials → any valid JWT —
	 * returns STS temp creds GET /api/admin/dashboard → ADMIN role only
	 */
/*
 * @Slf4j
 * 
 * @RestController public class GatewayController {
 * 
 * 
 * 
 * private final JwtTokenValidator tokenValidator; private final
 * StsCredentialService stsCredentialService;
 * 
 * public GatewayController(JwtTokenValidator tokenValidator,
 * StsCredentialService stsCredentialService) { this.tokenValidator =
 * tokenValidator; this.stsCredentialService = stsCredentialService; }
 * 
 * // ── Public ────────────────────────────────────────────────────────
 * 
 * @GetMapping("/health") public ResponseEntity<Map<String, String>> health() {
 * return ResponseEntity.ok(Map.of( "status", "UP", "service", "fgw-service",
 * "port", "8080", "account", "745791801485", "region", "us-east-1" )); }
 * 
 * // ── Protected: any authenticated user ─────────────────────────────
 * 
 *//**
	 * Returns profile info extracted from the JWT.
	 *
	 * curl -H "Authorization: Bearer <idToken>" http://localhost:8080/api/profile
	 */
/*
 * @GetMapping("/api/profile") public ResponseEntity<Map<String, Object>>
 * profile(Authentication auth) { Claims claims = (Claims) auth.getDetails();
 * return ResponseEntity.ok(Map.of( "username", auth.getName(), "email",
 * tokenValidator.getEmail(claims), "sub", tokenValidator.getSub(claims),
 * "groups", tokenValidator.getGroups(claims), "roles", auth.getAuthorities()
 * )); }
 * 
 *//**
	 * Shows all decoded JWT claims — useful for debugging.
	 *
	 * curl -H "Authorization: Bearer <idToken>"
	 * http://localhost:8080/api/token-info
	 */
/*
 * @GetMapping("/api/token-info") public ResponseEntity<Map<String, Object>>
 * tokenInfo(Authentication auth) { Claims claims = (Claims) auth.getDetails();
 * return ResponseEntity.ok(Map.of( "sub", claims.getSubject(), "issuer",
 * claims.getIssuer(), "expiry", claims.getExpiration().toString(), "token_use",
 * claims.getOrDefault("token_use", "N/A"), "groups",
 * tokenValidator.getGroups(claims), "email",
 * String.valueOf(claims.getOrDefault("email", "N/A")) )); }
 * 
 *//**
	 * Assumes IAM role via STS and returns temporary AWS credentials.
	 *
	 * curl -H "Authorization: Bearer <idToken>"
	 * http://localhost:8080/api/credentials
	 */
/*
 * @GetMapping("/api/credentials") public ResponseEntity<Map<String, Object>>
 * credentials(Authentication auth) {
 * 
 * System.out.println("hiiiiiiii"); Claims claims = (Claims) auth.getDetails();
 * String rawToken = (String) auth.getCredentials(); String sub =
 * tokenValidator.getSub(claims); List<String> groups =
 * tokenValidator.getGroups(claims);
 * 
 * StsCredentialService.AwsCredentials creds =
 * stsCredentialService.assumeRole(rawToken, sub, groups);
 * 
 * return ResponseEntity.ok(Map.of( "accessKeyId", creds.accessKeyId(),
 * "secretAccessKey", creds.secretAccessKey(), "sessionToken",
 * creds.sessionToken(), "roleArn", creds.roleArn(), "expiration",
 * creds.expiration().toString() )); }
 * 
 * // ── Protected: ADMIN only ─────────────────────────────────────────
 * 
 *//**
	 * Admin-only endpoint — requires Cognito group "admin".
	 *
	 * curl -H "Authorization: Bearer <adminIdToken>"
	 * http://localhost:8080/api/admin/dashboard
	 *//*
		 * @GetMapping("/api/admin/dashboard")
		 * 
		 * @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<Map<String, String>>
		 * adminDashboard(Authentication auth) { return ResponseEntity.ok(Map.of(
		 * "message", "Welcome admin: " + auth.getName(), "secretArn",
		 * "arn:aws:secretsmanager:us-east-1:745791801485:secret:jwt-public-key-tuqT71",
		 * "kmsKeyId", "64134bd9-8ee7-43fb-8c60-7826a0cb53aa", "account", "745791801485"
		 * )); } }
		 */

package com.fgw.controller;

import com.fgw.config.GlobalExceptionHandler;
import com.fgw.security.JwtTokenValidator;
import com.fgw.service.StsCredentialService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * FGW Service REST API — all routes protected by JwtAuthFilter.
 *
 * GET  /health              → public
 * GET  /api/profile         → any valid JWT
 * GET  /api/token-info      → any valid JWT — shows decoded claims
 * GET  /api/credentials     → any valid JWT — returns STS temp creds
 * GET  /api/admin/dashboard → ADMIN role only
 */
@Slf4j
@RestController
public class GatewayController {
	private static final Logger log = LoggerFactory.getLogger(GatewayController.class);

    private final JwtTokenValidator tokenValidator;
    private final StsCredentialService stsCredentialService;

    public GatewayController(JwtTokenValidator tokenValidator,
                              StsCredentialService stsCredentialService) {
        this.tokenValidator = tokenValidator;
        this.stsCredentialService = stsCredentialService;
    }

    // ── Public ────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status",  "UP",
                "service", "fgw-service",
                "port",    "8082",
                "account", "745791801485",
                "region",  "us-east-1"
        ));
    }

    // ── Protected: any authenticated user ─────────────────────────────

    /**
     * Returns profile info extracted from the JWT.
     *
     * curl -H "Authorization: Bearer <idToken>" http://localhost:8082/api/profile
     */
    @GetMapping("/api/profile")
    public ResponseEntity<Map<String, Object>> profile(Authentication auth) {
        Claims claims = extractClaims(auth);

        // ── accessToken has no email/given_name — use null-safe fallbacks ──
        String email    = tokenValidator.getEmail(claims);
        String username = auth.getName();

        return ResponseEntity.ok(Map.of(
                "username", username  != null ? username : "N/A",   // ✅ never null
                "email",    email     != null ? email    : "N/A",   // ✅ accessToken has no email
                "sub",      tokenValidator.getSub(claims),           // ✅ always present
                "groups",   tokenValidator.getGroups(claims),        // ✅ always a List
                "roles",    auth.getAuthorities(),                   // ✅ always a List
                "tokenUse", String.valueOf(claims.get("token_use"))  // ✅ shows id or access
        ));
    }

    /**
     * Shows all decoded JWT claims — useful for debugging.
     *
     * curl -H "Authorization: Bearer <idToken>" http://localhost:8082/api/token-info
     */
    @GetMapping("/api/token-info")
    public ResponseEntity<Map<String, Object>> tokenInfo(Authentication auth) {
        Claims claims = extractClaims(auth);

        return ResponseEntity.ok(Map.of(
                "sub",       claims.getSubject(),
                "issuer",    claims.getIssuer(),
                "expiry",    claims.getExpiration().toString(),
                "token_use", String.valueOf(claims.getOrDefault("token_use", "N/A")),
                "groups",    tokenValidator.getGroups(claims),
                "email",     String.valueOf(claims.getOrDefault("email", "N/A"))  // ✅ already safe
        ));
    }

    /**
     * Assumes IAM role via STS and returns temporary AWS credentials.
     *
     * curl -H "Authorization: Bearer <idToken>" http://localhost:8082/api/credentials
     */
    @GetMapping("/api/credentials")
    public ResponseEntity<Map<String, Object>> credentials(Authentication auth) {
        Claims claims       = extractClaims(auth);    // ← null-safe, throws 401 if invalid
        String rawToken     = (String) auth.getCredentials();
        String sub          = tokenValidator.getSub(claims);
        List<String> groups = tokenValidator.getGroups(claims);

        StsCredentialService.AwsCredentials creds =
                stsCredentialService.assumeRole(rawToken, sub, groups);

        return ResponseEntity.ok(Map.of(
                "accessKeyId",     creds.accessKeyId(),
                "secretAccessKey", creds.secretAccessKey(),
                "sessionToken",    creds.sessionToken(),
                "roleArn",         creds.roleArn(),
                "expiration",      creds.expiration().toString()
        ));
    }

    // ── Protected: ADMIN only ─────────────────────────────────────────

    /**
     * Admin-only endpoint — requires Cognito group "admin".
     *
     * curl -H "Authorization: Bearer <adminIdToken>" http://localhost:8082/api/admin/dashboard
     */
    @GetMapping("/api/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> adminDashboard(Authentication auth) {
        extractClaims(auth);    // ← validates auth, throws 401 if invalid
        return ResponseEntity.ok(Map.of(
                "message",   "Welcome admin: " + auth.getName(),
                "secretArn", "arn:aws:secretsmanager:us-east-1:745791801485:secret:jwt-public-key-tuqT71",
                "kmsKeyId",  "64134bd9-8ee7-43fb-8c60-7826a0cb53aa",
                "account",   "745791801485"
        ));
    }

    // ── Private helper ────────────────────────────────────────────────

    /**
     * Centralized null-safe Claims extractor.
     *
     * Covers all failure cases:
     *   - auth is null (no token sent)
     *   - auth is AnonymousAuthenticationToken (principal = "anonymousUser")
     *   - auth.getDetails() is not a Claims object (wrong token type)
     *
     * Throws 401 ResponseStatusException instead of NullPointerException.
     * GlobalExceptionHandler no longer sees a runtime crash — just a clean 401.
     */
    private Claims extractClaims(Authentication auth) {
        if (auth == null
                || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())
                || !(auth.getDetails() instanceof Claims)) {

            log.warn("extractClaims failed — invalid or missing JWT auth: {}",
                     auth == null ? "null" : auth.getClass().getSimpleName());

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Valid JWT token required");
        }
        return (Claims) auth.getDetails();
    }
}
