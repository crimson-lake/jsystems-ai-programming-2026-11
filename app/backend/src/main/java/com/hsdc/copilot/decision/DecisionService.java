package com.hsdc.copilot.decision;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.hsdc.copilot.analysis.ImageAnalysis;
import com.hsdc.copilot.cases.CaseSession;
import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.cases.ChatMessage;
import com.hsdc.copilot.cases.TurnInput;
import com.hsdc.copilot.config.OpenRouterProperties;
import com.hsdc.copilot.config.TriageProperties;
import com.hsdc.copilot.decision.DecisionResult.ReasonCode;
import com.hsdc.copilot.decision.DecisionResult.SuggestOtherCaseType;
import com.hsdc.copilot.llm.AiCallContext;
import com.hsdc.copilot.llm.AiStep;
import com.hsdc.copilot.llm.CancelToken;
import com.hsdc.copilot.llm.Deadline;
import com.hsdc.copilot.llm.LlmGateway;
import com.hsdc.copilot.llm.LlmRequest;
import com.hsdc.copilot.llm.LlmRequest.InputItem;
import com.hsdc.copilot.llm.LlmTimeoutException;
import com.hsdc.copilot.llm.LlmUnavailableException;
import com.hsdc.copilot.llm.ReasoningEffort;
import com.hsdc.copilot.policy.PolicyRepository;
import com.hsdc.copilot.prompts.PromptRepository;
import com.hsdc.copilot.triage.TriageAnswer;
import com.hsdc.copilot.triage.TriageResult;

import tools.jackson.databind.json.JsonMapper;

/**
 * Decision pipeline of one turn (ADR-001 section 3.4): structured decision, G6 (with at most one corrective re-ask),
 * G1-G5, then the streamed message through {@link RuleIdStreamFilter}.
 */
@Service
public class DecisionService {

    private static final Logger log = LoggerFactory.getLogger(DecisionService.class);

    static final int MAX_TEXT = 300;
    static final Duration REASK_MAX = Duration.ofSeconds(15);
    static final String SAFETY_KEY = "safety_hazard";

    /** First message after the form, or a chat reply. */
    public enum MessageKind {
        FIRST, REPLY
    }

    private final LlmGateway gateway;
    private final PromptRepository prompts;
    private final PolicyRepository policies;
    private final Guardrails guardrails;
    private final CaseContextAssembler context;
    private final OpenRouterProperties properties;
    private final TriageProperties triage;
    private final JsonMapper json;
    private final Duration decisionTimeout;
    private final Duration streamTimeout;

    public DecisionService(LlmGateway gateway, PromptRepository prompts, PolicyRepository policies,
            Guardrails guardrails, CaseContextAssembler context, OpenRouterProperties properties,
            TriageProperties triage, JsonMapper json,
            @Value("${app.timeouts.decision:20s}") Duration decisionTimeout,
            @Value("${app.timeouts.stream:60s}") Duration streamTimeout) {
        this.gateway = gateway;
        this.prompts = prompts;
        this.policies = policies;
        this.guardrails = guardrails;
        this.context = context;
        this.properties = properties;
        this.triage = triage;
        this.json = json;
        this.decisionTimeout = decisionTimeout;
        this.streamTimeout = streamTimeout;
    }

    /**
     * Makes the validated decision for the turn. {@code session} is the working copy: it already holds the analysis
     * of this turn's photo, but not the turn's user message, which comes in {@code input}.
     */
    public Decision decide(CaseSession session, TurnInput input, Deadline deadline) {
        CaseType type = session.getCaseType();
        Set<String> policyIds = policies.ruleIds(type);
        String instructions = prompts.render(type == CaseType.RETURN ? "decision-return" : "decision-complaint",
                Map.of("policy", policies.text(type)));
        List<InputItem> items = baseItems(session);
        if (input != null && hasText(input.text())) {
            items.add(context.customerMessage(input.text(), input.photoJpeg() != null));
        }

        DecisionResult result = sanitize(AiCallContext.withStep(AiStep.DECISION,
                () -> gateway.structured(request(instructions, items, deadline.callTimeout(decisionTimeout)),
                        DecisionResult.class)));

        RuleIdValidator.Split split = RuleIdValidator.split(result.ruleIds(), policyIds);
        if (!split.invalid().isEmpty()) {
            log.warn("Decision cited {} rule ID(s) outside the policy", split.invalid().size());
            if (RuleIdValidator.reAskAllowed(deadline.remaining())) {
                result = reAsk(instructions, items, result, split, policyIds, deadline).orElse(result);
            }
        }
        RuleIdValidator.Validated validated = RuleIdValidator.dropUnknown(result, type, policyIds);

        List<ImageAnalysis> analyses = session.getAnalyses();
        ImageAnalysis latest = analyses.isEmpty() ? null : analyses.get(analyses.size() - 1);
        Guardrails.Input in = new Guardrails.Input(type, session.getCurrentDecision() == null, List.copyOf(analyses),
                latest, triageSafetyProbability(session.getTriage()), triage.safetyProbability(),
                customerTexts(session, input), context.facts(session), validated, session.getCurrentDecision());
        Decision decision = guardrails.apply(in);
        if (decision.overriddenBy() != null) {
            log.info("Decision overridden by {} label={}", decision.overriddenBy(), decision.label());
        }
        return decision;
    }

    /**
     * Streams the customer message for {@code decision}. Deltas pass {@link RuleIdStreamFilter} before
     * {@code onDelta}; the returned text is the filtered text that is stored in history.
     */
    public String streamMessage(CaseSession session, Decision decision, MessageKind kind, Consumer<String> onDelta,
            CancelToken cancelToken) {
        CaseType type = session.getCaseType();
        String instructions = prompts.render(kind == MessageKind.FIRST ? "message-first" : "message-reply",
                Map.of("policy", policies.text(type)));
        List<InputItem> items = baseItems(session);
        items.add(context.decision(decision));
        LlmRequest request = new LlmRequest(properties.decisionModel(), instructions, items, ReasoningEffort.LOW, null,
                streamTimeout);
        RuleIdStreamFilter filter = new RuleIdStreamFilter(session.getCaseId(), decision.ruleIds());
        AiCallContext.withStep(AiStep.STREAM, () -> gateway.stream(request, delta -> {
            String out = filter.accept(delta);
            if (!out.isEmpty()) {
                onDelta.accept(out);
            }
        }, cancelToken));
        String rest = filter.flush();
        if (!rest.isEmpty()) {
            onDelta.accept(rest);
        }
        return filter.filteredText();
    }

    // ------------------------------------------------------------------ helpers

    private List<InputItem> baseItems(CaseSession session) {
        List<InputItem> items = new ArrayList<>();
        items.add(context.caseFacts(session, session.getTriage(), true));
        context.customerReason(session).ifPresent(items::add);
        items.addAll(context.history(session));
        return items;
    }

    private LlmRequest request(String instructions, List<InputItem> items, Duration timeout) {
        ReasoningEffort effort = Optional.ofNullable(properties.decisionReasoningEffort()).orElse(ReasoningEffort.MEDIUM);
        return new LlmRequest(properties.decisionModel(), instructions, items, effort, DecisionResult.class, timeout);
    }

    /** One corrective re-ask (G6). A failed re-ask falls back to dropping the unknown IDs. */
    private Optional<DecisionResult> reAsk(String instructions, List<InputItem> items, DecisionResult first,
            RuleIdValidator.Split split, Set<String> policyIds, Deadline deadline) {
        List<InputItem> reAskItems = new ArrayList<>(items);
        reAskItems.add(new InputItem(InputItem.Role.ASSISTANT, json.writeValueAsString(first), null));
        reAskItems.add(new InputItem(InputItem.Role.USER, "CORRECTION (system, trusted)\nThese ruleIds are not in "
                + "the applied policy: " + split.invalid() + ". Valid IDs: " + policyIds
                + ". Return the complete decision again, citing only valid IDs.", null));
        try {
            DecisionResult again = AiCallContext.withStep(AiStep.DECISION_REASK,
                    () -> gateway.structured(request(instructions, reAskItems, deadline.callTimeout(REASK_MAX)),
                            DecisionResult.class));
            return Optional.of(sanitize(again));
        }
        catch (LlmUnavailableException | LlmTimeoutException e) {
            log.warn("Rule-ID re-ask failed ({}); unknown IDs are dropped", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Null-safety and the 300-char limits; a missing label is an unusable response. */
    static DecisionResult sanitize(DecisionResult r) {
        if (r == null || r.label() == null) {
            throw new LlmUnavailableException("Decision response has no label");
        }
        return new DecisionResult(r.label(), r.ruleIds() == null ? List.of() : r.ruleIds(),
                Optional.ofNullable(r.reasonCode()).orElse(ReasonCode.OTHER),
                Optional.ofNullable(r.suggestOtherCaseType()).orElse(SuggestOtherCaseType.NONE),
                cut(r.changeReasonPl()), cut(r.missingInfoPl()));
    }

    static Double triageSafetyProbability(Optional<TriageResult> triage) {
        return triage.filter(t -> t.status() == TriageResult.Status.OK && t.answers() != null)
            .map(t -> t.answers().get(SAFETY_KEY))
            .filter(a -> !a.refused())
            .map(TriageAnswer::probabilityTrue)
            .orElse(null);
    }

    private static List<String> customerTexts(CaseSession session, TurnInput input) {
        List<String> texts = new ArrayList<>();
        if (hasText(session.getForm().reason())) {
            texts.add(session.getForm().reason());
        }
        session.getMessages().stream()
            .filter(m -> m.role() == ChatMessage.Role.USER && hasText(m.text()))
            .forEach(m -> texts.add(m.text()));
        if (input != null && hasText(input.text())) {
            texts.add(input.text());
        }
        return texts;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String cut(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
    }
}
