package com.hsdc.copilot.triage;

import java.util.Map;

/** Result of the triage stage for the first photo (ADR-004 section 4). */
public record TriageResult(
        String model,
        Map<String, TriageAnswer> answers,
        long latencyMs,
        Double costCredits,
        Status status) {

    public enum Status {
        OK, UNAVAILABLE, TIMEOUT, DISABLED
    }
}
