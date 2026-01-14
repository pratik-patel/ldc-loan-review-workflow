package com.ldc.workflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import software.amazon.awssdk.services.sfn.model.StartExecutionResponse;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessRequest;
import software.amazon.awssdk.services.sfn.model.SendTaskFailureRequest;

/**
 * Service for interacting with AWS Step Functions API.
 * Uses AWS SDK v2 for proper authentication and authorization.
 */
@Service
public class StepFunctionsService {

    private static final Logger logger = LoggerFactory.getLogger(StepFunctionsService.class);
    private final SfnClient sfnClient;

    public StepFunctionsService() {
        // AWS SDK v2 automatically handles credentials from Lambda environment
        this.sfnClient = SfnClient.builder().build();
    }

    /**
     * Start a new Step Function execution.
     */
    public String startExecution(String stateMachineArn, String executionName, String input) {
        try {
            StartExecutionRequest request = StartExecutionRequest.builder()
                    .stateMachineArn(stateMachineArn)
                    .name(executionName)
                    .input(input)
                    .build();

            StartExecutionResponse response = sfnClient.startExecution(request);

            logger.info("Step Function execution started: {}", response.executionArn());
            return response.executionArn();
        } catch (Exception e) {
            logger.error("Error starting Step Function execution", e);
            throw new RuntimeException("Failed to start Step Function execution", e);
        }
    }

    /**
     * Send task success to Step Functions to resume execution.
     */
    public void sendTaskSuccess(String taskToken, String output) {
        try {
            SendTaskSuccessRequest request = SendTaskSuccessRequest.builder()
                    .taskToken(taskToken)
                    .output(output)
                    .build();

            sfnClient.sendTaskSuccess(request);
            logger.info("Task success sent to Step Functions");
        } catch (Exception e) {
            logger.error("Error sending task success to Step Functions", e);
            throw new RuntimeException("Failed to send task success", e);
        }
    }

    /**
     * Send task failure to Step Functions.
     */
    public void sendTaskFailure(String taskToken, String error, String cause) {
        try {
            SendTaskFailureRequest request = SendTaskFailureRequest.builder()
                    .taskToken(taskToken)
                    .error(error)
                    .cause(cause)
                    .build();

            sfnClient.sendTaskFailure(request);
            logger.info("Task failure sent to Step Functions");
        } catch (Exception e) {
            logger.error("Error sending task failure to Step Functions", e);
            throw new RuntimeException("Failed to send task failure to Step Functions", e);
        }
    }
}
