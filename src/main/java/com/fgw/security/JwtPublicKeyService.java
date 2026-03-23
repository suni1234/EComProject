package com.fgw.security;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Fetches RSA public keys directly from Cognito JWKS endpoint.
 *
 * JWKS URL:
 * https://cognito-idp.us-east-1.amazonaws.com/us-east-1_QANRw4Vko/.well-known/jwks.json
 *
 * Cognito publishes its signing keys here publicly.
 * We fetch by kid (Key ID) from JWT header and cache them.
 */
@Slf4j
@Service
public class JwtPublicKeyService {
	
	private static final Logger log = LoggerFactory.getLogger(JwtPublicKeyService.class);

    @Value("${aws.cognito.user-pool-id}")
    private String userPoolId;

    @Value("${aws.region}")
    private String region;

    private final Map<String, RSAPublicKey> keyCache = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Returns RSAPublicKey matching the kid from JWT header.
     * Fetches from Cognito JWKS URL if not cached.
     */
    public RSAPublicKey getPublicKey(String kid) {
        if (keyCache.containsKey(kid)) {
            log.debug("Using cached public key for kid={}", kid);
            return keyCache.get(kid);
        }
        return fetchAndCacheKeys(kid);
    }

    public void evictCache() {
        log.info("Clearing JWKS key cache");
        keyCache.clear();
    }

    private RSAPublicKey fetchAndCacheKeys(String targetKid) {
        String jwksUrl = String.format(
                "https://cognito-idp.%s.amazonaws.com/%s/.well-known/jwks.json",
                region, userPoolId);

        log.info("Fetching JWKS from Cognito: {}", jwksUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jwksUrl))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("JWKS fetch failed. HTTP: " + response.statusCode());
            }

            JsonNode keys = objectMapper.readTree(response.body()).get("keys");

            if (keys == null || !keys.isArray() || keys.isEmpty()) {
                throw new RuntimeException("No keys in Cognito JWKS response");
            }

            RSAPublicKey targetKey = null;

            for (JsonNode keyNode : keys) {
                String kid = keyNode.get("kid").asText();
                String kty = keyNode.get("kty").asText();
                String n   = keyNode.get("n").asText();
                String e   = keyNode.get("e").asText();

                if (!"RSA".equals(kty)) continue;

                RSAPublicKey key = buildRSAKey(n, e);
                keyCache.put(kid, key);
                log.info("Cached Cognito public key kid={}", kid);

                if (kid.equals(targetKid)) targetKey = key;
            }

            if (targetKid != null && targetKey == null) {
                throw new RuntimeException("kid=" + targetKid + " not found in Cognito JWKS");
            }

            return targetKey != null ? targetKey : keyCache.values().iterator().next();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Cognito JWKS: " + e.getMessage(), e);
        }
    }

    private RSAPublicKey buildRSAKey(String nBase64, String eBase64) {
        try {
            Base64.Decoder dec = Base64.getUrlDecoder();
            BigInteger modulus  = new BigInteger(1, dec.decode(nBase64));
            BigInteger exponent = new BigInteger(1, dec.decode(eBase64));
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (Exception e) {
            throw new RuntimeException("Failed to build RSA public key: " + e.getMessage(), e);
        }
    }
}
