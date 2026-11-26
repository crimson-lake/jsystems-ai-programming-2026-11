package com.hsdc.copilot.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.decision.DecisionResult.Label;
import com.hsdc.copilot.decision.DecisionResult.ReasonCode;
import com.hsdc.copilot.decision.DecisionResult.SuggestOtherCaseType;

import org.junit.jupiter.api.Test;

/** G6 rule-ID validation (ADR-001 section 3.4 step 2.1). */
class RuleIdValidatorTest {

	private static final Set<String> RETURN_IDS = Set.of("R1", "R2", "R2.3", "R3", "R3.1", "R6", "R7", "R7.1");
	private static final Set<String> COMPLAINT_IDS = Set.of("C1", "C2.3", "C8");

	private static DecisionResult result(Label label, List<String> ids) {
		return new DecisionResult(label, ids, ReasonCode.RESELLABLE, SuggestOtherCaseType.NONE, "zmiana", "brak");
	}

	@Test
	void splitSeparatesValidAndInvalidIds() {
		RuleIdValidator.Split split = RuleIdValidator.split(List.of("R2.3", "C2.3", "R9.9", "R3.1"), RETURN_IDS);
		assertThat(split.valid()).containsExactly("R2.3", "R3.1");
		assertThat(split.invalid()).containsExactly("C2.3", "R9.9");
	}

	@Test
	void splitOfAllValidHasNoInvalid() {
		RuleIdValidator.Split split = RuleIdValidator.split(List.of("R2.3"), RETURN_IDS);
		assertThat(split.valid()).containsExactly("R2.3");
		assertThat(split.invalid()).isEmpty();
	}

	@Test
	void splitOfNullOrEmptyIsEmpty() {
		assertThat(RuleIdValidator.split(null, RETURN_IDS).valid()).isEmpty();
		assertThat(RuleIdValidator.split(null, RETURN_IDS).invalid()).isEmpty();
		assertThat(RuleIdValidator.split(List.of(), RETURN_IDS).valid()).isEmpty();
	}

	@Test
	void reAskNeedsAtLeastFifteenSecondsLeft() {
		assertThat(RuleIdValidator.reAskAllowed(Duration.ofSeconds(15))).isTrue();
		assertThat(RuleIdValidator.reAskAllowed(Duration.ofSeconds(60))).isTrue();
		assertThat(RuleIdValidator.reAskAllowed(Duration.ofMillis(14_999))).isFalse();
		assertThat(RuleIdValidator.reAskAllowed(Duration.ZERO)).isFalse();
		assertThat(RuleIdValidator.reAskAllowed(Duration.ofSeconds(-1))).isFalse();
	}

	@Test
	void dropUnknownKeepsValidIdsAndLabel() {
		RuleIdValidator.Validated out = RuleIdValidator.dropUnknown(
				result(Label.ACCEPTED_PRELIMINARY, List.of("C2.3", "R3.1", "R2.3")), CaseType.RETURN, RETURN_IDS);
		assertThat(out.escalatedByG6()).isFalse();
		assertThat(out.result().ruleIds()).containsExactly("R3.1", "R2.3");
		assertThat(out.result().label()).isEqualTo(Label.ACCEPTED_PRELIMINARY);
		assertThat(out.result().reasonCode()).isEqualTo(ReasonCode.RESELLABLE);
	}

	@Test
	void dropUnknownRemovesDuplicatesAndCapsAtFive() {
		Set<String> ids = Set.of("R1", "R2", "R3", "R4", "R5", "R6", "R7");
		RuleIdValidator.Validated out = RuleIdValidator.dropUnknown(
				result(Label.REJECTED, List.of("R1", "R1", "R2", "R3", "R4", "R5", "R6", "R7")), CaseType.RETURN, ids);
		assertThat(out.result().ruleIds()).containsExactly("R1", "R2", "R3", "R4", "R5");
		assertThat(out.escalatedByG6()).isFalse();
	}

	@Test
	void noValidIdLeftEscalatesWithR6ForReturn() {
		RuleIdValidator.Validated out = RuleIdValidator.dropUnknown(
				result(Label.ACCEPTED_PRELIMINARY, List.of("C2.3", "R9.9")), CaseType.RETURN, RETURN_IDS);
		assertThat(out.escalatedByG6()).isTrue();
		assertThat(out.result().label()).isEqualTo(Label.ESCALATED);
		assertThat(out.result().ruleIds()).containsExactly("R6");
		assertThat(out.result().reasonCode()).isEqualTo(ReasonCode.OTHER);
		assertThat(out.result().missingInfoPl()).isEqualTo("brak");
	}

	@Test
	void noValidIdLeftEscalatesWithC8ForComplaint() {
		RuleIdValidator.Validated out = RuleIdValidator.dropUnknown(
				result(Label.REJECTED, List.of("R2.3")), CaseType.COMPLAINT, COMPLAINT_IDS);
		assertThat(out.escalatedByG6()).isTrue();
		assertThat(out.result().label()).isEqualTo(Label.ESCALATED);
		assertThat(out.result().ruleIds()).containsExactly("C8");
	}

	@Test
	void emptyOrNullModelIdsEscalate() {
		assertThat(RuleIdValidator.dropUnknown(result(Label.REJECTED, List.of()), CaseType.RETURN, RETURN_IDS)
			.escalatedByG6()).isTrue();
		assertThat(RuleIdValidator.dropUnknown(result(Label.REJECTED, null), CaseType.RETURN, RETURN_IDS)
			.escalatedByG6()).isTrue();
	}
}
