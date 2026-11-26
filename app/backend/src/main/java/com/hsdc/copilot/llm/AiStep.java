package com.hsdc.copilot.llm;

/** The external AI call steps named in the {@code ai.call} evidence log line (ADR-001 section 3.6). */
public enum AiStep {
    TRIAGE,
    VISION,
    DECISION,
    DECISION_REASK,
    STREAM
}
