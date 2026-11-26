package com.hsdc.copilot.web.sse;

/** SSE {@code error}: Polish user-facing message; after it the stream closes, nothing is committed. */
public record StreamErrorEvent(Code code, String message) {

    public enum Code {
        AI_UNAVAILABLE, TIMEOUT
    }
}
