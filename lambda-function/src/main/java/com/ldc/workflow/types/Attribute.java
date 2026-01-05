package com.ldc.workflow.types;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents a loan attribute with its decision status.
 */
public class Attribute {

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Decision")
    private String decision;

    public Attribute() {
    }

    public Attribute(String name, String decision) {
        this.name = name;
        this.decision = decision;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Attribute attribute = (Attribute) o;
        return Objects.equals(name, attribute.name) &&
                Objects.equals(decision, attribute.decision);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, decision);
    }

    @Override
    public String toString() {
        return "Attribute{" +
                "name='" + name + '\'' +
                ", decision='" + decision + '\'' +
                '}';
    }
}
