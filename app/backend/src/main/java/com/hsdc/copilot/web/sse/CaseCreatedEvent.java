package com.hsdc.copilot.web.sse;

import java.util.UUID;

/** SSE {@code case}: the case was created. */
public record CaseCreatedEvent(UUID caseId) {
}
