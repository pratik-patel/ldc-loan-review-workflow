package com.ldc.workflow.validation;

import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

/**
 * Validator for LoanNumber field.
 * Valid pattern: ^[0-9]{10}$ (exactly 10 digits)
 */
@Component
public class LoanNumberValidator {

    private static final Pattern LOAN_NUMBER_PATTERN = Pattern.compile("^[0-9]{10}$");

    /**
     * Validate LoanNumber against required pattern.
     */
    public boolean isValid(String loanNumber) {
        return loanNumber != null && LOAN_NUMBER_PATTERN.matcher(loanNumber).matches();
    }

    /**
     * Get the required pattern as string.
     */
    public String getPattern() {
        return "^[0-9]{10}$";
    }
}
