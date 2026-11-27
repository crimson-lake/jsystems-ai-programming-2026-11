package com.hsdc.copilot.triage;

import static com.hsdc.copilot.triage.TriageTestSupport.choice;
import static com.hsdc.copilot.triage.TriageTestSupport.noul;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.hsdc.copilot.decision.DecisionResult.Label;
import com.hsdc.copilot.triage.RouteDecision.Route;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** ADR-004 section 3.2: one structured agreement line per case, no customer text. */
class TriageAgreementLoggerTest {

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final TriageAgreementLogger logger = new TriageAgreementLogger();

    @BeforeEach
    void attach() {
        logs.start();
        ((Logger) LoggerFactory.getLogger(TriageAgreementLogger.class)).addAppender(logs);
    }

    @AfterEach
    void detach() {
        ((Logger) LoggerFactory.getLogger(TriageAgreementLogger.class)).detachAppender(logs);
    }

    private static TriageResult result() {
        Map<String, TriageAnswer> answers = new LinkedHashMap<>();
        answers.put("photo_usable", noul("photo_usable", 0.79));
        answers.put("device_category", choice("device_category", "SMARTPHONE", 1.0));
        answers.put("safety_hazard", TriageTestSupport.refused("safety_hazard", TriageAnswer.Type.NOUL));
        return new TriageResult("typesafe/jev-1.14-20261014", answers, 1049, 0.0, TriageResult.Status.OK);
    }

    @Test
    void eligibleCase_logsWouldBeLabel_llmLabel_agreement_andConfidences() {
        UUID caseId = UUID.randomUUID();
        logger.log(caseId, new RouteDecision(Route.LLM_PATH, "T5", null, List.of()), result(), Label.REJECTED);

        assertThat(logs.list).hasSize(1);
        String line = logs.list.get(0).getFormattedMessage();
        assertThat(line).isEqualTo("triage.agreement caseId=" + caseId + " ruleId=T5 fastPathEligible=true "
                + "fastPathLabel=ACCEPTED_PRELIMINARY llmLabel=REJECTED agree=false "
                + "model=typesafe/jev-1.14-20261014 status=OK "
                + "confidence={photo_usable=0.79, device_category=1.00, safety_hazard=REFUSED}");
    }

    @Test
    void notEligible_logsNoFastPathLabel() {
        logger.log(UUID.randomUUID(), new RouteDecision(Route.LLM_PATH, "T6", null, List.of("photo_usable")),
                result(), Label.ACCEPTED_PRELIMINARY);

        String line = logs.list.get(0).getFormattedMessage();
        assertThat(line).contains("ruleId=T6 fastPathEligible=false fastPathLabel=- llmLabel=ACCEPTED_PRELIMINARY "
                + "agree=- ");
    }

    @Test
    void triageUnavailable_stillLogsOneLine() {
        logger.log(UUID.randomUUID(), new RouteDecision(Route.LLM_PATH, "T0", null, List.of()),
                new TriageResult(null, Map.of(), 8000, null, TriageResult.Status.TIMEOUT), Label.ESCALATED);

        assertThat(logs.list.get(0).getFormattedMessage()).contains("ruleId=T0", "model=- status=TIMEOUT",
                "confidence={}");
    }
}
