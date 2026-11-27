package com.hsdc.copilot.triage;

import static com.hsdc.copilot.triage.TriageTestSupport.JSON;
import static com.hsdc.copilot.triage.TriageTestSupport.capture;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.hsdc.copilot.cases.CaseForm;
import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.cases.Category;
import com.hsdc.copilot.config.TriageProperties.Mode;
import com.hsdc.copilot.decision.CaseFacts;
import com.hsdc.copilot.llm.AiCallContext;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** ADR-004 section 5: {@code TriageService.triage} never throws and logs one {@code ai.call step=TRIAGE} line. */
class TriageServiceTest {

    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1 };
    private static final byte[] WEBP = { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 1 };
    private static final String REASON = "Zmiana zdania.";

    /** Answers from a recorded capture body, or throws the configured failure. */
    static final class FakeClient extends DecisionsApiClient {
        final List<Map<String, Object>> facts = new ArrayList<>();
        final List<List<TriageQuestion>> questions = new ArrayList<>();
        RuntimeException failure;

        FakeClient() {
            super(TriageTestSupport.properties(Mode.ASSIST, "http://localhost:0/api/alpha/decisions"),
                    TriageTestSupport.openRouter(), JSON);
        }

        @Override
        public DecisionsResponse ask(String model, Map<String, Object> stateFacts, byte[] photo,
                List<TriageQuestion> qs) {
            facts.add(stateFacts);
            questions.add(qs);
            if (failure != null) {
                throw failure;
            }
            return parse(capture("decisions-jev-return-phone-1.json"), qs, 0.80, 0.95, JSON);
        }
    }

    private final FakeClient client = new FakeClient();
    private final TriageQuestionSet questions = new TriageQuestionSet(JSON);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        ((Logger) LoggerFactory.getLogger(TriageService.class)).addAppender(logs);
        MDC.put(AiCallContext.CASE_ID_KEY, "case-1");
    }

    @AfterEach
    void detach() {
        ((Logger) LoggerFactory.getLogger(TriageService.class)).detachAppender(logs);
        MDC.clear();
    }

    private TriageService service(Mode mode) {
        return new TriageService(client, questions, TriageTestSupport.properties(mode, "x"));
    }

    private static CaseForm form() {
        return new CaseForm(CaseType.RETURN, Category.SMARTPHONE, "Galaxy S23", LocalDate.of(2026, 11, 16), REASON);
    }

    private static CaseFacts facts() {
        return new CaseFacts(LocalDate.of(2026, 11, 26), 10, false, false);
    }

    private List<String> aiCallLines() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).filter(m -> m.startsWith("ai.call")).toList();
    }

    @Test
    void modeOff_makesNoCall() {
        TriageResult result = service(Mode.OFF).triage(form(), facts(), JPEG);

        assertThat(result.status()).isEqualTo(TriageResult.Status.DISABLED);
        assertThat(result.answers()).isEmpty();
        assertThat(client.facts).isEmpty();
        assertThat(aiCallLines()).isEmpty();
    }

    @Test
    void assist_mapsTheAnswers_sendsFormFacts_andLogsOneAiCallLineWithoutContent() {
        TriageResult result = service(Mode.ASSIST).triage(form(), facts(), JPEG);

        assertThat(result.status()).isEqualTo(TriageResult.Status.OK);
        assertThat(result.model()).isEqualTo("typesafe/jev-1.14-20261014");
        assertThat(result.costCredits()).isEqualTo(0.0);
        assertThat(result.answers().get("photo_usable").probabilityTrue()).isEqualTo(0.79);
        assertThat(client.facts.get(0)).containsExactly(Map.entry("caseType", "RETURN"),
                Map.entry("formCategory", "SMARTPHONE"), Map.entry("model", "Galaxy S23"),
                Map.entry("daysSincePurchase", 10L), Map.entry("reason", REASON));
        assertThat(client.questions.get(0)).extracting(TriageQuestion::key).contains("reason_reports_defect");

        assertThat(aiCallLines()).hasSize(1);
        String line = aiCallLines().get(0);
        assertThat(line).startsWith("ai.call step=TRIAGE caseId=case-1 model=typesafe/jev-1.14 status=OK latencyMs=")
            .endsWith("inTokens=3922 outTokens=233");
        assertThat(line).doesNotContain(REASON, "Galaxy");
        assertThat(MDC.get(AiCallContext.STEP_KEY)).as("step restored").isNull();
    }

    @Test
    void timeout_isStatusTimeout() {
        client.failure = new TriageUnavailableException("timeout", true);
        TriageResult result = service(Mode.ASSIST).triage(form(), facts(), JPEG);

        assertThat(result.status()).isEqualTo(TriageResult.Status.TIMEOUT);
        assertThat(result.answers()).isEmpty();
        assertThat(aiCallLines()).singleElement().asString().contains("status=TIMEOUT");
    }

    @Test
    void anyOtherFailure_isUnavailable_neverThrown() {
        client.failure = new TriageUnavailableException("HTTP 401", false);
        assertThat(service(Mode.ASSIST).triage(form(), facts(), JPEG).status())
            .isEqualTo(TriageResult.Status.UNAVAILABLE);
        client.failure = new IllegalStateException("bug");
        assertThat(service(Mode.ASSIST).triage(form(), facts(), JPEG).status())
            .isEqualTo(TriageResult.Status.UNAVAILABLE);
        assertThat(aiCallLines()).hasSize(2).allMatch(l -> l.contains("status=ERROR"));
    }

    @Test
    void webpPhoto_isNotSent_jevAcceptsOnlyPngAndJpeg() {
        TriageResult result = service(Mode.ASSIST).triage(form(), facts(), WEBP);

        assertThat(result.status()).isEqualTo(TriageResult.Status.UNAVAILABLE);
        assertThat(client.facts).isEmpty();
    }

    @Test
    void nullReason_isSentAsEmpty_andDropsReasonQuestions() {
        CaseForm noReason = new CaseForm(CaseType.RETURN, Category.SMARTPHONE, "Galaxy S23", LocalDate.of(2026, 11, 16),
                null);
        service(Mode.ASSIST).triage(noReason, facts(), JPEG);

        assertThat(client.facts.get(0)).containsEntry("reason", "");
        assertThat(client.questions.get(0)).extracting(TriageQuestion::key)
            .doesNotContain("description_consistent", "reason_reports_defect");
    }
}
