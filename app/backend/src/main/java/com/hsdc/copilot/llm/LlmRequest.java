package com.hsdc.copilot.llm;

import java.time.Duration;
import java.util.List;

/**
 * Provider-neutral model request (ADR-001 section 4).
 *
 * @param schema optional Java class that defines the strict JSON schema of a structured call
 */
public record LlmRequest(
        String model,
        String instructions,
        List<InputItem> inputItems,
        ReasoningEffort reasoningEffort,
        Class<?> schema,
        Duration timeout) {

    /** One input item; {@code imageDataUrl} is null for text-only items. */
    public record InputItem(Role role, String text, String imageDataUrl) {

        public enum Role {
            USER,
            ASSISTANT
        }
    }
}
