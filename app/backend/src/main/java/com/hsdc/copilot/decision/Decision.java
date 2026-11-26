package com.hsdc.copilot.decision;

import com.hsdc.copilot.decision.DecisionResult.Label;
import com.hsdc.copilot.decision.DecisionResult.ReasonCode;
import com.hsdc.copilot.decision.DecisionResult.SuggestOtherCaseType;
import java.util.List;

/**
 * Validated decision after rule-ID validation and guardrails (ADR-001 section 4).
 *
 * @param previousLabel null on the first decision
 * @param overriddenBy  guardrail that set the result (G1...G6), null if none
 */
public record Decision(
        Label label,
        List<String> ruleIds,
        ReasonCode reasonCode,
        SuggestOtherCaseType suggestOtherCaseType,
        String changeReasonPl,
        String missingInfoPl,
        boolean safety,
        boolean changed,
        Label previousLabel,
        String overriddenBy) {
}
