package com.hsdc.copilot.decision;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.hsdc.copilot.analysis.ImageAnalysis;
import com.hsdc.copilot.cases.CaseForm;
import com.hsdc.copilot.cases.CaseSession;
import com.hsdc.copilot.cases.ChatMessage;
import com.hsdc.copilot.llm.LlmRequest.InputItem;
import com.hsdc.copilot.llm.LlmRequest.InputItem.Role;
import com.hsdc.copilot.triage.TriageAnswer;
import com.hsdc.copilot.triage.TriageResult;

import tools.jackson.databind.json.JsonMapper;

/**
 * Stateless-replay context assembly (ADR-001 section 3.3). Every AI call is rebuilt from the {@link CaseSession}:
 * CASE FACTS first, then CUSTOMER REASON, then the history, then (message steps only) the DECISION item.
 *
 * <p>Triage seam (T29): {@code triageSignals} are taken from the {@code triage} argument; while triage is off it is
 * empty and the key is absent.
 */
@Component
public class CaseContextAssembler {

    public static final String CASE_FACTS_LABEL = "CASE FACTS (system-computed, trusted)";
    public static final String CUSTOMER_REASON_LABEL = "CUSTOMER REASON (untrusted)";
    public static final String CUSTOMER_MESSAGE_LABEL = "CUSTOMER MESSAGE (untrusted)";
    public static final String DECISION_LABEL = "DECISION (final, trusted)";
    public static final String PHOTO_ONLY_MESSAGE = "CUSTOMER MESSAGE (system-generated, trusted): photo attached, no text";
    public static final int PHOTO_LIMIT = 3;

    private final Clock clock;
    private final JsonMapper json;

    public CaseContextAssembler(Clock clock, JsonMapper json) {
        this.clock = clock;
        this.json = json;
    }

    public CaseFacts facts(CaseSession session) {
        return CaseFacts.of(session.getForm().purchaseDate(), clock);
    }

    /** The CASE FACTS item; {@code withAnalyses} is false for the vision step (it analyses the new photo). */
    public InputItem caseFacts(CaseSession session, Optional<TriageResult> triage, boolean withAnalyses) {
        CaseForm form = session.getForm();
        CaseFacts facts = facts(session);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("caseType", form.caseType());
        map.put("category", form.category());
        map.put("model", form.model());
        map.put("purchaseDate", form.purchaseDate().toString());
        map.put("today", facts.today().toString());
        map.put("daysSincePurchase", facts.daysSincePurchase());
        map.put("returnWindowExceeded", facts.returnWindowExceeded());
        map.put("complaintPeriodExceeded", facts.complaintPeriodExceeded());
        map.put("additionalPhotoCount", session.getAdditionalPhotoCount());
        map.put("additionalPhotoLimit", PHOTO_LIMIT);
        triage.filter(t -> t.status() == TriageResult.Status.OK && t.answers() != null && !t.answers().isEmpty())
            .ifPresent(t -> map.put("triageSignals", triageSignals(t)));
        if (withAnalyses) {
            map.put("imageAnalyses", analyses(session));
        }
        return text(Role.USER, CASE_FACTS_LABEL + "\n" + json.writeValueAsString(map));
    }

    /** The CUSTOMER REASON item, or empty when the form has no reason. */
    public Optional<InputItem> customerReason(CaseSession session) {
        String reason = session.getForm().reason();
        if (reason == null || reason.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(text(Role.USER, CUSTOMER_REASON_LABEL + "\n" + reason));
    }

    /** Prior turns in order: customer as USER (labelled untrusted), assistant body as ASSISTANT. */
    public List<InputItem> history(CaseSession session) {
        List<InputItem> items = new ArrayList<>();
        for (ChatMessage message : session.getMessages()) {
            if (message.role() == ChatMessage.Role.ASSISTANT) {
                items.add(text(Role.ASSISTANT, message.text()));
            }
            else {
                items.add(customerMessage(message.text(), message.hasPhoto()));
            }
        }
        return items;
    }

    /**
     * A customer chat message. A photo-only message (no text) becomes the trusted marker
     * {@value #PHOTO_ONLY_MESSAGE}, the same in the decision and the message steps.
     */
    public InputItem customerMessage(String text, boolean hasPhoto) {
        if (hasPhoto && (text == null || text.isBlank())) {
            return text(Role.USER, PHOTO_ONLY_MESSAGE);
        }
        String body = text == null ? "" : text;
        return text(Role.USER, CUSTOMER_MESSAGE_LABEL + (hasPhoto ? " [photo attached]" : "") + "\n" + body);
    }

    /** The final DECISION item of a message step. */
    public InputItem decision(Decision decision) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("label", decision.label());
        map.put("ruleIds", decision.ruleIds());
        map.put("reasonCode", decision.reasonCode());
        map.put("suggestOtherCaseType", decision.suggestOtherCaseType());
        map.put("missingInfoPl", decision.missingInfoPl());
        map.put("safety", decision.safety());
        map.put("changed", decision.changed());
        map.put("previousLabel", decision.previousLabel());
        map.put("changeReasonPl", decision.changeReasonPl());
        return text(Role.USER, DECISION_LABEL + "\n" + json.writeValueAsString(map));
    }

    private List<Map<String, Object>> analyses(CaseSession session) {
        List<Map<String, Object>> list = new ArrayList<>();
        List<ImageAnalysis> analyses = session.getAnalyses();
        for (int i = 0; i < analyses.size(); i++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", i);
            entry.put("time", analysisTime(session, i).toString());
            entry.put("analysis", analyses.get(i));
            list.add(entry);
        }
        return list;
    }

    /** The time of the chat message that carried the photo; the first photo came with the form. */
    private static Instant analysisTime(CaseSession session, int index) {
        return session.getMessages().stream()
            .filter(m -> m.analysisIndex() != null && m.analysisIndex() == index)
            .map(ChatMessage::createdAt)
            .findFirst()
            .orElse(session.getCreatedAt());
    }

    private static List<Map<String, Object>> triageSignals(TriageResult triage) {
        List<Map<String, Object>> signals = new ArrayList<>();
        for (TriageAnswer answer : triage.answers().values()) {
            if (answer.refused()) {
                continue;
            }
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("key", answer.key());
            s.put("answer", answer.value());
            s.put("confidence", answer.confidence());
            s.put("band", answer.band());
            signals.add(s);
        }
        return signals;
    }

    private static InputItem text(Role role, String text) {
        return new InputItem(role, text, null);
    }
}
