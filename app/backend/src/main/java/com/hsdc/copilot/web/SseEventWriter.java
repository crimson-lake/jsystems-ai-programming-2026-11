package com.hsdc.copilot.web;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.hsdc.copilot.llm.CancelToken;
import com.hsdc.copilot.web.sse.SseEventType;
import com.hsdc.copilot.web.sse.StreamErrorEvent;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the events of one streaming response (ADR-002 section 3.5). Payloads are serialized with the Jackson 3
 * {@link JsonMapper}; a {@code : ping} comment is sent every 10 s. A failed write, an emitter error or timeout
 * cancels the turn through its {@link CancelToken}. Thread-safe: the turn thread and the heartbeat share it.
 */
public final class SseEventWriter {

    private static final Logger log = LoggerFactory.getLogger(SseEventWriter.class);

    public static final long EMITTER_TIMEOUT_MS = 120_000L;
    public static final Duration HEARTBEAT = Duration.ofSeconds(10);
    public static final String AI_ERROR_MESSAGE = "Nie udało się przeanalizować zgłoszenia. Spróbuj ponownie.";

    private static final ScheduledExecutorService HEARTBEATS = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("sse-heartbeat").factory());

    private final SseEmitter emitter;
    private final JsonMapper json;
    private final CancelToken cancelToken;
    private ScheduledFuture<?> heartbeat;
    private boolean closed;

    public SseEventWriter(SseEmitter emitter, JsonMapper json, CancelToken cancelToken) {
        this.emitter = emitter;
        this.json = json;
        this.cancelToken = cancelToken;
        emitter.onTimeout(() -> clientGone("timeout"));
        emitter.onError(e -> clientGone("error"));
    }

    public static SseEmitter newEmitter() {
        return new SseEmitter(EMITTER_TIMEOUT_MS);
    }

    public CancelToken cancelToken() {
        return cancelToken;
    }

    public synchronized void startHeartbeat() {
        if (heartbeat == null && !closed) {
            long ms = HEARTBEAT.toMillis();
            heartbeat = HEARTBEATS.scheduleAtFixedRate(this::ping, ms, ms, TimeUnit.MILLISECONDS);
        }
    }

    /** Sends one named event; returns false (and cancels the turn) when the client is gone. */
    public synchronized boolean send(String name, Object payload) {
        if (closed || cancelToken.isCancelled()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(json.writeValueAsString(payload)));
            return true;
        }
        catch (IOException | IllegalStateException e) {
            clientGone("send failed");
            return false;
        }
    }

    /** Sends {@code error} (if the client is still there) and completes the emitter normally. */
    public synchronized void error(StreamErrorEvent.Code code) {
        send(SseEventType.ERROR, new StreamErrorEvent(code, AI_ERROR_MESSAGE));
        complete();
    }

    /** Stops the heartbeat and completes the emitter (never {@code completeWithError}). Idempotent. */
    public synchronized void complete() {
        if (closed) {
            return;
        }
        closed = true;
        stopHeartbeat();
        try {
            emitter.complete();
        }
        catch (RuntimeException e) {
            log.debug("Emitter already completed: {}", e.getClass().getSimpleName());
        }
    }

    synchronized void ping() {
        if (closed) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().comment(" ping"));
        }
        catch (IOException | IllegalStateException e) {
            clientGone("heartbeat failed");
        }
    }

    private void clientGone(String why) {
        if (!cancelToken.isCancelled()) {
            log.info("SSE client gone ({}); cancelling the turn", why);
        }
        stopHeartbeat();
        cancelToken.cancel();
    }

    private void stopHeartbeat() {
        ScheduledFuture<?> h = heartbeat;
        if (h != null) {
            h.cancel(false);
        }
    }
}
