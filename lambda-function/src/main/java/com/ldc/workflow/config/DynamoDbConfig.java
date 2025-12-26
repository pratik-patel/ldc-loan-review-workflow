package com.ldc.workflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ssm.SsmClient;

/**
 * Configuration for AWS SDK clients (DynamoDB, SSM).
 */
// @Configuration
public class DynamoDbConfig {

    /**
     * Create DynamoDB client bean.
     */
    @Bean
    public DynamoDbClient dynamoDbClient() {
        System.out.println("DEBUG: Creating DynamoDbClient bean");
        return DynamoDbClient.builder()
                .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                .credentialsProvider(
                        software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider.create())
                .build();
    }

    /**
     * Create SSM (Parameter Store) client bean.
     */
    @Bean
    public SsmClient ssmClient() {
        System.out.println("DEBUG: Creating SsmClient bean");
        return SsmClient.builder()
                .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                .credentialsProvider(
                        software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider.create())
                .build();
    }
}
