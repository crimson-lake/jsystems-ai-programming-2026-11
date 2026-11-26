package com.hsdc.copilot.web.sse;

import com.hsdc.copilot.decision.DecisionResult.Label;
import com.hsdc.copilot.decision.DecisionResult.SuggestOtherCaseType;
import java.util.List;

/** SSE {@code decision}: sent before any text. */
public record DecisionEvent(
        Label label,
        Label previousLabel,
        boolean changed,
        String changeReasonPl,
        boolean safety,
        SuggestOtherCaseType suggestOtherCaseType,
        List<String> ruleIds) {
}
