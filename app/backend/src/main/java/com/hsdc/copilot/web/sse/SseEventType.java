package com.hsdc.copilot.web.sse;

/** Names of the SSE event types shared by both streaming endpoints (ADR-000 section 6). */
public final class SseEventType {

    public static final String CASE = "case";
    public static final String PROGRESS = "progress";
    public static final String DECISION = "decision";
    public static final String DELTA = "delta";
    public static final String DONE = "done";
    public static final String ERROR = "error";

    private SseEventType() {
    }
}
