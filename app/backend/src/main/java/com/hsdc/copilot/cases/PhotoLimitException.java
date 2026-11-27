package com.hsdc.copilot.cases;

/** The additional photo limit of the case is reached (422 PHOTO_LIMIT). The message is for logs only and is never sent to the client. */
public class PhotoLimitException extends RuntimeException {

    public PhotoLimitException(String message) {
        super(message);
    }
}
