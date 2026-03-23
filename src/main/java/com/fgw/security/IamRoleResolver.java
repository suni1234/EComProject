package com.fgw.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Maps Cognito group names → IAM Role ARNs.
 * Role ARNs are in application.yml under aws.iam.*
 * All under account: 745791801485 / region: us-east-1
 */
@Service
public class IamRoleResolver {

    @Value("${aws.iam.role-admin}")
    private String adminRole;

    @Value("${aws.iam.role-editor}")
    private String editorRole;

    @Value("${aws.iam.role-viewer}")
    private String viewerRole;

    @Value("${aws.iam.role-default}")
    private String defaultRole;

    public String resolveRoleArn(List<String> cognitoGroups) {
        Map<String, String> roleMap = Map.of(
                "admin",  adminRole,
                "editor", editorRole,
                "viewer", viewerRole
        );

        for (String group : cognitoGroups) {
            String arn = roleMap.get(group.toLowerCase());
            if (arn != null) return arn;
        }

        return defaultRole;
    }
}
