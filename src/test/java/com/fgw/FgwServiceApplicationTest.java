package com.fgw;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "aws.region=us-east-1",
    "aws.account-id=745791801485",
    "aws.cognito.user-pool-id=us-east-1_TEST",
    "aws.cognito.client-id=test-client-id",
    "aws.cognito.issuer-uri=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_TEST",
    "aws.secrets-manager.secret-name=jwt-public-key",
    "aws.secrets-manager.secret-arn=arn:aws:secretsmanager:us-east-1:745791801485:secret:jwt-public-key-tuqT71",
    "aws.kms.key-id=64134bd9-8ee7-43fb-8c60-7826a0cb53aa",
    "aws.kms.key-arn=arn:aws:kms:us-east-1:745791801485:key/64134bd9-8ee7-43fb-8c60-7826a0cb53aa",
    "aws.iam.role-admin=arn:aws:iam::745791801485:role/FGW-AdminRole",
    "aws.iam.role-editor=arn:aws:iam::745791801485:role/FGW-EditorRole",
    "aws.iam.role-viewer=arn:aws:iam::745791801485:role/FGW-ViewerRole",
    "aws.iam.role-default=arn:aws:iam::745791801485:role/FGW-DefaultRole",
    "auth-service.url=http://localhost:8081"
})
class FgwServiceApplicationTest {

    @Test
    void contextLoads() {
        // Verifies Spring context starts without errors
    }
}
