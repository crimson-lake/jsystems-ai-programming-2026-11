package com.hsdc.copilot.web.sse;

/** SSE {@code progress}: drives the loading steps. */
public record ProgressEvent(Step step) {

    public enum Step {
        UPLOAD_RECEIVED, ANALYZING_PHOTO, PREPARING_DECISION, WRITING_REPLY
    }
}
