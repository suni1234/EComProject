package com.fgw.security;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class JwtTokenValidator {
	private static final Logger log = LoggerFactory.getLogger(JwtTokenValidator.class);

    @Value("${aws.cognito.user-pool-id}")
    private String userPoolId;

    @Value("${aws.region}")
    private String region;

    private final JwtPublicKeyService publicKeyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtTokenValidator(JwtPublicKeyService publicKeyService) {
        this.publicKeyService = publicKeyService;
    }

    /**
     * Full JWT validation:
     * 1. Extract kid from JWT header
     * 2. Fetch matching RSA public key from Cognito JWKS
     * 3. Verify RS256 signature
     * 4. Check issuer = Cognito User Pool
     * 5. Check token not expired
     * 6. Check token_use = id or access
     */
    public Claims validate(String token) {
        try {
            // Step 1: Extract kid from JWT header
            String kid = extractKid(token);
            log.debug("JWT kid={}", kid);

            // Step 2: Get correct public key from Cognito JWKS by kid
            RSAPublicKey publicKey = publicKeyService.getPublicKey(kid);

            // Step 3: Verify signature and parse claims
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Step 4: Check issuer
            checkIssuer(claims);

            // Step 5: Check expiry
            checkExpiry(claims);

            // Step 6: Check token_use
            checkTokenUse(claims);

            log.debug("JWT valid for sub={}", claims.getSubject());
            return claims;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("JWT validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts kid from JWT header.
     * JWT = base64(header).base64(payload).signature
     * header contains: { "kid": "...", "alg": "RS256" }
     */
    private String extractKid(String token) {
        try {
            String headerPart = token.split("\\.")[0];
            String headerJson = new String(Base64.getUrlDecoder().decode(headerPart));
            return objectMapper.readTree(headerJson).get("kid").asText();
        } catch (Exception e) {
            throw new RuntimeException("Cannot extract kid from JWT header: " + e.getMessage());
        }
    }

    private void checkIssuer(Claims claims) {
        String expected = "https://cognito-idp." + region
                + ".amazonaws.com/" + userPoolId;
        if (!expected.equals(claims.getIssuer())) {
            throw new RuntimeException(
                    "Invalid issuer. Expected: " + expected + " Got: " + claims.getIssuer());
        }
    }

    private void checkExpiry(Claims claims) {
        if (claims.getExpiration().before(new Date())) {
            throw new RuntimeException("JWT token has expired. Please login again.");
        }
    }

    private void checkTokenUse(Claims claims) {
        String use = claims.get("token_use", String.class);
        if (!"id".equals(use) && !"access".equals(use)) {
            throw new RuntimeException("Invalid token_use: " + use);
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> getGroups(Claims claims) {
        Object g = claims.get("cognito:groups");
        return g instanceof List ? (List<String>) g : List.of();
    }

    public String getUsername(Claims claims) {
        return claims.get("cognito:username", String.class);
    }

    public String getEmail(Claims claims) {
        return claims.get("email", String.class);
    }

    public String getSub(Claims claims) {
        return claims.getSubject();
    }
}
