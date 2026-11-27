package com.hsdc.copilot.cases;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.hsdc.copilot.analysis.ImageAnalysis;
import com.hsdc.copilot.analysis.ImageAnalysisService;
import com.hsdc.copilot.decision.CaseContextAssembler;
import com.hsdc.copilot.decision.Decision;
import com.hsdc.copilot.decision.DecisionService;
import com.hsdc.copilot.decision.DecisionService.MessageKind;
import com.hsdc.copilot.llm.AiCallContext;
import com.hsdc.copilot.llm.CancelToken;
import com.hsdc.copilot.llm.Deadline;
import com.hsdc.copilot.llm.LlmCancelledException;
import com.hsdc.copilot.llm.LlmTimeoutException;
import com.hsdc.copilot.llm.LlmUnavailableException;
import com.hsdc.copilot.web.SseEventWriter;
import com.hsdc.copilot.web.sse.CaseCreatedEvent;
import com.hsdc.copilot.web.sse.DecisionEvent;
import com.hsdc.copilot.web.sse.DeltaEvent;
import com.hsdc.copilot.web.sse.DoneEvent;
import com.hsdc.copilot.web.sse.ProgressEvent;
import com.hsdc.copilot.web.sse.ProgressEvent.Step;
import com.hsdc.copilot.web.sse.SseEventType;
import com.hsdc.copilot.web.sse.StreamErrorEvent;

/**
 * Turn orchestration (ADR-002 section 3.4). The first submission works on a new {@link CaseSession} that is saved
 * into the {@link CaseStore} only after the whole turn succeeded; any error leaves no case behind (AC-40).
 * Logs carry the case ID and step, never customer text or image data.
 */
@Service
public class CaseService {

    private static final Logger log = LoggerFactory.getLogger(CaseService.class);
    private static final int PHOTO_LIMIT = CaseContextAssembler.PHOTO_LIMIT;

    private final CaseStore store;
    private final ImageAnalysisService analysis;
    private final DecisionService decisions;
    private final TriageStage triage;
    private final Clock clock;
    private final Duration decisionBudget;
    private final Map<UUID, RunningTurn> running = new ConcurrentHashMap<>();

    private record RunningTurn(CaseSession session, CancelToken token) {
    }

    /** Result of the pre-stream checks of a chat turn: either a replay of a committed turn or a reservation. */
    public sealed interface TurnStart permits Replay, Reservation {
    }

    /** A committed turn to replay: its decision snapshot, the full assistant text and the assistant message ID. */
    public record Replay(UUID caseId, Decision decision, String text, UUID messageId) implements TurnStart {
    }

    /**
     * The case is reserved for one running turn (the busy check). Must be passed to {@link #runTurn} or released
     * with {@link #release}.
     */
    public static final class Reservation implements TurnStart {
        private final CaseSession session;
        private final RunningTurn turn;

        private Reservation(CaseSession session, RunningTurn turn) {
            this.session = session;
            this.turn = turn;
        }

        public UUID caseId() {
            return session.getCaseId();
        }
    }

    public CaseService(CaseStore store, ImageAnalysisService analysis, DecisionService decisions, TriageStage triage,
            Clock clock, @Value("${app.timeouts.decision-budget:55s}") Duration decisionBudget) {
        this.store = store;
        this.analysis = analysis;
        this.decisions = decisions;
        this.triage = triage;
        this.clock = clock;
        this.decisionBudget = decisionBudget;
    }

    /** Runs the first-decision turn of a new case; called on the turn executor, emits all events itself. */
    public void create(CaseForm form, byte[] photo, SseEventWriter writer) {
        UUID caseId = UUID.randomUUID();
        CaseSession working = new CaseSession(caseId, form, clock.instant());
        CancelToken token = writer.cancelToken();
        AtomicBoolean firstDeltaTimedOut = new AtomicBoolean();
        running.put(caseId, new RunningTurn(working, token));
        MDC.put(AiCallContext.CASE_ID_KEY, caseId.toString());
        working.getLock().lock();
        try {
            Deadline deadline = Deadline.in(decisionBudget);
            writer.startHeartbeat();
            send(writer, SseEventType.CASE, new CaseCreatedEvent(caseId));
            progress(writer, Step.UPLOAD_RECEIVED);
            progress(writer, Step.ANALYZING_PHOTO);
            // ADR-004 section 3.1: triage of the form photo only, inside the 55 s budget (ADR-001 section 3.6).
            TriageStage.Outcome triaged = triage.run(form, photo);
            triaged.result().ifPresent(working::setTriage);
            ImageAnalysis imageAnalysis = analysis.analyze(working, photo, deadline);
            working.getAnalyses().add(imageAnalysis);
            progress(writer, Step.PREPARING_DECISION);
            Decision decision = decisions.decide(working, new TurnInput(null, null, null), deadline);
            triage.logAgreement(working, triaged, decision.label());
            deadline.check();
            send(writer, SseEventType.DECISION, decisionEvent(decision));
            progress(writer, Step.WRITING_REPLY);

            AtomicBoolean firstDelta = new AtomicBoolean();
            CancelToken streamToken = streamToken(token);
            watchFirstDelta(deadline, firstDelta, firstDeltaTimedOut, streamToken);
            String text = decisions.streamMessage(working, decision, MessageKind.FIRST, delta -> {
                firstDelta.set(true);
                send(writer, SseEventType.DELTA, new DeltaEvent(delta));
            }, streamToken);

            UUID messageId = UUID.randomUUID();
            working.getMessages().add(new ChatMessage(messageId, ChatMessage.Role.ASSISTANT, text, null, false, null,
                    decision, decision.changed(), decision.previousLabel(), clock.instant()));
            working.setCurrentDecision(decision);
            if (!commit(working, token)) {
                writer.complete();
                return;
            }
            writer.send(SseEventType.DONE, new DoneEvent(messageId));
            writer.complete();
            log.info("Case created label={} overriddenBy={}", decision.label(), decision.overriddenBy());
        }
        catch (LlmCancelledException e) {
            if (firstDeltaTimedOut.get()) {
                fail(writer, StreamErrorEvent.Code.TIMEOUT, "first delta not received before the deadline");
            }
            else {
                log.info("Turn cancelled (client gone or case deleted); nothing stored");
                writer.complete();
            }
        }
        catch (LlmTimeoutException e) {
            fail(writer, StreamErrorEvent.Code.TIMEOUT, "timeout");
        }
        catch (LlmUnavailableException e) {
            fail(writer, StreamErrorEvent.Code.AI_UNAVAILABLE, "AI unavailable");
        }
        catch (ClientGoneException e) {
            log.info("Client gone during the turn; nothing stored");
            token.cancel();
            writer.complete();
        }
        catch (RuntimeException e) {
            log.error("Turn failed unexpectedly: {}", e.getClass().getName());
            log.debug("Unexpected turn failure", e);
            fail(writer, StreamErrorEvent.Code.AI_UNAVAILABLE, "unexpected");
        }
        finally {
            working.getLock().unlock();
            running.remove(caseId);
            MDC.remove(AiCallContext.CASE_ID_KEY);
        }
    }

    // ------------------------------------------------------------------ chat turn (ADR-002 section 3.4)

    /**
     * Pre-stream checks 2-4 of a chat turn on the request thread: unknown or deleted case (404), an already committed
     * {@code clientMessageId} (replay, no other check applies), a running turn (409). On success the case is reserved
     * for this turn; validation (400) and the photo limit (422, {@link #checkPhotoLimit}) follow.
     */
    public TurnStart beginTurn(UUID caseId, UUID clientMessageId, CancelToken token) {
        CaseSession session = store.find(caseId).filter(s -> !s.isDeleted())
            .orElseThrow(() -> new CaseNotFoundException("unknown or expired case"));
        if (clientMessageId != null && session.getCommittedClientMessageIds().contains(clientMessageId)) {
            Optional<Replay> replay = replayOf(session, clientMessageId);
            if (replay.isPresent()) {
                return replay.get();
            }
        }
        RunningTurn turn = new RunningTurn(session, token);
        if (running.putIfAbsent(caseId, turn) != null) {
            throw new CaseBusyException("a turn of the case is running");
        }
        return new Reservation(session, turn);
    }

    /** Check 6: a photo is rejected with 422 when the case already has {@value CaseContextAssembler#PHOTO_LIMIT}. */
    public void checkPhotoLimit(Reservation reservation, boolean hasPhoto) {
        if (hasPhoto && reservation.session.getAdditionalPhotoCount() >= PHOTO_LIMIT) {
            throw new PhotoLimitException("additional photo limit reached");
        }
    }

    /** Releases a reservation whose turn will not run (pre-stream rejection). Idempotent. */
    public void release(Reservation reservation) {
        running.remove(reservation.caseId(), reservation.turn);
    }

    /** Streams a committed turn again: {@code decision}, one {@code delta} with the full text, {@code done}. */
    public void replay(Replay replay, SseEventWriter writer) {
        MDC.put(AiCallContext.CASE_ID_KEY, replay.caseId().toString());
        try {
            if (writer.send(SseEventType.DECISION, decisionEvent(replay.decision()))
                    && writer.send(SseEventType.DELTA, new DeltaEvent(replay.text()))) {
                writer.send(SseEventType.DONE, new DoneEvent(replay.messageId()));
            }
            log.info("Turn replayed from the committed clientMessageId; no AI call");
        }
        finally {
            writer.complete();
            MDC.remove(AiCallContext.CASE_ID_KEY);
        }
    }

    /**
     * Runs a reserved chat turn on the turn executor (ADR-000 section 9.3). All work happens on a working copy; the
     * stored session changes only in the atomic commit just before {@code done}. Chat photos are not triaged.
     */
    public void runTurn(Reservation reservation, TurnInput input, SseEventWriter writer) {
        CaseSession session = reservation.session;
        UUID caseId = session.getCaseId();
        CancelToken token = writer.cancelToken();
        AtomicBoolean firstDeltaTimedOut = new AtomicBoolean();
        MDC.put(AiCallContext.CASE_ID_KEY, caseId.toString());
        session.getLock().lock();
        try {
            CaseSession working = workingCopy(session);
            Deadline deadline = Deadline.in(decisionBudget);
            writer.startHeartbeat();
            boolean hasPhoto = input.photoJpeg() != null;
            ImageAnalysis imageAnalysis = null;
            Integer analysisIndex = null;
            if (hasPhoto) {
                progress(writer, Step.ANALYZING_PHOTO);
                imageAnalysis = analysis.analyze(working, input.photoJpeg(), deadline);
                working.getAnalyses().add(imageAnalysis);
                analysisIndex = working.getAnalyses().size() - 1;
                working.setAdditionalPhotoCount(working.getAdditionalPhotoCount() + 1);
            }
            progress(writer, Step.PREPARING_DECISION);
            Decision decision = decisions.decide(working, input, deadline);
            deadline.check();
            send(writer, SseEventType.DECISION, decisionEvent(decision));
            progress(writer, Step.WRITING_REPLY);

            ChatMessage userMessage = new ChatMessage(UUID.randomUUID(), ChatMessage.Role.USER,
                    input.text() == null ? "" : input.text(), input.clientMessageId(), hasPhoto, analysisIndex, null,
                    false, null, clock.instant());
            working.getMessages().add(userMessage);

            AtomicBoolean firstDelta = new AtomicBoolean();
            CancelToken streamToken = streamToken(token);
            watchFirstDelta(deadline, firstDelta, firstDeltaTimedOut, streamToken);
            String text = decisions.streamMessage(working, decision, MessageKind.REPLY, delta -> {
                firstDelta.set(true);
                send(writer, SseEventType.DELTA, new DeltaEvent(delta));
            }, streamToken);

            ChatMessage assistant = new ChatMessage(UUID.randomUUID(), ChatMessage.Role.ASSISTANT, text, null, false,
                    null, decision, decision.changed(), decision.changed() ? decision.previousLabel() : null,
                    clock.instant());
            if (session.isDeleted() || token.isCancelled()) {
                log.info("Turn finished after DELETE or disconnect; not stored");
                writer.complete();
                return;
            }
            if (imageAnalysis != null) {
                session.getAnalyses().add(imageAnalysis);
            }
            session.getMessages().addAll(List.of(userMessage, assistant));
            session.setCurrentDecision(decision);
            session.setAdditionalPhotoCount(working.getAdditionalPhotoCount());
            if (input.clientMessageId() != null) {
                session.getCommittedClientMessageIds().add(input.clientMessageId());
            }
            if (!commit(session, token)) {
                writer.complete();
                return;
            }
            writer.send(SseEventType.DONE, new DoneEvent(assistant.id()));
            writer.complete();
            log.info("Chat turn committed label={} changed={} photo={} overriddenBy={}", decision.label(),
                    decision.changed(), hasPhoto, decision.overriddenBy());
        }
        catch (LlmCancelledException e) {
            if (firstDeltaTimedOut.get()) {
                fail(writer, StreamErrorEvent.Code.TIMEOUT, "first delta not received before the deadline");
            }
            else {
                log.info("Turn cancelled (client gone or case deleted); nothing committed");
                writer.complete();
            }
        }
        catch (LlmTimeoutException e) {
            fail(writer, StreamErrorEvent.Code.TIMEOUT, "timeout");
        }
        catch (LlmUnavailableException e) {
            fail(writer, StreamErrorEvent.Code.AI_UNAVAILABLE, "AI unavailable");
        }
        catch (ClientGoneException e) {
            log.info("Client gone during the turn; nothing committed");
            token.cancel();
            writer.complete();
        }
        catch (RuntimeException e) {
            log.error("Turn failed unexpectedly: {}", e.getClass().getName());
            log.debug("Unexpected turn failure", e);
            fail(writer, StreamErrorEvent.Code.AI_UNAVAILABLE, "unexpected");
        }
        finally {
            session.getLock().unlock();
            release(reservation);
            MDC.remove(AiCallContext.CASE_ID_KEY);
        }
    }

    /** The turn's working copy (ADR-002 section 3.4 step 2): the pipeline never mutates the stored session. */
    static CaseSession workingCopy(CaseSession session) {
        CaseSession copy = new CaseSession(session.getCaseId(), session.getForm(), session.getCreatedAt());
        session.getTriage().ifPresent(copy::setTriage);
        copy.getAnalyses().addAll(session.getAnalyses());
        copy.getMessages().addAll(session.getMessages());
        copy.getCommittedClientMessageIds().addAll(session.getCommittedClientMessageIds());
        copy.setCurrentDecision(session.getCurrentDecision());
        copy.setAdditionalPhotoCount(session.getAdditionalPhotoCount());
        return copy;
    }

    /** The committed user message with this ID and the assistant message that answered it. */
    static Optional<Replay> replayOf(CaseSession session, UUID clientMessageId) {
        List<ChatMessage> messages = List.copyOf(session.getMessages());
        for (int i = 0; i < messages.size() - 1; i++) {
            ChatMessage m = messages.get(i);
            if (m.role() == ChatMessage.Role.USER && clientMessageId.equals(m.clientMessageId())) {
                ChatMessage answer = messages.get(i + 1);
                if (answer.role() == ChatMessage.Role.ASSISTANT && answer.decisionSnapshot() != null) {
                    return Optional.of(new Replay(session.getCaseId(), answer.decisionSnapshot(), answer.text(),
                            answer.id()));
                }
            }
        }
        return Optional.empty();
    }

    /** DELETE /api/cases/{id}: idempotent; marks the case deleted, removes it and cancels a running turn. */
    public void delete(UUID caseId) {
        RunningTurn turn = running.get(caseId);
        if (turn != null) {
            turn.session().setDeleted(true);
            turn.token().cancel();
        }
        store.find(caseId).ifPresent(s -> s.setDeleted(true));
        store.remove(caseId);
    }

    /**
     * Re-puts the session unless the case was deleted or the client left (ADR-002 section 5). A DELETE can land
     * between the check and the put; {@link #delete} marks the session deleted before it removes it, so re-checking
     * the flag after the put and removing again guarantees a deleted case is never re-inserted.
     */
    private boolean commit(CaseSession session, CancelToken token) {
        if (session.isDeleted() || token.isCancelled()) {
            log.info("Turn finished after DELETE or disconnect; not stored");
            return false;
        }
        store.save(session);
        if (session.isDeleted()) {
            store.remove(session.getCaseId());
            log.info("Case deleted during the commit; removed again, not stored");
            return false;
        }
        return true;
    }

    /**
     * The cancellation handle of one model stream: cancelled when the client is gone or the case is deleted (the
     * writer's token) and by the first-delta deadline. The deadline cancels only this stream token, so the writer
     * still delivers the final {@code error{TIMEOUT}} (ADR-000 section 6: {@code done} or {@code error} is last).
     */
    private static CancelToken streamToken(CancelToken clientToken) {
        CancelToken streamToken = new CancelToken();
        clientToken.onCancel(streamToken::cancel);
        return streamToken;
    }

    private void fail(SseEventWriter writer, StreamErrorEvent.Code code, String why) {
        log.warn("Turn failed ({}), sending error {}; nothing stored", why, code);
        writer.error(code);
    }

    /** Cancels the stream when no delta arrived before the 55 s deadline (ADR-001 section 3.6). */
    private static void watchFirstDelta(Deadline deadline, AtomicBoolean firstDelta, AtomicBoolean timedOut,
            CancelToken token) {
        long ms = Math.max(0, deadline.remaining().toMillis());
        CompletableFuture.runAsync(() -> {
            if (!firstDelta.get() && !token.isCancelled()) {
                timedOut.set(true);
                token.cancel();
            }
        }, CompletableFuture.delayedExecutor(ms, TimeUnit.MILLISECONDS));
    }

    private static void progress(SseEventWriter writer, Step step) {
        send(writer, SseEventType.PROGRESS, new ProgressEvent(step));
    }

    private static void send(SseEventWriter writer, String name, Object payload) {
        if (!writer.send(name, payload)) {
            throw new ClientGoneException();
        }
    }

    /**
     * The wire payload. {@code previousLabel} is sent only with {@code changed=true} (ADR-000 section 6:
     * {@code previousLabel?}); {@link Decision} keeps the label before the turn also when it did not change.
     */
    static DecisionEvent decisionEvent(Decision d) {
        return new DecisionEvent(d.label(), d.changed() ? d.previousLabel() : null, d.changed(), d.changeReasonPl(), d.safety(),
                d.suggestOtherCaseType(), d.ruleIds());
    }

    /** The SSE client is gone (a write failed); the turn stops without an error event. */
    private static final class ClientGoneException extends RuntimeException {
        ClientGoneException() {
            super("SSE client gone", null, false, false);
        }
    }
}
