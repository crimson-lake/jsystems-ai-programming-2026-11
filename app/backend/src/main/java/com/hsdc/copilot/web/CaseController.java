package com.hsdc.copilot.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.hsdc.copilot.cases.CaseForm;
import com.hsdc.copilot.cases.CaseService;
import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.cases.Category;
import com.hsdc.copilot.cases.PhotoValidator;
import com.hsdc.copilot.llm.CancelToken;
import com.hsdc.copilot.web.ApiError.FieldError;
import com.hsdc.copilot.web.sse.StreamErrorEvent;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code POST /api/cases} and {@code DELETE /api/cases/{caseId}} (ADR-002 section 5). Validation errors are returned
 * as 400 JSON before any stream is opened; after that the turn runs on the turn executor and the SSE emitter is
 * returned at once.
 */
@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private static final Locale PL = Locale.forLanguageTag("pl-PL");

    private final CaseService cases;
    private final ExecutorService turnExecutor;
    private final Validator validator;
    private final JsonMapper json;
    private final ValidationTexts texts;

    public CaseController(CaseService cases, ExecutorService turnExecutor, Validator validator, JsonMapper json) {
        this.cases = cases;
        this.turnExecutor = turnExecutor;
        this.validator = validator;
        this.json = json;
        this.texts = new ValidationTexts();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SseEmitter> create(@RequestPart(value = "form", required = false) byte[] formPart,
            @RequestPart(value = "photo", required = false) MultipartFile photoPart) {
        List<FieldError> errors = new ArrayList<>();
        CaseForm form = parseForm(formPart, errors);
        for (ConstraintViolation<CaseForm> v : validator.validate(form)) {
            String field = v.getPropertyPath().toString();
            if (errors.stream().noneMatch(e -> e.field().equals(field))) {
                errors.add(new FieldError(field, v.getMessage()));
            }
        }
        byte[] photo = readPhoto(photoPart);
        PhotoValidator.validate(photo).ifPresent(key -> errors.add(new FieldError("photo", texts.get(key))));
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        SseEmitter emitter = SseEventWriter.newEmitter();
        SseEventWriter writer = new SseEventWriter(emitter, json, new CancelToken());
        try {
            turnExecutor.execute(() -> cases.create(form, photo, writer));
        }
        catch (RejectedExecutionException e) {
            writer.error(StreamErrorEvent.Code.AI_UNAVAILABLE);
        }
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache())
            .header("X-Accel-Buffering", "no")
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(emitter);
    }

    /** Always 204, also for unknown or malformed IDs (ADR-000 section 6: idempotent). */
    @DeleteMapping("/{caseId}")
    public ResponseEntity<Void> delete(@PathVariable String caseId) {
        try {
            cases.delete(UUID.fromString(caseId));
        }
        catch (IllegalArgumentException e) {
            // not a case ID: nothing to delete
        }
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ parsing

    /** Lenient manual parsing so that every field gets its own Polish message instead of one parse error. */
    private CaseForm parseForm(byte[] part, List<FieldError> errors) {
        JsonNode node = null;
        if (part != null && part.length > 0) {
            try {
                node = json.readTree(new String(part, StandardCharsets.UTF_8));
            }
            catch (JacksonException e) {
                node = null;
            }
        }
        if (node == null || !node.isObject()) {
            return new CaseForm(null, null, null, null, null);
        }
        CaseType type = enumValue(CaseType.class, node.get("caseType"));
        Category category = enumValue(Category.class, node.get("category"));
        String model = textValue(node.get("model"));
        String reason = textValue(node.get("reason"));
        LocalDate purchaseDate = null;
        String date = textValue(node.get("purchaseDate"));
        if (date != null && !date.isBlank()) {
            try {
                purchaseDate = LocalDate.parse(date);
            }
            catch (DateTimeParseException e) {
                errors.add(new FieldError("purchaseDate", texts.get("form.purchaseDate.invalid")));
            }
        }
        return new CaseForm(type, category, model, purchaseDate, reason);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode node) {
        String value = textValue(node);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String textValue(JsonNode node) {
        return node == null || !node.isString() ? null : node.asString();
    }

    private static byte[] readPhoto(MultipartFile part) {
        if (part == null || part.isEmpty()) {
            return null;
        }
        try {
            return part.getBytes();
        }
        catch (IOException e) {
            return null;
        }
    }

    /** The backend subset of the Polish texts (ValidationMessages_pl.properties) for checks done in code. */
    static final class ValidationTexts {

        private final ResourceBundle bundle = ResourceBundle.getBundle("ValidationMessages", PL);

        String get(String key) {
            return bundle.getString(key);
        }
    }
}
