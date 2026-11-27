package com.hsdc.copilot.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.hsdc.copilot.analysis.ImageAnalysis;
import com.hsdc.copilot.analysis.ImageAnalysisService;
import com.hsdc.copilot.cases.CaseService.Replay;
import com.hsdc.copilot.cases.CaseService.Reservation;
import com.hsdc.copilot.cases.CaseService.TurnStart;
import com.hsdc.copilot.config.TriageProperties;
import com.hsdc.copilot.decision.Decision;
import com.hsdc.copilot.decision.DecisionResult.Label;
import com.hsdc.copilot.decision.DecisionResult.ReasonCode;
import com.hsdc.copilot.decision.DecisionResult.SuggestOtherCaseType;
import com.hsdc.copilot.decision.DecisionService;
import com.hsdc.copilot.llm.CancelToken;
import com.hsdc.copilot.llm.Deadline;
import com.hsdc.copilot.llm.LlmCancelledException;
import com.hsdc.copilot.llm.LlmUnavailableException;
import com.hsdc.copilot.web.SseEventWriter;
import com.hsdc.copilot.web.sse.DecisionEvent;

import tools.jackson.databind.json.JsonMapper;

/** Chat-turn orchestration of {@link CaseService} (ADR-002 section 3.4) with fake analysis and decision steps. */
class CaseServiceTurnTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), ZoneId.of("Europe/Warsaw"));
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00 };

    /** Records the SSE events (name and JSON data) written through {@link SseEventWriter}. */
    static final class RecordingEmitter extends SseEmitter {
        final List<String> events = new ArrayList<>();
        boolean completed;

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            StringBuilder sb = new StringBuilder();
            builder.build().forEach(d -> sb.append(d.getData()));
            String raw = sb.toString();
            if (raw.startsWith("event:")) {
                events.add(raw.substring(6, raw.indexOf('\n')) + " " + raw.substring(raw.indexOf("data:") + 5).strip());
            }
        }

        @Override
        public synchronized void complete() {
            completed = true;
        }

        List<String> names() {
            return events.stream().map(e -> e.substring(0, e.indexOf(' '))).toList();
        }
    }

    static final class FakeAnalysis extends ImageAnalysisService {
        final List<CaseSession> seen = new ArrayList<>();

        FakeAnalysis() {
            super(null, null, null, null, Duration.ofSeconds(20));
        }

        @Override
        public ImageAnalysis analyze(CaseSession session, byte[] photo, Deadline deadline) {
            seen.add(session);
            return analysis();
        }
    }

    static final class FakeDecisions extends DecisionService {
        Decision next = decision(Label.ACCEPTED_PRELIMINARY, false, null);
        RuntimeException failDecide;
        /** Simulates a model that sends no delta: blocks until the stream's token is cancelled, then throws. */
        boolean stallUntilCancelled;
        Runnable duringStream = () -> {
        };
        final List<CaseSession> decided = new ArrayList<>();
        final List<Integer> historySizeAtStream = new ArrayList<>();

        FakeDecisions() {
            super(null, null, null, null, null, null, null, null, Duration.ofSeconds(20), Duration.ofSeconds(60));
        }

        @Override
        public Decision decide(CaseSession session, TurnInput input, Deadline deadline) {
            decided.add(session);
            if (failDecide != null) {
                throw failDecide;
            }
            return next;
        }

        @Override
        public String streamMessage(CaseSession session, Decision decision, MessageKind kind, Consumer<String> onDelta,
                CancelToken cancelToken) {
            historySizeAtStream.add(session.getMessages().size());
            if (stallUntilCancelled) {
                long until = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                while (!cancelToken.isCancelled() && System.nanoTime() < until) {
                    Thread.onSpinWait();
                }
                throw new LlmCancelledException("stream cancelled");
            }
            onDelta.accept("Dzień dobry, ");
            duringStream.run();
            onDelta.accept("oto odpowiedź.");
            return "Dzień dobry, oto odpowiedź.";
        }
    }

    private final CaffeineCaseStore store = new CaffeineCaseStore(Duration.ofMinutes(60));
    private final FakeAnalysis analysis = new FakeAnalysis();
    private final FakeDecisions decisions = new FakeDecisions();
    private final TriageStage triageOff = new TriageStage(null, null, null, new TriageProperties(TriageProperties.Mode.OFF,
            "test/triage-model", 0.80, 0.95, 0.30, "http://localhost:0", Duration.ofSeconds(8)), CLOCK);
    private final CaseService service = new CaseService(store, analysis, decisions, triageOff, CLOCK,
            Duration.ofSeconds(55));
    private final JsonMapper json = JsonMapper.builder().build();

    static ImageAnalysis analysis() {
        return new ImageAnalysis(true, ImageAnalysis.PhotoProblem.NONE, true, false, ImageAnalysis.DamageType.NONE, "",
                ImageAnalysis.LikelyCause.UNKNOWN, ImageAnalysis.CauseConfidence.LOW, false, List.of(), true, false,
                "Telefon.", List.of(), ImageAnalysis.Source.LLM);
    }

    static Decision decision(Label label, boolean changed, Label previous) {
        return new Decision(label, List.of("R1.1"), ReasonCode.OTHER, SuggestOtherCaseType.NONE, "", "", false, changed,
                previous, null);
    }

    private CaseSession storedCase() {
        CaseSession s = new CaseSession(UUID.randomUUID(), new CaseForm(CaseType.RETURN, Category.SMARTPHONE,
                "Galaxy S23", LocalDate.now(CLOCK).minusDays(10), "Zmiana zdania"), CLOCK.instant());
        s.getAnalyses().add(analysis());
        Decision first = decision(Label.ACCEPTED_PRELIMINARY, false, null);
        s.getMessages().add(new ChatMessage(UUID.randomUUID(), ChatMessage.Role.ASSISTANT, "Pierwsza odpowiedź", null,
                false, null, first, false, null, CLOCK.instant()));
        s.setCurrentDecision(first);
        store.save(s);
        return s;
    }

    private Reservation reserve(CaseSession s, UUID clientMessageId, SseEventWriter writer) {
        TurnStart start = service.beginTurn(s.getCaseId(), clientMessageId, writer.cancelToken());
        assertThat(start).isInstanceOf(Reservation.class);
        return (Reservation) start;
    }

    private SseEventWriter writer(RecordingEmitter emitter) {
        return new SseEventWriter(emitter, json, new CancelToken());
    }

    // ------------------------------------------------------------------ pre-stream checks

    @Test
    void unknownOrDeletedCase_isNotFound() {
        assertThatThrownBy(() -> service.beginTurn(UUID.randomUUID(), UUID.randomUUID(), new CancelToken()))
            .isInstanceOf(CaseNotFoundException.class);
        CaseSession s = storedCase();
        s.setDeleted(true);
        assertThatThrownBy(() -> service.beginTurn(s.getCaseId(), UUID.randomUUID(), new CancelToken()))
            .isInstanceOf(CaseNotFoundException.class);
    }

    @Test
    void aSecondTurnWhileOneIsReserved_isBusy_untilReleased() {
        CaseSession s = storedCase();
        Reservation first = (Reservation) service.beginTurn(s.getCaseId(), UUID.randomUUID(), new CancelToken());

        assertThatThrownBy(() -> service.beginTurn(s.getCaseId(), UUID.randomUUID(), new CancelToken()))
            .isInstanceOf(CaseBusyException.class);

        service.release(first);
        service.release(first);
        assertThat(service.beginTurn(s.getCaseId(), UUID.randomUUID(), new CancelToken()))
            .isInstanceOf(Reservation.class);
    }

    @Test
    void photoLimit_rejectsOnlyAPhotoAtThreeAdditionalPhotos() {
        CaseSession s = storedCase();
        s.setAdditionalPhotoCount(2);
        Reservation r = (Reservation) service.beginTurn(s.getCaseId(), UUID.randomUUID(), new CancelToken());
        service.checkPhotoLimit(r, true);

        s.setAdditionalPhotoCount(3);
        service.checkPhotoLimit(r, false);
        assertThatThrownBy(() -> service.checkPhotoLimit(r, true)).isInstanceOf(PhotoLimitException.class);
    }

    @Test
    void committedClientMessageId_replays_evenWhileBusyAndAtThePhotoLimit() {
        CaseSession s = storedCase();
        RecordingEmitter emitter = new RecordingEmitter();
        UUID clientMessageId = UUID.randomUUID();
        service.runTurn(reserve(s, clientMessageId, writer(emitter)), new TurnInput("Zdjęcie", JPEG, clientMessageId),
                writer(emitter));
        s.setAdditionalPhotoCount(3);
        service.beginTurn(s.getCaseId(), UUID.randomUUID(), new CancelToken()); // another turn is now running

        TurnStart start = service.beginTurn(s.getCaseId(), clientMessageId, new CancelToken());

        assertThat(start).isInstanceOf(Replay.class);
        Replay replay = (Replay) start;
        ChatMessage assistant = s.getMessages().get(s.getMessages().size() - 1);
        assertThat(replay.messageId()).isEqualTo(assistant.id());
        assertThat(replay.text()).isEqualTo("Dzień dobry, oto odpowiedź.");
        assertThat(replay.decision()).isEqualTo(assistant.decisionSnapshot());
    }

    @Test
    void replay_streamsDecisionOneDeltaAndDone() {
        RecordingEmitter emitter = new RecordingEmitter();
        UUID messageId = UUID.randomUUID();

        service.replay(new Replay(UUID.randomUUID(), decision(Label.REJECTED, true, Label.ACCEPTED_PRELIMINARY),
                "Cały tekst", messageId), writer(emitter));

        assertThat(emitter.names()).containsExactly("decision", "delta", "done");
        assertThat(emitter.events.get(1)).contains("Cały tekst");
        assertThat(emitter.events.get(2)).contains(messageId.toString());
        assertThat(emitter.completed).isTrue();
    }

    // ------------------------------------------------------------------ the turn

    @Test
    void photoTurn_runsOnAWorkingCopy_andCommitsEverythingAtomically() {
        CaseSession s = storedCase();
        decisions.next = decision(Label.REJECTED, true, Label.ACCEPTED_PRELIMINARY);
        RecordingEmitter emitter = new RecordingEmitter();
        SseEventWriter writer = writer(emitter);
        UUID clientMessageId = UUID.randomUUID();
        Reservation reservation = reserve(s, clientMessageId, writer);
        int messagesBefore = s.getMessages().size();

        service.runTurn(reservation, new TurnInput("Dodaję zdjęcie", JPEG, clientMessageId), writer);

        assertThat(emitter.names()).containsExactly("progress", "progress", "decision", "progress", "delta", "delta",
                "done");
        assertThat(emitter.events.get(0)).contains("ANALYZING_PHOTO");
        assertThat(emitter.events.get(1)).contains("PREPARING_DECISION");
        assertThat(emitter.events.get(3)).contains("WRITING_REPLY");
        assertThat(decisions.decided.get(0)).as("the pipeline sees a working copy").isNotSameAs(s);
        assertThat(analysis.seen.get(0)).isNotSameAs(s);
        assertThat(decisions.historySizeAtStream).as("the stream sees the user message").containsExactly(messagesBefore + 1);

        assertThat(s.getMessages()).hasSize(messagesBefore + 2);
        ChatMessage user = s.getMessages().get(messagesBefore);
        ChatMessage assistant = s.getMessages().get(messagesBefore + 1);
        assertThat(user.role()).isEqualTo(ChatMessage.Role.USER);
        assertThat(user.clientMessageId()).isEqualTo(clientMessageId);
        assertThat(user.hasPhoto()).isTrue();
        assertThat(user.analysisIndex()).isEqualTo(1);
        assertThat(assistant.text()).isEqualTo("Dzień dobry, oto odpowiedź.");
        assertThat(assistant.changed()).isTrue();
        assertThat(assistant.previousLabel()).isEqualTo(Label.ACCEPTED_PRELIMINARY);
        assertThat(emitter.events.get(6)).contains(assistant.id().toString());
        assertThat(s.getAnalyses()).hasSize(2);
        assertThat(s.getAdditionalPhotoCount()).isEqualTo(1);
        assertThat(s.getCurrentDecision().label()).isEqualTo(Label.REJECTED);
        assertThat(s.getCommittedClientMessageIds()).containsExactly(clientMessageId);
        assertThat(store.find(s.getCaseId())).containsSame(s);
        assertThat(service.beginTurn(s.getCaseId(), UUID.randomUUID(), new CancelToken()))
            .as("the reservation is released").isInstanceOf(Reservation.class);
    }

    @Test
    void textTurn_hasNoPhotoStep_andDoesNotCountAPhoto() {
        CaseSession s = storedCase();
        RecordingEmitter emitter = new RecordingEmitter();
        SseEventWriter writer = writer(emitter);
        UUID clientMessageId = UUID.randomUUID();

        service.runTurn(reserve(s, clientMessageId, writer), new TurnInput("Pytanie", null, clientMessageId), writer);

        assertThat(emitter.names()).containsExactly("progress", "decision", "progress", "delta", "delta", "done");
        assertThat(analysis.seen).isEmpty();
        assertThat(s.getAdditionalPhotoCount()).isZero();
        ChatMessage user = s.getMessages().get(s.getMessages().size() - 2);
        assertThat(user.hasPhoto()).isFalse();
        assertThat(user.analysisIndex()).isNull();
    }

    @Test
    void failedTurn_commitsNothing_sendsError_andReleases() {
        CaseSession s = storedCase();
        decisions.failDecide = new LlmUnavailableException("401");
        RecordingEmitter emitter = new RecordingEmitter();
        SseEventWriter writer = writer(emitter);
        UUID clientMessageId = UUID.randomUUID();
        int messages = s.getMessages().size();

        service.runTurn(reserve(s, clientMessageId, writer), new TurnInput("Tekst", JPEG, clientMessageId), writer);

        assertThat(emitter.names()).containsExactly("progress", "progress", "error");
        assertThat(emitter.events.get(2)).contains("AI_UNAVAILABLE");
        assertThat(s.getMessages()).hasSize(messages);
        assertThat(s.getAnalyses()).hasSize(1);
        assertThat(s.getAdditionalPhotoCount()).isZero();
        assertThat(s.getCommittedClientMessageIds()).isEmpty();
        assertThat(service.beginTurn(s.getCaseId(), clientMessageId, new CancelToken()))
            .as("a failed ID is processed normally, not replayed").isInstanceOf(Reservation.class);
    }

    @Test
    void deleteDuringTheStream_commitsNothing_andTheCaseStaysDeleted() {
        CaseSession s = storedCase();
        decisions.duringStream = () -> service.delete(s.getCaseId());
        RecordingEmitter emitter = new RecordingEmitter();
        SseEventWriter writer = writer(emitter);
        UUID clientMessageId = UUID.randomUUID();
        int messages = s.getMessages().size();

        service.runTurn(reserve(s, clientMessageId, writer), new TurnInput("Tekst", null, clientMessageId), writer);

        assertThat(emitter.names()).doesNotContain("done", "error");
        assertThat(emitter.completed).isTrue();
        assertThat(store.find(s.getCaseId())).isEmpty();
        assertThat(s.getMessages()).hasSize(messages);
        assertThat(writer.cancelToken().isCancelled()).isTrue();
    }

    // ------------------------------------------------------------------ T50 review findings

    /** A store whose {@code save} runs a hook first: a DELETE landing between the commit check and the put. */
    static final class InterleavingStore implements CaseStore {
        final CaffeineCaseStore delegate = new CaffeineCaseStore(Duration.ofMinutes(60));
        Runnable beforeSave = () -> {
        };

        @Override
        public java.util.Optional<CaseSession> find(UUID caseId) {
            return delegate.find(caseId);
        }

        @Override
        public void save(CaseSession session) {
            Runnable hook = beforeSave;
            beforeSave = () -> {
            };
            hook.run();
            delegate.save(session);
        }

        @Override
        public void remove(UUID caseId) {
            delegate.remove(caseId);
        }
    }

    private CaseService shortBudgetService(CaseStore caseStore) {
        return new CaseService(caseStore, analysis, decisions, triageOff, CLOCK, Duration.ofMillis(300));
    }

    @Test
    void firstSubmission_firstDeltaDeadline_endsWithErrorTimeout_andStoresNothing() {
        decisions.stallUntilCancelled = true;
        CaffeineCaseStore emptyStore = new CaffeineCaseStore(Duration.ofMinutes(60));
        RecordingEmitter emitter = new RecordingEmitter();
        SseEventWriter writer = writer(emitter);

        shortBudgetService(emptyStore).create(new CaseForm(CaseType.RETURN, Category.SMARTPHONE, "Galaxy S23",
                LocalDate.now(CLOCK).minusDays(10), "Zmiana zdania"), JPEG, writer);

        assertThat(emitter.names()).as("done or error is always last (ADR-000 section 6)").last().isEqualTo("error");
        assertThat(emitter.events.get(emitter.events.size() - 1)).contains("TIMEOUT");
        assertThat(emitter.names()).doesNotContain("done");
        assertThat(emitter.completed).isTrue();
        assertThat(emptyStore.estimatedSize()).as("nothing committed").isZero();
        assertThat(writer.cancelToken().isCancelled()).as("the deadline is not a client disconnect").isFalse();
    }

    @Test
    void chatTurn_firstDeltaDeadline_endsWithErrorTimeout_andCommitsNothing() {
        CaseSession s = storedCase();
        decisions.stallUntilCancelled = true;
        CaseService shortBudget = shortBudgetService(store);
        RecordingEmitter emitter = new RecordingEmitter();
        SseEventWriter writer = writer(emitter);
        UUID clientMessageId = UUID.randomUUID();
        int messages = s.getMessages().size();
        Reservation reservation = (Reservation) shortBudget.beginTurn(s.getCaseId(), clientMessageId,
                writer.cancelToken());

        shortBudget.runTurn(reservation, new TurnInput("Pytanie", null, clientMessageId), writer);

        assertThat(emitter.names()).last().isEqualTo("error");
        assertThat(emitter.events.get(emitter.events.size() - 1)).contains("TIMEOUT");
        assertThat(emitter.names()).doesNotContain("done");
        assertThat(s.getMessages()).hasSize(messages);
        assertThat(s.getCommittedClientMessageIds()).isEmpty();
        assertThat(store.find(s.getCaseId())).containsSame(s);
    }

    @Test
    void deleteBetweenTheCommitCheckAndThePut_doesNotReinsertTheChatCase() {
        InterleavingStore racing = new InterleavingStore();
        CaseService racingService = new CaseService(racing, analysis, decisions, triageOff, CLOCK,
                Duration.ofSeconds(55));
        CaseSession s = storedCase();
        racing.delegate.save(s);
        RecordingEmitter emitter = new RecordingEmitter();
        SseEventWriter writer = writer(emitter);
        UUID clientMessageId = UUID.randomUUID();
        Reservation reservation = (Reservation) racingService.beginTurn(s.getCaseId(), clientMessageId,
                writer.cancelToken());
        racing.beforeSave = () -> racingService.delete(s.getCaseId());

        racingService.runTurn(reservation, new TurnInput("Pytanie", null, clientMessageId), writer);

        assertThat(racing.find(s.getCaseId())).as("a deleted case is never re-inserted").isEmpty();
        assertThat(emitter.names()).doesNotContain("done");
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void deleteBetweenTheCommitCheckAndThePut_doesNotInsertTheNewCase() {
        InterleavingStore racing = new InterleavingStore();
        CaseService racingService = new CaseService(racing, analysis, decisions, triageOff, CLOCK,
                Duration.ofSeconds(55));
        RecordingEmitter emitter = new RecordingEmitter();
        SseEventWriter writer = writer(emitter);
        UUID[] created = new UUID[1];
        racing.beforeSave = () -> {
            created[0] = UUID.fromString(emitter.events.get(0).replaceAll(".*\"caseId\":\"([^\"]+)\".*", "$1"));
            racingService.delete(created[0]);
        };

        racingService.create(new CaseForm(CaseType.RETURN, Category.SMARTPHONE, "Galaxy S23",
                LocalDate.now(CLOCK).minusDays(10), "Zmiana zdania"), JPEG, writer);

        assertThat(created[0]).isNotNull();
        assertThat(racing.find(created[0])).isEmpty();
        assertThat(racing.delegate.estimatedSize()).isZero();
        assertThat(emitter.names()).doesNotContain("done");
    }

    // ------------------------------------------------------------------ wire payload

    @Test
    void decisionEvent_carriesPreviousLabelOnlyWhenChanged() {
        DecisionEvent unchanged = CaseService.decisionEvent(decision(Label.ACCEPTED_PRELIMINARY, false,
                Label.ACCEPTED_PRELIMINARY));
        DecisionEvent changed = CaseService.decisionEvent(decision(Label.REJECTED, true, Label.ACCEPTED_PRELIMINARY));

        assertThat(unchanged.previousLabel()).isNull();
        assertThat(unchanged.changed()).isFalse();
        assertThat(changed.previousLabel()).isEqualTo(Label.ACCEPTED_PRELIMINARY);
    }

    @Test
    void replayOf_ignoresAnIdWithoutAnAnswer() {
        CaseSession s = storedCase();
        UUID id = UUID.randomUUID();
        s.getMessages().add(new ChatMessage(UUID.randomUUID(), ChatMessage.Role.USER, "x", id, false, null, null,
                false, null, CLOCK.instant()));

        assertThat(CaseService.replayOf(s, id)).isEmpty();
        assertThat(CaseService.replayOf(s, UUID.randomUUID())).isEmpty();
    }
}
