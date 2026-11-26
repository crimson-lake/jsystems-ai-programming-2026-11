package com.hsdc.copilot.triage;

import java.util.Map;

/**
 * One answer of the decisions model (ADR-004 section 4), mapped from the TypeSafe Jev 1.14 wire format recorded
 * in spike S-2 ({@code openrouter-captures/decisions-jev-*.json}).
 *
 * <ul>
 * <li>noul: the wire value is P(true) with no {@code confidence}; {@code probabilityTrue} keeps it, {@code value} is
 * {@code probabilityTrue >= 0.5}, and {@code confidence = max(p, 1 - p)} with {@code confidenceDerived = true}.</li>
 * <li>choice: {@code value} is the chosen option key; {@code probabilities} and {@code confidence} as returned.</li>
 * <li>score: {@code value} is the legend label with the highest probability, {@code score} the probability-weighted
 * position (0..N-1), {@code probabilities} keyed by legend label.</li>
 * </ul>
 *
 * @param value             Boolean for noul, option key for choice, legend label for score; null when refused
 * @param probabilityTrue   noul only: P(true) as returned; null otherwise
 * @param score             score only: probability-weighted position as returned; null otherwise
 * @param probabilities     per-option probabilities (noul: {@code true}/{@code false}); null when not returned
 * @param confidence        0-1; returned by the model for choice and score, derived for noul
 * @param confidenceDerived true when {@code confidence} was computed from {@code probabilityTrue}
 */
public record TriageAnswer(
        String key,
        Type type,
        Object value,
        Double probabilityTrue,
        Double score,
        Map<String, Double> probabilities,
        double confidence,
        boolean confidenceDerived,
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
