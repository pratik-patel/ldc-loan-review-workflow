package com.ldc.workflow.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single attribute decision within a loan review.
 * Each attribute can have a decision status: Approved, Rejected, Reclass,
 * Repurchase, or Pending.
 */
public class LoanAttribute {

    @JsonProperty("Name")
    private String attributeName;

    @JsonProperty("Decision")
    private String attributeDecision; // Approved, Rejected, Reclass, Repurchase, Pending

    // Constructors
    public LoanAttribute() {
    }

    public LoanAttribute(String attributeName, String attributeDecision) {
        this.attributeName = attributeName;
        this.attributeDecision = attributeDecision;
    }

    // Getters and Setters
    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public String getAttributeDecision() {
        return attributeDecision;
    }

    public void setAttributeDecision(String attributeDecision) {
        this.attributeDecision = attributeDecision;
    }

    @Override
    public String toString() {
        return "LoanAttribute{" +
                "attributeName='" + attributeName + '\'' +
                ", attributeDecision='" + attributeDecision + '\'' +
                '}';
    }
}
