package com.hsdc.copilot.web.sse;

import java.util.UUID;

/** SSE {@code done}: the message is complete and committed. */
public record DoneEvent(UUID messageId) {
}
