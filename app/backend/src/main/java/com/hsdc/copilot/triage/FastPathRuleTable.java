package com.hsdc.copilot.triage;

import java.util.List;

import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.decision.DecisionResult;
import com.hsdc.copilot.decision.DecisionResult.Label;
import com.hsdc.copilot.decision.DecisionResult.ReasonCode;
import com.hsdc.copilot.decision.DecisionResult.SuggestOtherCaseType;

/**
 * Pure mapping of the fast-path rules T1-T5 to a label and rule IDs (ADR-004 section 3.5). Label and rule IDs only:
 * the fast path is disabled in the PoC (D-T2 amendment), so there are no templates and no message.
 */
public final class FastPathRuleTable {

    static final String NEW_UNUSED = "NEW_UNUSED";

    private FastPathRuleTable() {
    }

    /** The would-be fast-path label of a rule, or null for T0, T6 and T7. */
    public static Label label(String ruleId) {
        return switch (ruleId == null ? "" : ruleId) {
            case "T1" -> Label.ESCALATED;
            case "T2" -> Label.REJECTED;
            case "T3", "T4" -> Label.NEEDS_MORE_INFO;
            case "T5" -> Label.ACCEPTED_PRELIMINARY;
            default -> null;
        };
    }

    /** The fast-path decision of rule T1-T5; {@code condition} is the T5 condition label. */
    public static DecisionResult decision(String ruleId, CaseType type, String condition) {
        boolean ret = type == CaseType.RETURN;
        return switch (ruleId) {
            case "T1" -> result(Label.ESCALATED, List.of(ret ? "R7.1" : "C7.1"), ReasonCode.SAFETY);
            case "T2" -> result(Label.REJECTED, List.of(ret ? "R2.3" : "C2.3"), ReasonCode.OUTSIDE_WINDOW);
            case "T3" -> result(Label.NEEDS_MORE_INFO, List.of(), ReasonCode.PHOTO_UNUSABLE);
            case "T4" -> result(Label.NEEDS_MORE_INFO, List.of(), ReasonCode.CATEGORY_MISMATCH);
            case "T5" -> result(Label.ACCEPTED_PRELIMINARY,
                    NEW_UNUSED.equals(condition) ? List.of("R2.2", "R3.1", "R5.3") : List.of("R2.2", "R3.3", "R5.3"),
                    ReasonCode.RESELLABLE);
            default -> throw new IllegalArgumentException("Not a fast-path rule: " + ruleId);
        };
    }

    private static DecisionResult result(Label label, List<String> ruleIds, ReasonCode reason) {
        return new DecisionResult(label, ruleIds, reason, SuggestOtherCaseType.NONE, "", "");
    }
}
