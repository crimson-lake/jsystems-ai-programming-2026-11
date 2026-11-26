package com.hsdc.copilot.decision;

import com.hsdc.copilot.analysis.ImageAnalysis;
import com.hsdc.copilot.analysis.ImageAnalysis.CauseConfidence;
import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.decision.DecisionResult.Label;
import com.hsdc.copilot.decision.DecisionResult.ReasonCode;
import com.hsdc.copilot.prompts.GuardrailReasons;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Deterministic overrides G1-G5 after G6, plus the revision rule (ADR-001 section 3.4; PRD section 11 precedence).
 * No I/O: everything the rules need is in {@link Input}. The first matching rule wins.
 */
@Component
public class Guardrails {

	static final int MAX_IDS = 5;
	static final int MAX_REASON_LENGTH = 300;

	private final GuardrailReasons reasons;

	public Guardrails(GuardrailReasons reasons) {
		this.reasons = reasons;
	}

	/**
	 * @param firstDecision      true for the decision made from the first submission (G4 applies only then)
	 * @param latestAnalysis     analysis of the most recent photo, may be null
	 * @param triageSafetyProbability P(safety_hazard = true) from triage, null if triage is off or failed
	 * @param safetyThreshold    TRIAGE_SAFETY_PROBABILITY
	 * @param customerTexts      reason and chat messages of the customer (G1 lexicon)
	 * @param validated          output of {@link RuleIdValidator#dropUnknown} (G6)
	 * @param current            the decision before this turn, null on the first decision
	 */
	public record Input(CaseType caseType, boolean firstDecision, List<ImageAnalysis> analyses,
			ImageAnalysis latestAnalysis, Double triageSafetyProbability, double safetyThreshold,
			List<String> customerTexts, CaseFacts facts, RuleIdValidator.Validated validated, Decision current) {
	}

	public Decision apply(Input in) {
		DecisionResult model = in.validated().result();
		String rule = firstMatchingRule(in, model);
		boolean returnCase = in.caseType() == CaseType.RETURN;

		Label label = model.label();
		ReasonCode reasonCode = model.reasonCode();
		List<String> ruleIds = merge(model.ruleIds(), List.of());
		String reasonKey = null;

		switch (rule) {
			case "G1" -> {
				label = Label.ESCALATED;
				reasonCode = ReasonCode.SAFETY;
				ruleIds = List.of(returnCase ? "R7.1" : "C7.1");
				reasonKey = "G1";
			}
			case "G2" -> {
				label = Label.REJECTED;
				reasonCode = ReasonCode.OUTSIDE_WINDOW;
				ruleIds = List.of("R2.3");
				reasonKey = "G2";
			}
			case "G3" -> {
				label = Label.REJECTED;
				reasonCode = ReasonCode.OUTSIDE_WINDOW;
				ruleIds = List.of("C2.3");
				reasonKey = "G3";
			}
			case "G4" -> {
				label = Label.NEEDS_MORE_INFO;
				boolean photoProblem = !in.latestAnalysis().photoUsable();
				reasonCode = photoProblem ? ReasonCode.PHOTO_UNUSABLE : ReasonCode.CATEGORY_MISMATCH;
				// The model's IDs still explain the request, so they are kept (at most 5 in total).
				ruleIds = merge(List.of(), model.ruleIds());
				reasonKey = photoProblem ? "G4.photo" : "G4.category";
			}
			case "G5" -> {
				label = Label.ESCALATED;
				reasonCode = ReasonCode.UNCERTAIN_CAUSE;
				ruleIds = List.of("C8");
				reasonKey = "G5";
			}
			case "G6" -> reasonKey = "G6";
			default -> {
			}
		}
		String overriddenBy = rule.isEmpty() ? null : rule;

		boolean safety = rule.equals("G1") || (in.current() != null && in.current().safety());
		boolean changed = in.current() != null && label != in.current().label();
		Label previous = in.current() == null ? null : in.current().label();
		String changeReason = changed ? changeReason(reasonKey, model.changeReasonPl()) : "";

		return new Decision(label, ruleIds, reasonCode, model.suggestOtherCaseType(), changeReason,
				model.missingInfoPl(), safety, changed, previous, overriddenBy);
	}

	/** G1..G5 in precedence order; G6 if only the rule-ID validation escalated; otherwise "". */
	private static String firstMatchingRule(Input in, DecisionResult model) {
		ImageAnalysis latest = in.latestAnalysis();
		if (safetyRelated(in)) {
			return "G1";
		}
		if (in.caseType() == CaseType.RETURN && in.facts().returnWindowExceeded()) {
			return "G2";
		}
		if (in.caseType() == CaseType.COMPLAINT && in.facts().complaintPeriodExceeded()) {
			return "G3";
		}
		if (in.firstDecision() && latest != null && (!latest.photoUsable() || !latest.matchesCategory())) {
			return "G4";
		}
		if (in.caseType() == CaseType.COMPLAINT && model.label() == Label.REJECTED && latest != null
				&& latest.causeConfidence() == CauseConfidence.LOW) {
			return "G5";
		}
		return in.validated().escalatedByG6() ? "G6" : "";
	}

	private static boolean safetyRelated(Input in) {
		boolean hazardInAnalysis = in.analyses().stream().anyMatch(ImageAnalysis::safetyHazard);
		boolean triage = in.triageSafetyProbability() != null && in.triageSafetyProbability() >= in.safetyThreshold();
		return hazardInAnalysis || triage || in.customerTexts().stream().anyMatch(SafetyLexicon::matches);
	}

	private String changeReason(String guardrailKey, String modelReason) {
		if (guardrailKey != null) {
			return reasons.get(guardrailKey);
		}
		if (modelReason == null || modelReason.isBlank()) {
			return reasons.get("model.default");
		}
		return modelReason.length() > MAX_REASON_LENGTH ? modelReason.substring(0, MAX_REASON_LENGTH) : modelReason;
	}

	private static List<String> merge(List<String> first, List<String> second) {
		List<String> all = new ArrayList<>(first);
		all.addAll(second);
		return all.stream().distinct().limit(MAX_IDS).toList();
	}
}
