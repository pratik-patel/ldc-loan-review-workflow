package com.ldc.workflow.validation;

import com.ldc.workflow.constants.WorkflowConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Validator for review type values.
 * Ensures review type is one of the allowed values.
 */
@Component
public class ReviewTypeValidator {

    private static final Logger logger = LoggerFactory.getLogger(ReviewTypeValidator.class);
    private static final Set<String> ALLOWED_REVIEW_TYPES = new HashSet<>(Arrays.asList(
            WorkflowConstants.REVIEW_TYPE_LDC,
            WorkflowConstants.REVIEW_TYPE_SEC_POLICY,
            WorkflowConstants.REVIEW_TYPE_CONDUIT));

    /**
     * Validate that the review type is one of the allowed values.
     */
    public boolean isValid(String reviewType) {
        if (reviewType == null || reviewType.trim().isEmpty()) {
            logger.warn("Review type is null or empty");
            return false;
        }

        boolean valid = ALLOWED_REVIEW_TYPES.contains(reviewType);
        if (!valid) {
            logger.warn("Invalid review type: {}. Allowed values: {}", reviewType, ALLOWED_REVIEW_TYPES);
        }
        return valid;
    }

    /**
     * Get the set of allowed review types.
     */
    public Set<String> getAllowedReviewTypes() {
        return new HashSet<>(ALLOWED_REVIEW_TYPES);
    }

    /**
     * Get error message for invalid review type.
     */
    public String getErrorMessage(String reviewType) {
        return String.format("Invalid reviewType: '%s'. Must be one of: %s",
                reviewType, String.join(", ", ALLOWED_REVIEW_TYPES));
    }
}
