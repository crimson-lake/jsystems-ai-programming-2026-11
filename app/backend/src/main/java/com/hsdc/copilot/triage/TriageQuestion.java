package com.hsdc.copilot.triage;

import tools.jackson.databind.JsonNode;

/**
 * One triage question of a versioned question set (ADR-004 section 3.3).
 *
 * @param criteria       noul: {@code {"true": …, "false": …}}; choice: option → description; score: array from lowest
 *                       to highest
 * @param requiresReason asked only when the form reason is non-empty
 */
public record TriageQuestion(String key, TriageAnswer.Type type, String instructions, JsonNode criteria,
        boolean requiresReason) {
}
