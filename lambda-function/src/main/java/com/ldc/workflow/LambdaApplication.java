package com.ldc.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot application entry point for Lambda functions.
 * 
 * This application uses Spring Cloud Function to expose Lambda handlers.
 * The handler is configured via spring.cloud.function.definition property.
 * 
 * Lambda Handler:
 * org.springframework.cloud.function.adapter.aws.FunctionInvoker
 * 
 * Best Practices for Spring Boot on Lambda:
 * 1. Use Lambda Layers to separate dependencies from code
 * 2. Exclude Spring Boot from function JAR (included in layer)
 * 3. Configure spring.cloud.function.definition to specify handler
 * 4. Use environment variables for configuration
 * 5. Initialize AWS clients as beans for reuse across invocations
 */
@SpringBootApplication
@ComponentScan(basePackages = { "com.ldc.workflow" })
public class LambdaApplication {

    public static void main(String[] args) {
        SpringApplication.run(LambdaApplication.class, args);
    }
}
