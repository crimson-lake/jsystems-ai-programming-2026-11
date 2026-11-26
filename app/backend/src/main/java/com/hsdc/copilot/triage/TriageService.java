package com.hsdc.copilot.triage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.hsdc.copilot.cases.CaseForm;
import com.hsdc.copilot.config.TriageProperties;
import com.hsdc.copilot.decision.CaseFacts;
import com.hsdc.copilot.llm.AiCallContext;
import com.hsdc.copilot.llm.AiStep;
import com.hsdc.copilot.triage.DecisionsApiClient.DecisionsResponse;

/**
 * Triage of the form photo with the decisions model (ADR-004 sections 3.2-3.4). Builds the state (form facts and
 * the photo reference), calls {@link DecisionsApiClient} and returns a {@link TriageResult}. Never throws: a failure
 * is {@code UNAVAILABLE} or {@code TIMEOUT}, {@code mode=off} is {@code DISABLED} without a call. Writes one
 * {@code ai.call step=TRIAGE} line per call (ADR-001 section 3.6), never with content.
 */
@Service
public class TriageService {

    private static final Logger log = LoggerFactory.getLogger(TriageService.class);

    private final DecisionsApiClient client;
    private final TriageQuestionSet questions;
    private final TriageProperties properties;

    public TriageService(DecisionsApiClient client, TriageQuestionSet questions, TriageProperties properties) {
        this.client = client;
        this.questions = questions;
        this.properties = properties;
    }

    public TriageResult triage(CaseForm form, CaseFacts facts, byte[] photo) {
        if (properties.mode() == null || properties.mode() == TriageProperties.Mode.OFF) {
            return result(null, Map.of(), 0, null, TriageResult.Status.DISABLED);
        }
        if (!DecisionsApiClient.isSupported(photo)) {
            log.info("Triage skipped: the decisions model accepts only PNG and JPEG photos");
            return result(null, Map.of(), 0, null, TriageResult.Status.UNAVAILABLE);
        }
        String reason = form.reason() == null ? "" : form.reason();
        List<TriageQuestion> asked = questions.forCase(form.caseType(), reason);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("caseType", form.caseType().name());
        state.put("formCategory", form.category().name());
        state.put("model", form.model());
        state.put("daysSincePurchase", facts.daysSincePurchase());
        state.put("reason", reason);

        long start = System.nanoTime();
        return AiCallContext.withStep(AiStep.TRIAGE, () -> {
            try {
                DecisionsResponse response = client.ask(properties.model(), state, photo, asked);
                long latency = elapsedMs(start);
                logCall("OK", latency, response.inputTokens(), response.outputTokens());
                return result(response.model(), response.answers(), latency, response.cost(), TriageResult.Status.OK);
            }
            catch (TriageUnavailableException e) {
                long latency = elapsedMs(start);
                logCall(e.isTimeout() ? "TIMEOUT" : "ERROR", latency, 0, 0);
                log.warn("Triage unavailable ({}); continuing without signals", e.getMessage());
                return result(null, Map.of(), latency,
                        null, e.isTimeout() ? TriageResult.Status.TIMEOUT : TriageResult.Status.UNAVAILABLE);
            }
            catch (RuntimeException e) {
                long latency = elapsedMs(start);
                logCall("ERROR", latency, 0, 0);
                log.warn("Triage failed unexpectedly ({}); continuing without signals", e.getClass().getSimpleName());
                return result(null, Map.of(), latency, null, TriageResult.Status.UNAVAILABLE);
            }
        });
    }

    private static TriageResult result(String model, Map<String, TriageAnswer> answers, long latencyMs, Double cost,
            TriageResult.Status status) {
        return new TriageResult(model, answers, latencyMs, cost, status);
    }

    private void logCall(String status, long latencyMs, long in, long out) {
        String step = Optional.ofNullable(MDC.get(AiCallContext.STEP_KEY)).orElse("UNKNOWN");
        String caseId = Optional.ofNullable(MDC.get(AiCallContext.CASE_ID_KEY)).orElse("-");
        log.info("ai.call step={} caseId={} model={} status={} latencyMs={} inTokens={} outTokens={}", step, caseId,
                properties.model(), status, latencyMs, in, out);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
