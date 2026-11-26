package com.hsdc.copilot.cases;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hsdc.copilot.config.TriageProperties;
import com.hsdc.copilot.config.TriageProperties.Mode;
import com.hsdc.copilot.decision.CaseFacts;
import com.hsdc.copilot.decision.DecisionResult.Label;
import com.hsdc.copilot.triage.RouteDecision;
import com.hsdc.copilot.triage.TriageAgreementLogger;
import com.hsdc.copilot.triage.TriageAnswer;
import com.hsdc.copilot.triage.TriageResult;
import com.hsdc.copilot.triage.TriageRouter;
import com.hsdc.copilot.triage.TriageService;

/** T29: the first-photo triage stage of {@link CaseService} (ADR-004 sections 3.1, 3.5; TAC-004-01/02). */
class TriageStageTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), ZoneId.of("Europe/Warsaw"));
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00 };

    static final class FakeTriage extends TriageService {
        TriageResult next;
        RuntimeException fail;
        int calls;

        FakeTriage() {
            super(null, null, null);
        }

        @Override
        public TriageResult triage(CaseForm form, CaseFacts facts, byte[] photo) {
            calls++;
            if (fail != null) {
                throw fail;
            }
            return next;
        }
    }

    static final class RecordingAgreement extends TriageAgreementLogger {
        final List<String> lines = new ArrayList<>();

        @Override
        public void log(UUID caseId, RouteDecision route, TriageResult result, Label llmLabel) {
            lines.add(route.ruleId() + " " + route.route() + " " + llmLabel);
        }
    }

    private final FakeTriage triage = new FakeTriage();
    private final RecordingAgreement agreement = new RecordingAgreement();

    private static TriageProperties properties(Mode mode) {
        return new TriageProperties(mode, "typesafe/jev-1.14", 0.80, 0.95, 0.30, "http://localhost:0",
                Duration.ofSeconds(8));
    }

    private TriageStage stage(Mode mode) {
        return new TriageStage(triage, new TriageRouter(properties(mode)), agreement, properties(mode), CLOCK);
    }

    private static CaseForm form(int daysAgo, String reason) {
        return new CaseForm(CaseType.RETURN, Category.SMARTPHONE, "Galaxy S23", LocalDate.now(CLOCK).minusDays(daysAgo),
                reason);
    }

    private static TriageResult safety(double p) {
        TriageAnswer a = new TriageAnswer("safety_hazard", TriageAnswer.Type.NOUL, p >= 0.5, p, null,
                Map.of("true", p, "false", 1 - p), Math.max(p, 1 - p), true, TriageAnswer.Band.LOW, false);
        return new TriageResult("typesafe/jev-1.14", Map.of("safety_hazard", a), 600, null, TriageResult.Status.OK);
    }

    private static CaseSession session(CaseForm form) {
        return new CaseSession(UUID.randomUUID(), form, CLOCK.instant());
    }

    @Test
    void off_makesNoCallAndLogsNoAgreement() {
        TriageStage stage = stage(Mode.OFF);

        TriageStage.Outcome outcome = stage.run(form(10, "Zmiana zdania"), JPEG);
        stage.logAgreement(session(form(10, "Zmiana zdania")), outcome, Label.ACCEPTED_PRELIMINARY);

        assertThat(triage.calls).isZero();
        assertThat(outcome.result()).isEmpty();
        assertThat(agreement.lines).isEmpty();
    }

    @Test
    void assist_safetyProbability_isWouldBeT1_butAlwaysTheLlmPath() {
        triage.next = safety(0.6);
        TriageStage stage = stage(Mode.ASSIST);
        CaseForm form = form(45, "Zmiana zdania");

        TriageStage.Outcome outcome = stage.run(form, JPEG);
        stage.logAgreement(session(form), outcome, Label.ESCALATED);

        assertThat(triage.calls).isEqualTo(1);
        assertThat(outcome.result()).contains(triage.next);
        assertThat(agreement.lines).containsExactly("T1 LLM_PATH ESCALATED");
    }

    @Test
    void fastPathMode_isRoutedAsAssist_becauseTheFastPathIsDisabledInThePoc() {
        triage.next = safety(0.6);
        TriageStage stage = stage(Mode.FAST_PATH);
        CaseForm form = form(10, "Zmiana zdania");

        stage.logAgreement(session(form), stage.run(form, JPEG), Label.ESCALATED);

        assertThat(agreement.lines).containsExactly("T1 LLM_PATH ESCALATED");
    }

    @Test
    void safetyLexiconInTheReason_isWouldBeT1() {
        triage.next = new TriageResult("typesafe/jev-1.14", Map.of(), 600, null, TriageResult.Status.OK);
        TriageStage stage = stage(Mode.ASSIST);
        CaseForm form = form(10, "Bateria spuchła");

        stage.logAgreement(session(form), stage.run(form, JPEG), Label.ESCALATED);

        assertThat(agreement.lines).containsExactly("T1 LLM_PATH ESCALATED");
    }

    @Test
    void failedTriage_isRuleT0WithoutSignals() {
        triage.next = new TriageResult(null, Map.of(), 8000, null, TriageResult.Status.TIMEOUT);
        TriageStage stage = stage(Mode.ASSIST);
        CaseForm form = form(10, null);

        TriageStage.Outcome outcome = stage.run(form, JPEG);
        stage.logAgreement(session(form), outcome, Label.ACCEPTED_PRELIMINARY);

        assertThat(agreement.lines).containsExactly("T0 LLM_PATH ACCEPTED_PRELIMINARY");
    }

    @Test
    void anUnexpectedFailureNeverReachesTheTurn() {
        triage.fail = new IllegalStateException("boom");
        TriageStage stage = stage(Mode.ASSIST);

        TriageStage.Outcome outcome = stage.run(form(10, "Zmiana zdania"), JPEG);
        stage.logAgreement(session(form(10, "Zmiana zdania")), outcome, Label.REJECTED);

        assertThat(outcome.result()).isEmpty();
        assertThat(agreement.lines).isEmpty();
    }
}
