package com.hsdc.copilot.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.io.DefaultResourceLoader;

import com.hsdc.copilot.analysis.ImageAnalysis;
import com.hsdc.copilot.cases.CaseForm;
import com.hsdc.copilot.cases.CaseSession;
import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.cases.Category;
import com.hsdc.copilot.cases.TurnInput;
import com.hsdc.copilot.config.OpenRouterProperties;
import com.hsdc.copilot.config.TriageProperties;
import com.hsdc.copilot.decision.DecisionResult.Label;
import com.hsdc.copilot.decision.DecisionResult.ReasonCode;
import com.hsdc.copilot.decision.DecisionResult.SuggestOtherCaseType;
import com.hsdc.copilot.llm.AiCallContext;
import com.hsdc.copilot.llm.CancelToken;
import com.hsdc.copilot.llm.Deadline;
import com.hsdc.copilot.llm.LlmGateway;
import com.hsdc.copilot.llm.LlmRequest;
import com.hsdc.copilot.llm.LlmUnavailableException;
import com.hsdc.copilot.llm.ReasoningEffort;
import com.hsdc.copilot.llm.StreamResult;
import com.hsdc.copilot.policy.PolicyRepository;
import com.hsdc.copilot.prompts.GuardrailReasons;
import com.hsdc.copilot.prompts.PromptRepository;
import com.hsdc.copilot.triage.TriageAnswer;
import com.hsdc.copilot.triage.TriageResult;

import tools.jackson.databind.json.JsonMapper;

class DecisionServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), ZONE);

    /** Answers structured calls from a queue and records each request with its MDC step. */
    static final class FakeGateway implements LlmGateway {
        final Deque<DecisionResult> answers = new ArrayDeque<>();
        final List<LlmRequest> requests = new ArrayList<>();
        final List<String> steps = new ArrayList<>();
        List<String> deltas = List.of();

        @Override
        public <T> T structured(LlmRequest request, Class<T> type) {
            requests.add(request);
            steps.add(MDC.get(AiCallContext.STEP_KEY));
            return type.cast(answers.removeFirst());
        }

        @Override
        public StreamResult stream(LlmRequest request, Consumer<String> deltaConsumer, CancelToken cancelToken) {
            requests.add(request);
            steps.add(MDC.get(AiCallContext.STEP_KEY));
            deltas.forEach(deltaConsumer);
            return new StreamResult(String.join("", deltas), 1, 1, StreamResult.FinishStatus.COMPLETED);
        }

        @Override
        public String describe() {
            return "fake";
        }
    }

    private final FakeGateway gateway = new FakeGateway();
    private final DecisionService service = service(gateway);

    private static DecisionService service(LlmGateway gateway) {
        DefaultResourceLoader loader = new DefaultResourceLoader();
        JsonMapper json = JsonMapper.builder().build();
        return new DecisionService(gateway, new PromptRepository(loader), new PolicyRepository(loader),
                new Guardrails(new GuardrailReasons(loader)), new CaseContextAssembler(CLOCK, json),
                new OpenRouterProperties("k", null, "v/model", "d/model", ReasoningEffort.HIGH, null, null),
                new TriageProperties(TriageProperties.Mode.OFF, "t", 0.8, 0.95, 0.3, null, Duration.ofSeconds(8)),
                json, Duration.ofSeconds(20), Duration.ofSeconds(60));
    }

    private static CaseSession returnCase(int daysAgo, String reason) {
        CaseSession s = new CaseSession(UUID.randomUUID(), new CaseForm(CaseType.RETURN, Category.SMARTPHONE,
                "Galaxy S23", LocalDate.now(CLOCK).minusDays(daysAgo), reason), CLOCK.instant());
        s.getAnalyses().add(new ImageAnalysis(true, ImageAnalysis.PhotoProblem.NONE, true, false,
                ImageAnalysis.DamageType.NONE, "", ImageAnalysis.LikelyCause.UNKNOWN, ImageAnalysis.CauseConfidence.LOW,
                false, List.of(), true, false, "Telefon bez śladów użycia.", List.of(), ImageAnalysis.Source.LLM));
        return s;
    }

    private static DecisionResult result(Label label, String... ids) {
        return new DecisionResult(label, List.of(ids), ReasonCode.RESELLABLE, SuggestOtherCaseType.NONE, "", "");
    }

    private static final TurnInput FIRST = new TurnInput(null, null, null);

    @Test
    void validIdsNeedOneDecisionCallWithTheConfiguredEffortAndCaseFactsFirst() {
        gateway.answers.add(result(Label.ACCEPTED_PRELIMINARY, "R2.2", "R3.1"));

        Decision d = service.decide(returnCase(10, "Zmiana zdania"), FIRST, Deadline.in(Duration.ofSeconds(55)));

        assertThat(d.label()).isEqualTo(Label.ACCEPTED_PRELIMINARY);
        assertThat(d.ruleIds()).containsExactly("R2.2", "R3.1");
        assertThat(d.changed()).isFalse();
        assertThat(d.previousLabel()).isNull();
        assertThat(gateway.steps).containsExactly("DECISION");
        LlmRequest request = gateway.requests.get(0);
        assertThat(request.reasoningEffort()).isEqualTo(ReasoningEffort.HIGH);
        assertThat(request.model()).isEqualTo("d/model");
        assertThat(request.instructions()).doesNotContain("{{policy}}");
        String facts = request.inputItems().get(0).text();
        assertThat(facts).startsWith(CaseContextAssembler.CASE_FACTS_LABEL).contains("\"today\":\"2026-11-26\"")
            .contains("\"daysSincePurchase\":10").contains("\"imageAnalyses\"").doesNotContain("triageSignals");
        assertThat(request.inputItems().get(1).text()).startsWith(CaseContextAssembler.CUSTOMER_REASON_LABEL);
    }

    @Test
    void unknownIdsTriggerOneReAskWhoseAnswerIsUsed() {
        gateway.answers.add(result(Label.ACCEPTED_PRELIMINARY, "R99"));
        gateway.answers.add(result(Label.ACCEPTED_PRELIMINARY, "R2.2"));

        Decision d = service.decide(returnCase(10, "Zmiana zdania"), FIRST, Deadline.in(Duration.ofSeconds(55)));

        assertThat(gateway.steps).containsExactly("DECISION", "DECISION_REASK");
        assertThat(d.ruleIds()).containsExactly("R2.2");
        assertThat(d.overriddenBy()).isNull();
    }

    @Test
    void noReAskWithLessThan15SecondsLeft_unknownIdsAreDroppedAndTheCaseEscalatedWithR6() {
        gateway.answers.add(result(Label.ACCEPTED_PRELIMINARY, "C3"));

        Decision d = service.decide(returnCase(10, "Zmiana zdania"), FIRST, Deadline.in(Duration.ofSeconds(10)));

        assertThat(gateway.steps).containsExactly("DECISION");
        assertThat(d.label()).isEqualTo(Label.ESCALATED);
        assertThat(d.ruleIds()).containsExactly("R6");
        assertThat(d.overriddenBy()).isEqualTo("G6");
    }

    @Test
    void safetyWordingInTheReasonWinsOverTheTimeWindow() {
        gateway.answers.add(result(Label.ACCEPTED_PRELIMINARY, "R2.2"));

        Decision d = service.decide(returnCase(45, "Bateria spuchła"), FIRST, Deadline.in(Duration.ofSeconds(55)));

        assertThat(d.label()).isEqualTo(Label.ESCALATED);
        assertThat(d.safety()).isTrue();
        assertThat(d.ruleIds()).containsExactly("R7.1");
    }

    @Test
    void aResponseWithoutLabelIsUnavailable() {
        gateway.answers.add(new DecisionResult(null, null, null, null, null, null));

        assertThatThrownBy(() -> service.decide(returnCase(10, null), FIRST, Deadline.in(Duration.ofSeconds(55))))
            .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void sanitizeCutsTextsAndFillsDefaults() {
        DecisionResult r = DecisionService.sanitize(new DecisionResult(Label.REJECTED, null, null, null,
                "x".repeat(400), null));

        assertThat(r.ruleIds()).isEmpty();
        assertThat(r.reasonCode()).isEqualTo(ReasonCode.OTHER);
        assertThat(r.suggestOtherCaseType()).isEqualTo(SuggestOtherCaseType.NONE);
        assertThat(r.changeReasonPl()).hasSize(300);
        assertThat(r.missingInfoPl()).isEmpty();
    }

    @Test
    void streamedMessageIsFilteredAndTheDecisionItemComesLast() {
        CaseSession session = returnCase(10, "Zmiana zdania");
        Decision decision = new Decision(Label.ACCEPTED_PRELIMINARY, List.of("R2.2"), ReasonCode.RESELLABLE,
                SuggestOtherCaseType.NONE, "", "", false, false, null, null);
        gateway.deltas = List.of("Zgodnie z [R2", ".2] i [R9", "9] oraz [x");
        List<String> sent = new ArrayList<>();

        String text = service.streamMessage(session, decision, DecisionService.MessageKind.FIRST, sent::add,
                new CancelToken());

        assertThat(text).isEqualTo("Zgodnie z [R2.2] i  oraz [x");
        assertThat(String.join("", sent)).isEqualTo(text);
        assertThat(gateway.steps).containsExactly("STREAM");
        LlmRequest request = gateway.requests.get(0);
        assertThat(request.reasoningEffort()).isEqualTo(ReasoningEffort.LOW);
        assertThat(request.schema()).isNull();
        List<LlmRequest.InputItem> items = request.inputItems();
        assertThat(items.get(items.size() - 1).text()).startsWith(CaseContextAssembler.DECISION_LABEL)
            .contains("\"ruleIds\":[\"R2.2\"]");
    }

    // ------------------------------------------------------------ T29: triage signals and G1 from triage

    /** A triage result as the domain object (not a wire payload): only safety_hazard, P(true) = 0.6. */
    private static TriageResult triageWithSafety(double p) {
        TriageAnswer safety = new TriageAnswer("safety_hazard", TriageAnswer.Type.NOUL, p >= 0.5, p, null,
                java.util.Map.of("true", p, "false", 1 - p), Math.max(p, 1 - p), true, TriageAnswer.Band.LOW, false);
        return new TriageResult("typesafe/jev-1.14", java.util.Map.of("safety_hazard", safety), 700, null,
                TriageResult.Status.OK);
    }

    @Test
    void triageSafetyProbabilityAtLeastTheThreshold_escalatesAReturn45DaysOldWithR7_1_viaG1() {
        CaseSession session = returnCase(45, "Zmiana zdania");
        session.setTriage(triageWithSafety(0.6));
        gateway.answers.add(new DecisionResult(Label.REJECTED, List.of("R2.3"), ReasonCode.OUTSIDE_WINDOW,
                SuggestOtherCaseType.NONE, "", ""));

        Decision d = service.decide(session, FIRST, Deadline.in(Duration.ofSeconds(55)));

        assertThat(d.label()).isEqualTo(Label.ESCALATED);
        assertThat(d.safety()).isTrue();
        assertThat(d.ruleIds()).containsExactly("R7.1");
        assertThat(d.overriddenBy()).isEqualTo("G1");
        // ADR-004 section 3.4: the signals are in CASE FACTS with answer, confidence and band.
        String facts = gateway.requests.get(0).inputItems().get(0).text();
        assertThat(facts).contains("\"triageSignals\":[{\"key\":\"safety_hazard\",\"answer\":true,"
                + "\"confidence\":0.6,\"band\":\"LOW\"}]");
    }

    @Test
    void triageSafetyProbabilityBelowTheThreshold_doesNotEscalate() {
        CaseSession session = returnCase(45, "Zmiana zdania");
        session.setTriage(triageWithSafety(0.2));
        gateway.answers.add(new DecisionResult(Label.REJECTED, List.of("R2.3"), ReasonCode.OUTSIDE_WINDOW,
                SuggestOtherCaseType.NONE, "", ""));

        Decision d = service.decide(session, FIRST, Deadline.in(Duration.ofSeconds(55)));

        assertThat(d.label()).isEqualTo(Label.REJECTED);
        assertThat(d.safety()).isFalse();
    }

    @Test
    void failedTriage_addsNoSignals() {
        CaseSession session = returnCase(10, "Zmiana zdania");
        session.setTriage(new TriageResult(null, java.util.Map.of(), 8000, null, TriageResult.Status.TIMEOUT));
        gateway.answers.add(result(Label.ACCEPTED_PRELIMINARY, "R2.2"));

        service.decide(session, FIRST, Deadline.in(Duration.ofSeconds(55)));

        assertThat(gateway.requests.get(0).inputItems().get(0).text()).doesNotContain("triageSignals");
    }

    @Test
    void photoOnlyChatTurn_sendsTheSameTrustedMarkerToTheDecisionAndTheMessageStep() {
        CaseSession session = returnCase(10, "Zmiana zdania");
        gateway.answers.add(result(Label.ACCEPTED_PRELIMINARY, "R2.2"));

        Decision d = service.decide(session, new TurnInput(null, new byte[] { 1 }, UUID.randomUUID()),
                Deadline.in(Duration.ofSeconds(55)));
        List<LlmRequest.InputItem> decideItems = gateway.requests.get(0).inputItems();
        assertThat(decideItems.get(decideItems.size() - 1).text()).isEqualTo(CaseContextAssembler.PHOTO_ONLY_MESSAGE);

        session.getMessages().add(new com.hsdc.copilot.cases.ChatMessage(UUID.randomUUID(),
                com.hsdc.copilot.cases.ChatMessage.Role.USER, "", UUID.randomUUID(), true, 1, null, false, null,
                CLOCK.instant()));
        service.streamMessage(session, d, DecisionService.MessageKind.REPLY, x -> {
        }, new CancelToken());
        List<LlmRequest.InputItem> streamItems = gateway.requests.get(1).inputItems();
        assertThat(streamItems.get(streamItems.size() - 2).text()).isEqualTo(CaseContextAssembler.PHOTO_ONLY_MESSAGE);
    }
}
