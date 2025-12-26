package com.ldc.workflow.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldc.workflow.service.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

import java.util.function.Function;

/**
 * Lambda handler for email notifications.
 * Sends email notifications for Repurchase and Reclass Expiration events.
 * Errors are logged but do not fail the workflow.
 * 
 * Input: JSON with notificationType, loanNumber, requestNumber, recipientEmail, templateName
 * Output: JSON with send status
 */
@Component("emailNotificationHandler")
public class EmailNotificationHandler implements Function<JsonNode, JsonNode> {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SENDER_EMAIL = System.getenv("SENDER_EMAIL");

    private final SesClient sesClient;
    private final ConfigurationService configurationService;

    public EmailNotificationHandler(SesClient sesClient, ConfigurationService configurationService) {
        this.sesClient = sesClient;
        this.configurationService = configurationService;
    }

    @Override
    public JsonNode apply(JsonNode input) {
        try {
            logger.info("Email Notification handler invoked");

            // Extract input fields
            String notificationType = input.get("notificationType").asText();
            String loanNumber = input.get("loanNumber").asText();
            String requestNumber = input.get("requestNumber").asText();
            String templateName = input.get("templateName").asText();

            logger.debug("Sending {} notification for loanNumber: {}", notificationType, loanNumber);

            // Get recipient email from configuration
            String recipientEmail = configurationService.getNotificationEmail(notificationType.toLowerCase());
            if (recipientEmail == null || recipientEmail.isEmpty()) {
                logger.error("Recipient email not configured for notification type: {}", notificationType);
                // Log error but don't fail workflow
                return createSuccessResponse(requestNumber, loanNumber, false, 
                        "Recipient email not configured");
            }

            // Get email template from configuration
            String emailTemplate = configurationService.getEmailTemplate(templateName);
            if (emailTemplate == null || emailTemplate.isEmpty()) {
                logger.error("Email template not found: {}", templateName);
                // Log error but don't fail workflow
                return createSuccessResponse(requestNumber, loanNumber, false, 
                        "Email template not found");
            }

            // Replace placeholders in template
            String emailBody = emailTemplate
                    .replace("${loanNumber}", loanNumber)
                    .replace("${requestNumber}", requestNumber)
                    .replace("${notificationType}", notificationType);

            // Send email via SES
            String messageId = sendEmail(recipientEmail, emailBody, notificationType);

            logger.info("Email notification sent successfully for loanNumber: {}, messageId: {}", 
                    loanNumber, messageId);

            return createSuccessResponse(requestNumber, loanNumber, true, messageId);
        } catch (Exception e) {
            logger.error("Error sending email notification", e);
            // Log error but don't fail workflow (non-blocking error)
            return createSuccessResponse("unknown", "unknown", false, 
                    "Error: " + e.getMessage());
        }
    }

    private String sendEmail(String recipientEmail, String emailBody, String notificationType) {
        try {
            if (SENDER_EMAIL == null || SENDER_EMAIL.isEmpty()) {
                throw new RuntimeException("SENDER_EMAIL environment variable is not set");
            }

            SendEmailRequest request = SendEmailRequest.builder()
                    .source(SENDER_EMAIL)
                    .destination(Destination.builder()
                            .toAddresses(recipientEmail)
                            .build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .data("LDC Loan Review - " + notificationType + " Notification")
                                    .charset("UTF-8")
                                    .build())
                            .body(Body.builder()
                                    .html(Content.builder()
                                            .data(emailBody)
                                            .charset("UTF-8")
                                            .build())
                                    .build())
                            .build())
                    .build();

            SendEmailResponse response = sesClient.sendEmail(request);
            logger.debug("Email sent successfully, messageId: {}", response.messageId());
            return response.messageId();
        } catch (Exception e) {
            logger.error("Error sending email via SES", e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    private JsonNode createSuccessResponse(String requestNumber, String loanNumber, 
                                          boolean sent, String messageIdOrError) {
        return objectMapper.createObjectNode()
                .put("success", true)
                .put("requestNumber", requestNumber)
                .put("loanNumber", loanNumber)
                .put("sent", sent)
                .put("messageId", messageIdOrError);
    }
}
