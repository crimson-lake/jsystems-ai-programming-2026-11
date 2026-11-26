package com.hsdc.copilot.web;

import java.util.List;

/** A request failed validation in code; mapped to 400 VALIDATION with these field errors (ADR-002 section 3.7). */
public class FieldValidationException extends RuntimeException {

    private final transient List<ApiError.FieldError> fieldErrors;

    public FieldValidationException(List<ApiError.FieldError> fieldErrors) {
        super("Validation failed for " + fieldErrors.size() + " field(s)");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<ApiError.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
