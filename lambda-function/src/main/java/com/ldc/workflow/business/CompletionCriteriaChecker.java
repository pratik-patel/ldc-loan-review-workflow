package com.ldc.workflow.business;

import com.ldc.workflow.types.LoanAttribute;
import com.ldc.workflow.constants.WorkflowConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Checks if loan decision is complete based on completion criteria.
 * Loan decision is complete when all attributes are non-null and non-Pending,
 * and the loan decision itself is non-null.
 */
@Component
public class CompletionCriteriaChecker {

    private static final Logger logger = LoggerFactory.getLogger(CompletionCriteriaChecker.class);

    /**
     * Check if loan decision is complete.
     * 
     * Criteria:
     * - All attribute decisions are non-null and non-Pending
     * 
     * Note: LoanDecision itself is calculated AFTER this check by
     * DetermineLoanStatus
     */
    public boolean isLoanDecisionComplete(String loanDecision, List<LoanAttribute> attributes) {
        // Check if all attributes are non-null and non-Pending
        if (attributes == null || attributes.isEmpty()) {
            logger.debug("No attributes provided - considered complete (edge case)");
            return true; // Empty attributes = immediate decision
        }

        boolean allAttributesComplete = attributes.stream()
                .allMatch(attr -> {
                    String decision = attr.getAttributeDecision();
                    return decision != null && !WorkflowConstants.STATUS_PENDING.equals(decision);
                });

        if (!allAttributesComplete) {
            logger.debug("Not all attributes are complete (some are null or Pending)");
            return false;
        }

        logger.info("All attributes complete - ready for loan decision determination");
        return true;
    }

    /**
     * Get the list of incomplete attributes (null or Pending).
     */
    public List<LoanAttribute> getIncompleteAttributes(List<LoanAttribute> attributes) {
        if (attributes == null) {
            return List.of();
        }

        return attributes.stream()
                .filter(attr -> {
                    String decision = attr.getAttributeDecision();
                    return decision == null || WorkflowConstants.STATUS_PENDING.equals(decision);
                })
                .toList();
    }

    /**
     * Get a human-readable message about why the loan decision is not complete.
     */
    public String getIncompleteReason(String loanDecision, List<LoanAttribute> attributes) {
        if (loanDecision == null || loanDecision.trim().isEmpty()) {
            return "Loan decision is not set";
        }

        List<LoanAttribute> incompleteAttrs = getIncompleteAttributes(attributes);
        if (!incompleteAttrs.isEmpty()) {
            String incompleteNames = incompleteAttrs.stream()
                    .map(LoanAttribute::getAttributeName)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(WorkflowConstants.DEFAULT_UNKNOWN);
            return "The following attributes are incomplete (Pending or null): " + incompleteNames;
        }

        return "Loan decision is complete";
    }
}
