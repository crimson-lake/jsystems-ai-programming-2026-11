package com.hsdc.copilot.triage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.hsdc.copilot.cases.CaseForm;
import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.cases.Category;
import com.hsdc.copilot.config.TriageProperties;
import com.hsdc.copilot.config.TriageProperties.Mode;
import com.hsdc.copilot.decision.CaseFacts;
import com.hsdc.copilot.triage.RouteDecision.Route;

/**
 * The router table of ADR-004 section 3.5; the first matching row wins. T1-T5 are always evaluated (the agreement
 * log needs the would-be rule), but only {@code mode=fast-path} takes them; {@code assist} always routes to the LLM.
 */
@Component
public class TriageRouter {

    static final String PHOTO_USABLE = "photo_usable";
    static final String DEVICE_CATEGORY = "device_category";
    static final String DAMAGE_VISIBLE = "damage_visible";
    static final String CONDITION = "condition";
    static final String SAFETY_HAZARD = "safety_hazard";
    static final String DESCRIPTION_CONSISTENT = "description_consistent";
    static final String REASON_REPORTS_DEFECT = "reason_reports_defect";
    static final String NOT_A_DEVICE = "NOT_A_DEVICE";

    private static final Set<String> T5_CONDITIONS = Set.of("NEW_UNUSED", "LIGHT_SIGNS_OF_USE");
    private static final Set<Category> NO_FAST_ACCEPT = Set.of(Category.AUDIO, Category.OTHER);

    private final TriageProperties properties;

    public TriageRouter(TriageProperties properties) {
        this.properties = properties;
    }

    /**
     * @param reasonMatchesSafetyLexicon whether the ADR-001 G1 lexicon matches the form reason (computed by the
     *                                   caller, which owns the lexicon)
     */
    public RouteDecision route(TriageResult result, CaseForm form, CaseFacts facts, Mode mode,
            boolean reasonMatchesSafetyLexicon) {
        double high = properties.highConfidence();
        double low = properties.lowConfidence();
        if (mode == null || mode == Mode.OFF || result == null || result.status() != TriageResult.Status.OK
                || result.answers() == null || result.answers().values().stream().anyMatch(TriageAnswer::refused)) {
            return llm("T0", List.of());
        }
        Map<String, TriageAnswer> a = result.answers();
        CaseType type = form.caseType();

        TriageAnswer safety = a.get(SAFETY_HAZARD);
        if (reasonMatchesSafetyLexicon || (safety != null && safety.probabilityTrue() != null
                && safety.probabilityTrue() >= properties.safetyProbability())) {
            return fast("T1", type, null, mode);
        }
        boolean windowExceeded = type == CaseType.RETURN ? facts.returnWindowExceeded() : facts.complaintPeriodExceeded();
        if (windowExceeded) {
            return fast("T2", type, null, mode);
        }
        TriageAnswer usable = a.get(PHOTO_USABLE);
        if (is(usable, false) && atLeast(high, usable)) {
            return fast("T3", type, null, mode);
        }
        TriageAnswer category = a.get(DEVICE_CATEGORY);
        if (category != null && atLeast(high, usable, category)
                && (!form.category().name().equals(category.value()) || NOT_A_DEVICE.equals(category.value()))) {
            return fast("T4", type, null, mode);
        }
        if (t5(a, form, high)) {
            return fast("T5", type, (String) a.get(CONDITION).value(), mode);
        }
        List<String> lowKeys = below(a, low);
        if (!lowKeys.isEmpty()) {
            return llm("T6", lowKeys);
        }
        return llm("T7", below(a, high));
    }

    private static boolean t5(Map<String, TriageAnswer> a, CaseForm form, double high) {
        if (form.caseType() != CaseType.RETURN || NO_FAST_ACCEPT.contains(form.category())) {
            return false;
        }
        TriageAnswer usable = a.get(PHOTO_USABLE);
        TriageAnswer category = a.get(DEVICE_CATEGORY);
        TriageAnswer damage = a.get(DAMAGE_VISIBLE);
        TriageAnswer condition = a.get(CONDITION);
        TriageAnswer safety = a.get(SAFETY_HAZARD);
        TriageAnswer consistent = a.get(DESCRIPTION_CONSISTENT);
        TriageAnswer defect = a.get(REASON_REPORTS_DEFECT);
        if (usable == null || category == null || damage == null || condition == null || safety == null) {
            return false;
        }
        List<TriageAnswer> listed = new ArrayList<>(List.of(usable, category, damage, condition, safety));
        if (consistent != null) {
            listed.add(consistent);
        }
        if (defect != null) {
            listed.add(defect);
        }
        return is(usable, true) && form.category().name().equals(category.value()) && is(damage, false)
                && T5_CONDITIONS.contains(condition.value()) && is(safety, false) && !is(consistent, false)
                && !is(defect, true) && atLeast(high, listed.toArray(TriageAnswer[]::new));
    }

    private static boolean is(TriageAnswer answer, boolean value) {
        return answer != null && Boolean.valueOf(value).equals(answer.value());
    }

    private static boolean atLeast(double threshold, TriageAnswer... answers) {
        for (TriageAnswer answer : answers) {
            if (answer == null || answer.confidence() < threshold) {
                return false;
            }
        }
        return true;
    }

    private static List<String> below(Map<String, TriageAnswer> answers, double threshold) {
        return answers.values().stream().filter(x -> x.confidence() < threshold).map(TriageAnswer::key).toList();
    }

    private static RouteDecision fast(String ruleId, CaseType type, String condition, Mode mode) {
        if (mode == Mode.FAST_PATH) {
            return new RouteDecision(Route.FAST_PATH, ruleId, FastPathRuleTable.decision(ruleId, type, condition),
                    List.of());
        }
        return llm(ruleId, List.of());
    }

    private static RouteDecision llm(String ruleId, List<String> reasons) {
        return new RouteDecision(Route.LLM_PATH, ruleId, null, reasons);
    }
}
