package com.hsdc.copilot.triage;

/**
 * The Decisions API call failed: any non-2xx status, a timeout, a transport error or a malformed body (ADR-004
 * section 5). Never surfaced to the customer; the router falls back to the LLM path (T0). The message is for logs
 * only and never contains a response body.
 */
public class TriageUnavailableException extends RuntimeException {

    private final boolean timeout;

    public TriageUnavailableException(String message, boolean timeout) {
        super(message);
        this.timeout = timeout;
    }

    /** True when the 8 s triage timeout expired. */
    public boolean isTimeout() {
        return timeout;
    }
}
