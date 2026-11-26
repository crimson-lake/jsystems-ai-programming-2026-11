package com.hsdc.copilot.cases;

/** A turn of the case is already running (409 CASE_BUSY). The message is for logs only and is never sent to the client. */
public class CaseBusyException extends RuntimeException {

    public CaseBusyException(String message) {
        super(message);
    }
}
