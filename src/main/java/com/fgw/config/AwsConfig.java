package com.fgw.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.sts.StsClient;

@Configuration
public class AwsConfig {

    @Value("${aws.region}")
    private String region;

    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public KmsClient kmsClient() {
        return KmsClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public StsClient stsClient() {
        return StsClient.builder()
                .region(Region.of(region))
                .build();
    }
}
