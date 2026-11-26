package com.hsdc.copilot.web;

import com.hsdc.copilot.cases.CaseBusyException;
import com.hsdc.copilot.cases.CaseNotFoundException;
import com.hsdc.copilot.cases.PhotoLimitException;
import com.hsdc.copilot.web.ApiError.Code;
import com.hsdc.copilot.web.ApiError.FieldError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Maps exceptions to {@link ApiError} bodies (ADR-002 section 3.7). Bodies contain codes and Polish texts only,
 * never exception messages, class names or stack traces (AC-42). The body is always JSON, also when the client
 * asked for {@code text/event-stream} first.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    static final String PAYLOAD_TOO_LARGE_MESSAGE = "Zdjęcie przekracza limit 10 MB";
    static final String INTERNAL_MESSAGE = "Wystąpił błąd. Spróbuj ponownie.";

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BindException.class) // also MethodArgumentNotValidException
    ResponseEntity<ApiError> bindFailure(BindException e) {
        List<FieldError> fields = new ArrayList<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> fields.add(new FieldError(f.getField(), f.getDefaultMessage())));
        e.getBindingResult().getGlobalErrors()
                .forEach(g -> fields.add(new FieldError(g.getObjectName(), g.getDefaultMessage())));
        return validation(fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraintViolation(ConstraintViolationException e) {
        List<FieldError> fields = new ArrayList<>();
        for (ConstraintViolation<?> v : e.getConstraintViolations()) {
            fields.add(new FieldError(lastNode(v.getPropertyPath().toString()), v.getMessage()));
        }
        return validation(fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> methodValidation(HandlerMethodValidationException e) {
        List<FieldError> fields = new ArrayList<>();
        e.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> {
                String message = error.getDefaultMessage();
                fields.add(new FieldError(name == null ? "" : name, message));
            });
        });
        return validation(fields);
    }

    @ExceptionHandler(FieldValidationException.class)
    ResponseEntity<ApiError> fieldValidation(FieldValidationException e) {
        return validation(e.getFieldErrors());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableBody() {
        return validation(List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> payloadTooLarge() {
        return json(HttpStatus.PAYLOAD_TOO_LARGE, new ApiError(Code.PAYLOAD_TOO_LARGE, PAYLOAD_TOO_LARGE_MESSAGE, null));
    }

    @ExceptionHandler(CaseNotFoundException.class)
    ResponseEntity<ApiError> caseNotFound() {
        return json(HttpStatus.NOT_FOUND, new ApiError(Code.CASE_NOT_FOUND, null, null));
    }

    @ExceptionHandler(CaseBusyException.class)
    ResponseEntity<ApiError> caseBusy() {
        return json(HttpStatus.CONFLICT, new ApiError(Code.CASE_BUSY, null, null));
    }

    @ExceptionHandler(PhotoLimitException.class)
    ResponseEntity<ApiError> photoLimit() {
        return json(HttpStatus.UNPROCESSABLE_ENTITY, new ApiError(Code.PHOTO_LIMIT, null, null));
    }

    /**
     * Everything else is 500 INTERNAL. Spring's own HTTP errors (wrong method, unknown static resource, missing
     * part, ...) keep their status so that they are not reported as server faults, with the same generic body.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            if (status.is4xxClientError()) {
                log.warn("Request rejected by the framework with HTTP {}: {}", status.value(), e.getClass().getSimpleName());
                return status.value() == HttpStatus.BAD_REQUEST.value()
                        ? validation(List.of())
                        : json(status, new ApiError(Code.INTERNAL, INTERNAL_MESSAGE, null));
            }
        }
        log.error("Unhandled exception", e);
        return json(HttpStatus.INTERNAL_SERVER_ERROR, new ApiError(Code.INTERNAL, INTERNAL_MESSAGE, null));
    }

    private static ResponseEntity<ApiError> validation(List<FieldError> fields) {
        return json(HttpStatus.BAD_REQUEST, new ApiError(Code.VALIDATION, null, List.copyOf(fields)));
    }

    private static ResponseEntity<ApiError> json(HttpStatusCode status, ApiError body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
