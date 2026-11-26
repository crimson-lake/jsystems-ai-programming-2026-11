package com.hsdc.copilot.cases;

/** Unknown or expired case (404 CASE_NOT_FOUND). The message is for logs only and is never sent to the client. */
public class CaseNotFoundException extends RuntimeException {

    public CaseNotFoundException(String message) {
        super(message);
    }
}
