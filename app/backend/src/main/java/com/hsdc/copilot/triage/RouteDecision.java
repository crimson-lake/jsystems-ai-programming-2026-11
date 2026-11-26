package com.hsdc.copilot.triage;

import com.hsdc.copilot.decision.DecisionResult;
import java.util.List;

/**
 * Router outcome, only logged by the agreement logger (ADR-004 section 4).
 *
 * @param ruleId           T0...T7
 * @param fastPathDecision null unless route is FAST_PATH
 * @param reasons          questions that failed the threshold
 */
public record RouteDecision(Route route, String ruleId, DecisionResult fastPathDecision, List<String> reasons) {

    public enum Route {
        FAST_PATH, LLM_PATH
    }
}
