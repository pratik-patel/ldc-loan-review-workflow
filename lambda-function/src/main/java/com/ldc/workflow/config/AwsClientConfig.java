package com.ldc.workflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.ssm.SsmClient;

/**
 * Configuration for AWS SDK v2 clients.
 * 
 * These clients are initialized as Spring beans to ensure they are reused
 * across Lambda invocations, improving performance and reducing cold start time.
 * 
 * Best Practice: Initialize AWS clients as beans rather than creating new instances
 * for each invocation. This allows connection pooling and reuse.
 */
@Configuration
public class AwsClientConfig {

    /**
     * DynamoDB client for state persistence and audit trail.
     */
    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder().build();
    }

    /**
     * SSM (Systems Manager) client for parameter store configuration.
     */
    @Bean
    public SsmClient ssmClient() {
        return SsmClient.builder().build();
    }

    /**
     * SES (Simple Email Service) client for sending notifications.
     */
    @Bean
    public SesClient sesClient() {
        return SesClient.builder().build();
    }

    /**
     * SQS (Simple Queue Service) client for reclass confirmations.
     */
    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder().build();
    }

}
