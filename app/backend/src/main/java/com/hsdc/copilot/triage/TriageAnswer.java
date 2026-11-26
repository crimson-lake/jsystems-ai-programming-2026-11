package com.hsdc.copilot.triage;

import java.util.Map;

/**
 * One answer of the decisions model (ADR-004 section 4).
 *
 * @param value         boolean for noul, option key for choice, level label for score
 * @param probabilities per-option probabilities, null when not returned
 */
public record TriageAnswer(
        String key,
        Type type,
        Object value,
        Map<String, Double> probabilities,
        double confidence,
        Band band,
        boolean refused) {

    public enum Type {
        NOUL, CHOICE, SCORE
    }

    /** HIGH at or above the high threshold, LOW below the low threshold, otherwise MEDIUM. */
    public enum Band {
        HIGH, MEDIUM, LOW
    }
}
