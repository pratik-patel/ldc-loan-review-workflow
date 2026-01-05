package com.ldc.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Application configuration properties loaded from environment variables and application.properties.
 * 
 * These properties are injected into handlers and services for accessing configuration values.
 */
@Configuration
@ConfigurationProperties(prefix = "")
public class ApplicationConfig {

    private String awsRegion;
    private Sqs sqs = new Sqs();
    private Ses ses = new Ses();
    private ParameterStore parameterStore = new ParameterStore();

    public static class Sqs {
        private String queueUrl;

        public String getQueueUrl() {
            return queueUrl;
        }

        public void setQueueUrl(String queueUrl) {
            this.queueUrl = queueUrl;
        }
    }

    public static class Ses {
        private String senderEmail;

        public String getSenderEmail() {
            return senderEmail;
        }

        public void setSenderEmail(String senderEmail) {
            this.senderEmail = senderEmail;
        }
    }

    public static class ParameterStore {
        private String prefix;

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }
    }

    // Getters and Setters
    public String getAwsRegion() {
        return awsRegion;
    }

    public void setAwsRegion(String awsRegion) {
        this.awsRegion = awsRegion;
    }

    public Sqs getSqs() {
        return sqs;
    }

    public void setSqs(Sqs sqs) {
        this.sqs = sqs;
    }

    public Ses getSes() {
        return ses;
    }

    public void setSes(Ses ses) {
        this.ses = ses;
    }

    public ParameterStore getParameterStore() {
        return parameterStore;
    }

    public void setParameterStore(ParameterStore parameterStore) {
        this.parameterStore = parameterStore;
    }
}
