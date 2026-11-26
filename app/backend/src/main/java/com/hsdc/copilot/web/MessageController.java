package com.hsdc.copilot.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.hsdc.copilot.cases.CaseNotFoundException;
import com.hsdc.copilot.cases.CaseService;
import com.hsdc.copilot.cases.CaseService.Replay;
import com.hsdc.copilot.cases.CaseService.Reservation;
import com.hsdc.copilot.cases.CaseService.TurnStart;
import com.hsdc.copilot.cases.PhotoValidator;
import com.hsdc.copilot.cases.TurnInput;
import com.hsdc.copilot.cases.validation.ChatTextValidator;
import com.hsdc.copilot.llm.CancelToken;
import com.hsdc.copilot.web.ApiError.FieldError;
import com.hsdc.copilot.web.sse.StreamErrorEvent;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@code POST /api/cases/{caseId}/messages} (ADR-002 sections 3.4 and 5). Pre-stream check order: 413 (servlet
 * multipart limit) → 404 → committed {@code clientMessageId} replay → 409 → 400 → 422. Rejections are JSON; once
 * the checks pass the turn runs on the turn executor and the SSE emitter is returned at once.
 */
@RestController
@RequestMapping("/api/cases/{caseId}/messages")
public class MessageController {

    private final CaseService cases;
    private final ExecutorService turnExecutor;
    private final JsonMapper json;
    private final CaseController.ValidationTexts texts = new CaseController.ValidationTexts();

    public MessageController(CaseService cases, ExecutorService turnExecutor, JsonMapper json) {
        this.cases = cases;
        this.turnExecutor = turnExecutor;
        this.json = json;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SseEmitter> send(@PathVariable String caseId,
            @RequestPart(value = "clientMessageId", required = false) byte[] clientMessageIdPart,
            @RequestPart(value = "text", required = false) byte[] textPart,
            @RequestPart(value = "photo", required = false) MultipartFile photoPart) {
        UUID id = parseUuid(caseId);
        if (id == null) {
            throw new CaseNotFoundException("malformed case ID");
        }
        UUID clientMessageId = parseUuid(textOf(clientMessageIdPart));
        String text = textOf(textPart);
        byte[] photo = readPhoto(photoPart);

        SseEmitter emitter = SseEventWriter.newEmitter();
        SseEventWriter writer = new SseEventWriter(emitter, json, new CancelToken());
        TurnStart start = cases.beginTurn(id, clientMessageId, writer.cancelToken());
        if (start instanceof Replay replay) {
            execute(() -> cases.replay(replay, writer), writer);
            return stream(emitter);
        }
        Reservation reservation = (Reservation) start;
        try {
            validate(clientMessageId, text, photo);
            cases.checkPhotoLimit(reservation, photo != null);
        }
        catch (RuntimeException e) {
            cases.release(reservation);
            throw e;
        }
        TurnInput input = new TurnInput(text, photo, clientMessageId);
        if (!execute(() -> cases.runTurn(reservation, input, writer), writer)) {
            cases.release(reservation);
        }
        return stream(emitter);
    }

    private void validate(UUID clientMessageId, String text, byte[] photo) {
        List<FieldError> errors = new ArrayList<>();
        ChatTextValidator.validate(text, photo != null)
            .ifPresent(key -> errors.add(new FieldError("text", texts.get(key))));
        if (photo != null) {
            PhotoValidator.validate(photo).ifPresent(key -> errors.add(new FieldError("photo", texts.get(key))));
        }
        // A missing or malformed clientMessageId is a client bug, not a user error: 400 VALIDATION without a
        // field text (the backend key set of ADR-002 section 3.6 has no key for it).
        if (!errors.isEmpty() || clientMessageId == null) {
            throw new FieldValidationException(errors);
        }
    }

    private boolean execute(Runnable task, SseEventWriter writer) {
        try {
            turnExecutor.execute(task);
            return true;
        }
        catch (RejectedExecutionException e) {
            writer.error(StreamErrorEvent.Code.AI_UNAVAILABLE);
            return false;
        }
    }

    private static ResponseEntity<SseEmitter> stream(SseEmitter emitter) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache())
            .header("X-Accel-Buffering", "no")
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(emitter);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.strip());
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String textOf(byte[] part) {
        return part == null ? null : new String(part, StandardCharsets.UTF_8);
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
}
