package com.hsdc.copilot.llm;

/** Domain exception of the model gateway (Cancelled). */
public class LlmCancelledException extends RuntimeException {

    public LlmCancelledException(String message) {
        super(message);
    }

    public LlmCancelledException(String message, Throwable cause) {
        super(message, cause);
    }
}
