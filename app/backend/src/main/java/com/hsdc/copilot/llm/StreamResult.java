package com.hsdc.copilot.llm;

/** Result of a streamed model call (ADR-001 section 4). */
public record StreamResult(String fullText, long inputTokens, long outputTokens, FinishStatus finishStatus) {

    public enum FinishStatus {
        COMPLETED,
        FAILED,
        INCOMPLETE,
        CANCELLED
    }
}
