package com.fgw.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fgw.security.IamRoleResolver;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithWebIdentityRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithWebIdentityResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

/**
 * Calls AWS STS AssumeRoleWithWebIdentity to get temporary credentials
 * based on the user's Cognito group / IAM role mapping.
 *
 * Account: 745791801485 | Region: us-east-1
 */
@Slf4j
@Service
public class StsCredentialService {
	
	private static final Logger log = LoggerFactory.getLogger(StsCredentialService.class);

    private final StsClient stsClient;
    private final IamRoleResolver roleResolver;

    // Cache: sub → AwsCredentials (expires automatically)
    private final Map<String, CachedCredentials> cache = new ConcurrentHashMap<>();

    public StsCredentialService(StsClient stsClient, IamRoleResolver roleResolver) {
        this.stsClient = stsClient;
        this.roleResolver = roleResolver;
    }

    public record AwsCredentials(
            String accessKeyId,
            String secretAccessKey,
            String sessionToken,
            String roleArn,
            Instant expiration
    ) {}

    private record CachedCredentials(AwsCredentials credentials, Instant expiresAt) {}

    /**
     * Returns temporary AWS credentials by assuming the right IAM role
     * using the user's raw JWT as the web identity token.
     */
    public AwsCredentials assumeRole(String rawJwt, String sub, List<String> groups) {
        // Return cached creds if still valid (with 5-min buffer)
        CachedCredentials cached = cache.get(sub);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(300))) {
            log.debug("Using cached STS credentials for sub={}", sub);
            return cached.credentials();
        }

        String roleArn = roleResolver.resolveRoleArn(groups);
        log.info("Assuming IAM role {} for sub={}", roleArn, sub);

        AssumeRoleWithWebIdentityRequest request = AssumeRoleWithWebIdentityRequest.builder()
                .roleArn(roleArn)
                .roleSessionName("fgw-" + sub.substring(0, Math.min(sub.length(), 20)))
                .webIdentityToken(rawJwt)
                .durationSeconds(3600)
                .build();

        AssumeRoleWithWebIdentityResponse response =
                stsClient.assumeRoleWithWebIdentity(request);
        Credentials creds = response.credentials();

        AwsCredentials awsCreds = new AwsCredentials(
                creds.accessKeyId(),
                creds.secretAccessKey(),
                creds.sessionToken(),
                roleArn,
                creds.expiration()
        );

        cache.put(sub, new CachedCredentials(awsCreds, creds.expiration()));
        log.info("STS credentials obtained. Role={} Expires={}", roleArn, creds.expiration());
        return awsCreds;
    }
}
