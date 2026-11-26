package com.hsdc.copilot.llm;

/** Domain exception of the model gateway (Timeout). */
public class LlmTimeoutException extends RuntimeException {

    public LlmTimeoutException(String message) {
        super(message);
    }

    public LlmTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
