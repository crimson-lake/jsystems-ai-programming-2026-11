package com.hsdc.copilot.cases;

import com.hsdc.copilot.decision.Decision;
import com.hsdc.copilot.decision.DecisionResult;
import java.time.Instant;
import java.util.UUID;

/** One message of a case conversation (ADR-002 section 4). */
public record ChatMessage(
        UUID id,
        Role role,
        String text,
        UUID clientMessageId,
        boolean hasPhoto,
        Integer analysisIndex,
        Decision decisionSnapshot,
        boolean changed,
        DecisionResult.Label previousLabel,
        Instant createdAt) {

    public enum Role {
        USER,
        ASSISTANT
    }
}
