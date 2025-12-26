package com.ldc.workflow.service;

import com.ldc.workflow.types.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

/**
 * Service for sending email notifications via AWS SES.
 * Handles email sending for repurchase and reclass expiration notifications.
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final SesClient sesClient;
    private final ConfigurationService configurationService;
    private final String senderEmail;

    public EmailService(SesClient sesClient, ConfigurationService configurationService) {
        this.sesClient = sesClient;
        this.configurationService = configurationService;
        this.senderEmail = System.getenv("SES_SENDER_EMAIL");
        if (this.senderEmail == null || this.senderEmail.isEmpty()) {
            logger.warn("SES_SENDER_EMAIL environment variable not set");
        }
    }

    /**
     * Send a notification email.
     */
    public void sendNotificationEmail(String requestNumber, String loanNumber, String subject,
                                      String templateName, WorkflowState state) {
        try {
            logger.debug("Sending notification email for requestNumber: {}, templateName: {}", 
                    requestNumber, templateName);

            // Get recipient email from configuration
            String recipientEmail = configurationService.getParameter("/ldc-workflow/notification-email");
            if (recipientEmail == null || recipientEmail.isEmpty()) {
                logger.warn("Notification email not configured, skipping email send");
                return;
            }

            // Get email template from configuration
            String templateContent = configurationService.getParameter("/ldc-workflow/email-template/" + templateName);
            if (templateContent == null || templateContent.isEmpty()) {
                logger.warn("Email template not found: {}", templateName);
                templateContent = buildDefaultTemplate(templateName, state);
            }

            // Replace placeholders in template
            String emailBody = templateContent
                    .replace("{{requestNumber}}", requestNumber)
                    .replace("{{loanNumber}}", loanNumber)
                    .replace("{{reviewType}}", state.getReviewType() != null ? state.getReviewType() : "N/A")
                    .replace("{{loanDecision}}", state.getLoanDecision() != null ? state.getLoanDecision() : "N/A")
                    .replace("{{loanStatus}}", state.getLoanStatus() != null ? state.getLoanStatus() : "N/A");

            // Send email via SES
            SendEmailRequest request = SendEmailRequest.builder()
                    .source(senderEmail)
                    .destination(Destination.builder()
                            .toAddresses(recipientEmail)
                            .build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .data(subject)
                                    .charset("UTF-8")
                                    .build())
                            .body(Body.builder()
                                    .text(Content.builder()
                                            .data(emailBody)
                                            .charset("UTF-8")
                                            .build())
                                    .build())
                            .build())
                    .build();

            SendEmailResponse response = sesClient.sendEmail(request);
            logger.info("Email sent successfully for requestNumber: {}, messageId: {}", 
                    requestNumber, response.messageId());
        } catch (Exception e) {
            logger.error("Error sending notification email for requestNumber: {}", requestNumber, e);
            // Non-blocking error - don't throw
        }
    }

    /**
     * Build a default email template if not found in configuration.
     */
    private String buildDefaultTemplate(String templateName, WorkflowState state) {
        return switch (templateName) {
            case "repurchase" -> buildRepurchaseTemplate(state);
            case "reclass-expired" -> buildReclassExpiredTemplate(state);
            default -> "Loan Review Notification\n\n" +
                    "Request Number: {{requestNumber}}\n" +
                    "Loan Number: {{loanNumber}}\n" +
                    "Review Type: {{reviewType}}\n" +
                    "Loan Decision: {{loanDecision}}\n" +
                    "Loan Status: {{loanStatus}}";
        };
    }

    private String buildRepurchaseTemplate(WorkflowState state) {
        return "Repurchase Notification\n\n" +
                "Request Number: {{requestNumber}}\n" +
                "Loan Number: {{loanNumber}}\n" +
                "Review Type: {{reviewType}}\n" +
                "Decision: Repurchase\n\n" +
                "The loan has been marked for repurchase. Please take appropriate action.";
    }

    private String buildReclassExpiredTemplate(WorkflowState state) {
        return "Reclass Confirmation Expired\n\n" +
                "Request Number: {{requestNumber}}\n" +
                "Loan Number: {{loanNumber}}\n" +
                "Review Type: {{reviewType}}\n\n" +
                "The 2-day reclass confirmation period has expired. No confirmation was received. " +
                "Please review and take appropriate action.";
    }
}
