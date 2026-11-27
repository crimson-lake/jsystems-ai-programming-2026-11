package com.hsdc.copilot.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Error body of every non-streaming error response (ADR-002 sections 3.7 and 4). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Code code, String message, List<FieldError> fieldErrors) {

    public enum Code {
        VALIDATION, CASE_NOT_FOUND, CASE_BUSY, PHOTO_LIMIT, PAYLOAD_TOO_LARGE, INTERNAL
    }

    public record FieldError(String field, String message) {
    }
}
