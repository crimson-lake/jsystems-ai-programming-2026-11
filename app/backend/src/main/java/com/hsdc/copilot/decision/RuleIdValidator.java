package com.hsdc.copilot.decision;

import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.decision.DecisionResult.Label;
import com.hsdc.copilot.decision.DecisionResult.ReasonCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** G6: validation of the model's rule IDs against the applied policy (ADR-001 section 3.4 step 2.1). Pure. */
public final class RuleIdValidator {

	/** A corrective re-ask is made only if at least this much of the deadline is left. */
	public static final Duration MIN_REASK_TIME = Duration.ofSeconds(15);
	public static final int MAX_IDS = 5;

	private RuleIdValidator() {
	}

	public record Split(List<String> valid, List<String> invalid) {
	}

	/**
	 * Result of G6.
	 *
	 * @param escalatedByG6 true if no valid ID was left and the label was forced to ESCALATED
	 */
	public record Validated(DecisionResult result, boolean escalatedByG6) {
	}

	public static Split split(List<String> ruleIds, Set<String> policyIds) {
		List<String> valid = new ArrayList<>();
		List<String> invalid = new ArrayList<>();
		for (String id : ruleIds == null ? List.<String>of() : ruleIds) {
			(policyIds.contains(id) ? valid : invalid).add(id);
		}
		return new Split(valid, invalid);
	}

	public static boolean reAskAllowed(Duration remaining) {
		return remaining.compareTo(MIN_REASK_TIME) >= 0;
	}

	/**
	 * Drops unknown IDs (and duplicates, and anything beyond {@value #MAX_IDS}). If none is left the decision becomes
	 * ESCALATED with R6 (return) or C8 (complaint).
	 */
	public static Validated dropUnknown(DecisionResult result, CaseType type, Set<String> policyIds) {
		List<String> kept = split(result.ruleIds(), policyIds).valid().stream().distinct().limit(MAX_IDS).toList();
		if (kept.isEmpty()) {
			String id = type == CaseType.RETURN ? "R6" : "C8";
			return new Validated(new DecisionResult(Label.ESCALATED, List.of(id), ReasonCode.OTHER,
					result.suggestOtherCaseType(), result.changeReasonPl(), result.missingInfoPl()), true);
		}
		return new Validated(new DecisionResult(result.label(), kept, result.reasonCode(),
				result.suggestOtherCaseType(), result.changeReasonPl(), result.missingInfoPl()), false);
	}
}
