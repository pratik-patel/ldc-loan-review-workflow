package com.ldc.workflow;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * AWS Lambda handler for LDC Loan Review Workflow.
 * 
 * This handler initializes Spring Boot context and routes requests to appropriate handlers.
 * It implements the AWS Lambda RequestStreamHandler interface to avoid JSON deserialization issues.
 */
public class LambdaHandler implements RequestStreamHandler {

    private static final Logger logger = LoggerFactory.getLogger(LambdaHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static ApplicationContext applicationContext;

    static {
        try {
            logger.info("Initializing Spring Boot application context");
            applicationContext = SpringApplication.run(LambdaApplication.class);
            logger.info("Spring Boot application context initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize Spring Boot application context", e);
            throw new RuntimeException("Failed to initialize Spring Boot context", e);
        }
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) {
        try {
            // Read input stream and parse JSON
            String inputStr = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            logger.info("Lambda handler invoked with input: {}", inputStr);
            
            JsonNode inputNode = objectMapper.readTree(inputStr);
            
            // Get the loan review router bean from Spring context
            com.ldc.workflow.handlers.LoanReviewRouter router = 
                applicationContext.getBean(com.ldc.workflow.handlers.LoanReviewRouter.class);
            
            // Route the request to the appropriate handler
            JsonNode response = router.apply(inputNode);
            
            logger.info("Lambda handler returning response: {}", response);
            
            // Write response to output stream
            String responseStr = objectMapper.writeValueAsString(response);
            output.write(responseStr.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (Exception e) {
            logger.error("Error processing Lambda request", e);
            try {
                JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("error", e.getMessage())
                    .put("errorType", e.getClass().getSimpleName());
                String errorStr = objectMapper.writeValueAsString(errorResponse);
                output.write(errorStr.getBytes(StandardCharsets.UTF_8));
                output.flush();
            } catch (Exception ex) {
                logger.error("Failed to write error response", ex);
            }
        }
    }
}
