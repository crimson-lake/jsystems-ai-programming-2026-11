package com.hsdc.copilot.web.sse;

/** SSE {@code delta}: markdown chunk of the assistant message. */
public record DeltaEvent(String text) {
}
