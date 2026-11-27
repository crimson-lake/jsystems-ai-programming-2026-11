package com.hsdc.copilot.triage;

import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.hsdc.copilot.decision.DecisionResult.Label;

/**
 * The {@code assist}-mode experiment log (ADR-004 section 3.2): one structured line per case with the would-be
 * fast-path rule and label, the LLM label, their agreement and the per-question confidence. No customer text, no
 * image, no answer values beyond the confidence.
 */
@Component
public class TriageAgreementLogger {

    private static final Logger log = LoggerFactory.getLogger(TriageAgreementLogger.class);

    public void log(UUID caseId, RouteDecision route, TriageResult result, Label llmLabel) {
        Label fastPathLabel = FastPathRuleTable.label(route.ruleId());
        boolean eligible = fastPathLabel != null;
        String confidence = result == null || result.answers() == null ? "{}"
                : result.answers().values().stream()
                    .map(a -> a.key() + "=" + (a.refused() ? "REFUSED" : String.format(Locale.ROOT, "%.2f", a.confidence())))
                    .collect(Collectors.joining(", ", "{", "}"));
        log.info("triage.agreement caseId={} ruleId={} fastPathEligible={} fastPathLabel={} llmLabel={} agree={} "
                + "model={} status={} confidence={}", caseId, route.ruleId(), eligible,
                eligible ? fastPathLabel : "-", llmLabel, eligible ? fastPathLabel == llmLabel : "-",
                result == null || result.model() == null ? "-" : result.model(),
                result == null ? "-" : result.status(), confidence);
    }
}
