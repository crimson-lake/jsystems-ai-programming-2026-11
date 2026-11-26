package com.hsdc.copilot.llm;

/** Reasoning effort sent to the model (ADR-001 section 4). */
public enum ReasoningEffort {
    NONE("none"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    private final String wireValue;

    ReasoningEffort(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
