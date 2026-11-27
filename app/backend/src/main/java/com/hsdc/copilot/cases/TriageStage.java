package com.hsdc.copilot.cases;

import java.time.Clock;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.hsdc.copilot.config.TriageProperties;
import com.hsdc.copilot.config.TriageProperties.Mode;
import com.hsdc.copilot.decision.CaseFacts;
import com.hsdc.copilot.decision.DecisionResult.Label;
import com.hsdc.copilot.decision.SafetyLexicon;
import com.hsdc.copilot.triage.RouteDecision;
import com.hsdc.copilot.triage.TriageAgreementLogger;
import com.hsdc.copilot.triage.TriageResult;
import com.hsdc.copilot.triage.TriageRouter;
import com.hsdc.copilot.triage.TriageService;

/**
 * The triage stage of the first submission (ADR-004 sections 3.1, 3.5): the decisions-model call on the form photo,
 * the router (for the agreement log only) and the agreement line after the LLM decision. Chat photos are never
 * triaged. The PoC ships {@code assist} only (D-T2 amendment): every case takes the LLM path, also when the mode is
 * configured as {@code fast-path}. Never throws: a failure means no signals and no agreement line.
 */
@Component
public class TriageStage {

    private static final Logger log = LoggerFactory.getLogger(TriageStage.class);

    /** Triage result and would-be route of one case; both empty when triage is off. */
    public record Outcome(Optional<TriageResult> result, Optional<RouteDecision> route) {

        static final Outcome NONE = new Outcome(Optional.empty(), Optional.empty());
    }

    private final TriageService service;
    private final TriageRouter router;
    private final TriageAgreementLogger agreement;
    private final TriageProperties properties;
    private final Clock clock;

    public TriageStage(TriageService service, TriageRouter router, TriageAgreementLogger agreement,
            TriageProperties properties, Clock clock) {
        this.service = service;
        this.router = router;
        this.agreement = agreement;
        this.properties = properties;
        this.clock = clock;
    }

    /** Triages the first photo; {@code mode=off} makes no call (TAC-004-01). */
    public Outcome run(CaseForm form, byte[] photo) {
        Mode mode = properties.mode();
        if (mode == null || mode == Mode.OFF) {
            return Outcome.NONE;
        }
        try {
            CaseFacts facts = CaseFacts.of(form.purchaseDate(), clock);
            TriageResult result = service.triage(form, facts, photo);
            boolean lexicon = form.reason() != null && SafetyLexicon.matches(form.reason());
            // The fast path is disabled in the PoC: the route is computed as in assist mode and only logged.
            RouteDecision route = router.route(result, form, facts, Mode.ASSIST, lexicon);
            return new Outcome(Optional.ofNullable(result), Optional.of(route));
        }
        catch (RuntimeException e) {
            log.warn("Triage stage failed ({}); continuing without signals", e.getClass().getSimpleName());
            return Outcome.NONE;
        }
    }

    /** One {@code triage.agreement} line with the final (post-guardrail) label of the first decision. */
    public void logAgreement(CaseSession session, Outcome outcome, Label finalLabel) {
        if (outcome.route().isEmpty()) {
            return;
        }
        try {
            agreement.log(session.getCaseId(), outcome.route().get(), outcome.result().orElse(null), finalLabel);
        }
        catch (RuntimeException e) {
            log.warn("Triage agreement log failed ({})", e.getClass().getSimpleName());
        }
    }
}
