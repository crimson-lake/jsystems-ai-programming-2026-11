package com.hsdc.copilot.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.hsdc.copilot.analysis.ImageAnalysis;
import com.hsdc.copilot.analysis.ImageAnalysis.CauseConfidence;
import com.hsdc.copilot.analysis.ImageAnalysis.DamageType;
import com.hsdc.copilot.analysis.ImageAnalysis.LikelyCause;
import com.hsdc.copilot.analysis.ImageAnalysis.PhotoProblem;
import com.hsdc.copilot.analysis.ImageAnalysis.Source;
import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.decision.DecisionResult.Label;
import com.hsdc.copilot.decision.DecisionResult.ReasonCode;
import com.hsdc.copilot.decision.DecisionResult.SuggestOtherCaseType;
import com.hsdc.copilot.prompts.GuardrailReasons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.DefaultResourceLoader;

/** G1-G6, precedence, ID replacement and revision rule (ADR-001 section 3.4; PRD section 11; AC-23...AC-27). */
class GuardrailsTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);
	private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
	private static final Clock CLOCK = Clock.fixed(TODAY.atTime(12, 0).atZone(WARSAW).toInstant(), WARSAW);
	private static final GuardrailReasons REASONS = new GuardrailReasons(new DefaultResourceLoader());
	private static final Guardrails GUARDRAILS = new Guardrails(REASONS);
	private static final double THRESHOLD = 0.30;

	// ---- builders -------------------------------------------------------------------------------------------

	private static ImageAnalysis analysis(boolean usable, boolean matches, boolean hazard, CauseConfidence confidence) {
		return new ImageAnalysis(usable, usable ? PhotoProblem.NONE : PhotoProblem.BLURRY, matches, true,
				DamageType.SCREEN_CRACK, "ekran", LikelyCause.UNKNOWN, confidence, false, List.of(), false, hazard,
				"opis", List.of(), Source.LLM);
	}

	private static ImageAnalysis okAnalysis() {
		return analysis(true, true, false, CauseConfidence.HIGH);
	}

	private static DecisionResult model(Label label, String... ids) {
		return new DecisionResult(label, List.of(ids), ReasonCode.RESELLABLE, SuggestOtherCaseType.COMPLAINT, "",
				"brakuje");
	}

	private static DecisionResult withReason(Label label, String changeReason, String... ids) {
		return new DecisionResult(label, List.of(ids), ReasonCode.COVERED_DEFECT, SuggestOtherCaseType.NONE,
				changeReason, "brakuje");
	}

	private static Decision current(Label label, boolean safety) {
		return new Decision(label, List.of("R3.1"), ReasonCode.RESELLABLE, SuggestOtherCaseType.NONE, "", "", safety,
				false, null, null);
	}

	/** Mutable test input; defaults describe an unremarkable first RETURN decision bought 5 days ago. */
	private static final class In {
		CaseType type = CaseType.RETURN;
		boolean first = true;
		List<ImageAnalysis> analyses = List.of(okAnalysis());
		ImageAnalysis latest = analyses.get(0);
		Double triage = null;
		List<String> texts = List.of();
		CaseFacts facts = CaseFacts.of(TODAY.minusDays(5), CLOCK);
		RuleIdValidator.Validated validated = new RuleIdValidator.Validated(GuardrailsTest.model(Label.ACCEPTED_PRELIMINARY, "R3.1"),
				false);
		Decision current = null;

		In type(CaseType t) {
			type = t;
			return this;
		}

		In notFirst() {
			first = false;
			return this;
		}

		In analysis(ImageAnalysis a) {
			analyses = List.of(a);
			latest = a;
			return this;
		}

		In analyses(List<ImageAnalysis> all, ImageAnalysis last) {
			analyses = all;
			latest = last;
			return this;
		}

		In triage(Double p) {
			triage = p;
			return this;
		}

		In texts(String... t) {
			texts = List.of(t);
			return this;
		}

		In purchasedDaysAgo(int days) {
			facts = CaseFacts.of(TODAY.minusDays(days), CLOCK);
			return this;
		}

		In purchased(LocalDate date) {
			facts = CaseFacts.of(date, CLOCK);
			return this;
		}

		In decision(DecisionResult m) {
			validated = new RuleIdValidator.Validated(m, false);
			return this;
		}

		In g6(DecisionResult m) {
			validated = new RuleIdValidator.Validated(m, true);
			return this;
		}

		In current(Decision d) {
			current = d;
			return this;
		}

		Decision run() {
			return GUARDRAILS.apply(new Guardrails.Input(type, first, analyses, latest, triage, THRESHOLD, texts,
					facts, validated, current));
		}
	}

	private static In in() {
		return new In();
	}

	// ---- no guardrail ---------------------------------------------------------------------------------------

	@Test
	void unremarkableFirstDecisionPassesThroughWithoutOverride() {
		Decision d = in().decision(withReason(Label.ACCEPTED_PRELIMINARY, "stare", "R3.1", "R5.3")).run();
		assertThat(d.label()).isEqualTo(Label.ACCEPTED_PRELIMINARY);
		assertThat(d.ruleIds()).containsExactly("R3.1", "R5.3");
		assertThat(d.reasonCode()).isEqualTo(ReasonCode.COVERED_DEFECT);
		assertThat(d.suggestOtherCaseType()).isEqualTo(SuggestOtherCaseType.NONE);
		assertThat(d.missingInfoPl()).isEqualTo("brakuje");
		assertThat(d.overriddenBy()).isNull();
		assertThat(d.safety()).isFalse();
	}

	@Test
	void firstDecisionHasNoChangeAndNoPreviousLabel() {
		Decision d = in().decision(withReason(Label.REJECTED, "model chce zmiany", "R3.4")).run();
		assertThat(d.changed()).isFalse();
		assertThat(d.previousLabel()).isNull();
		assertThat(d.changeReasonPl()).isEmpty();
	}

	@Test
	void modelRuleIdsAreCappedAtFiveAndDeduplicated() {
		Decision d = in().decision(model(Label.REJECTED, "R1", "R1", "R2", "R3", "R4", "R5", "R6", "R7")).run();
		assertThat(d.ruleIds()).containsExactly("R1", "R2", "R3", "R4", "R5");
	}

	@Test
	void missingAnalysisSkipsPhotoGuardrails() {
		Decision d = in().analyses(List.of(), null)
			.type(CaseType.COMPLAINT)
			.decision(model(Label.REJECTED, "C4"))
			.run();
		assertThat(d.label()).isEqualTo(Label.REJECTED);
		assertThat(d.overriddenBy()).isNull();
	}

	// ---- G1 safety ------------------------------------------------------------------------------------------

	@Test
	void g1AnalysisHazardEscalatesWithR7_1ForReturn() {
		Decision d = in().analysis(analysis(true, true, true, CauseConfidence.HIGH)).run();
		assertThat(d.label()).isEqualTo(Label.ESCALATED);
		assertThat(d.ruleIds()).containsExactly("R7.1");
		assertThat(d.reasonCode()).isEqualTo(ReasonCode.SAFETY);
		assertThat(d.safety()).isTrue();
		assertThat(d.overriddenBy()).isEqualTo("G1");
	}

	@Test
	void g1UsesC7_1ForComplaint() {
		Decision d = in().type(CaseType.COMPLAINT)
			.analysis(analysis(true, true, true, CauseConfidence.HIGH))
			.decision(model(Label.ACCEPTED_PRELIMINARY, "C3"))
			.run();
		assertThat(d.ruleIds()).containsExactly("C7.1");
		assertThat(d.label()).isEqualTo(Label.ESCALATED);
	}

	@Test
	void g1AnyEarlierAnalysisCounts() {
		ImageAnalysis hazard = analysis(true, true, true, CauseConfidence.HIGH);
		ImageAnalysis later = okAnalysis();
		Decision d = in().notFirst().analyses(List.of(hazard, later), later).run();
		assertThat(d.overriddenBy()).isEqualTo("G1");
	}

	@Test
	void g1TriageProbabilityThreshold() {
		assertThat(in().triage(0.30).run().overriddenBy()).isEqualTo("G1");
		assertThat(in().triage(0.31).run().overriddenBy()).isEqualTo("G1");
		assertThat(in().triage(0.29).run().overriddenBy()).isNull();
		assertThat(in().triage(null).run().overriddenBy()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"bateria jest napuchnięta", "bateria jest napuchnieta", "Telefon NAPUCHŁ", "wybrzusza sie obudowa",
			"obudowa się wybrzuszyła", "wydobywa się dym", "leci DYM z ładowarki", "widać iskry", "iskrzy gniazdo",
			"ładowarka jest nadpalona", "kabel nadpalony", "nadpalony", "przypalona obudowa", "spalony port",
			"czuć spaleniznę", "swąd z urządzenia", "telefon pali się", "telefon pali sie", "zapalił się",
			"zapalil sie", "był ogień", "ogien", "pozar", "to był pożar", "laptop się przegrzewa",
			"przegrzewa się", "przegrzewa sie", "gorąca bateria", "goraca bateria", "odsłonięte przewody",
			"odslonieto przewod", "puchnie bateria", "spuchnięty ekran" })
	void g1LexiconMatchesWithAndWithoutDiacritics(String text) {
		Decision d = in().texts("Dzień dobry", text).run();
		assertThat(d.overriddenBy()).as(text).isEqualTo("G1");
		assertThat(d.label()).isEqualTo(Label.ESCALATED);
		assertThat(d.safety()).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "ekran jest zarysowany", "nie włącza się", "ognisty kolor obudowy",
			"pali", "pozarzadowy", "goraca woda i bateria", "" })
	void g1LexiconDoesNotMatchHarmlessText(String text) {
		assertThat(in().texts(text).run().overriddenBy()).as(text).isNull();
	}

	@Test
	void g1BeatsG2ForOldReturn() {
		Decision d = in().purchasedDaysAgo(45)
			.texts("bateria jest napuchnięta")
			.decision(model(Label.ACCEPTED_PRELIMINARY, "R3.1"))
			.run();
		assertThat(d.label()).isEqualTo(Label.ESCALATED);
		assertThat(d.ruleIds()).containsExactly("R7.1");
		assertThat(d.overriddenBy()).isEqualTo("G1");
	}

	@Test
	void safetyIsKeptOnLaterTurns() {
		Decision d = in().notFirst().current(current(Label.ESCALATED, true)).run();
		assertThat(d.safety()).isTrue();
		assertThat(d.overriddenBy()).isNull();
	}

	// ---- G2 / G3 --------------------------------------------------------------------------------------------

	@Test
	void g2Day30IsAllowedDay31IsRejectedWithR2_3() {
		Decision day30 = in().purchasedDaysAgo(30).decision(model(Label.ACCEPTED_PRELIMINARY, "R3.1")).run();
		assertThat(day30.label()).isEqualTo(Label.ACCEPTED_PRELIMINARY);
		assertThat(day30.overriddenBy()).isNull();

		Decision day31 = in().purchasedDaysAgo(31).decision(model(Label.ACCEPTED_PRELIMINARY, "R3.1")).run();
		assertThat(day31.label()).isEqualTo(Label.REJECTED);
		assertThat(day31.ruleIds()).containsExactly("R2.3");
		assertThat(day31.reasonCode()).isEqualTo(ReasonCode.OUTSIDE_WINDOW);
		assertThat(day31.overriddenBy()).isEqualTo("G2");
		assertThat(day31.safety()).isFalse();
	}

	@Test
	void g2PurchaseTodayIsInsideTheWindow() {
		assertThat(in().purchasedDaysAgo(0).run().overriddenBy()).isNull();
	}

	@Test
	void g2AppliesOnlyToReturns() {
		Decision d = in().type(CaseType.COMPLAINT).purchasedDaysAgo(45).decision(model(Label.ACCEPTED_PRELIMINARY, "C3")).run();
		assertThat(d.overriddenBy()).isNull();
		assertThat(d.label()).isEqualTo(Label.ACCEPTED_PRELIMINARY);
	}

	@Test
	void g3TwoYearsAllowedTwoYearsAndOneDayRejectedWithC2_3() {
		In ok = in().type(CaseType.COMPLAINT).purchased(TODAY.minusYears(2)).decision(model(Label.ACCEPTED_PRELIMINARY, "C3"));
		assertThat(ok.run().overriddenBy()).isNull();

		Decision late = in().type(CaseType.COMPLAINT)
			.purchased(TODAY.minusYears(2).minusDays(1))
			.decision(model(Label.ACCEPTED_PRELIMINARY, "C3"))
			.run();
		assertThat(late.label()).isEqualTo(Label.REJECTED);
		assertThat(late.ruleIds()).containsExactly("C2.3");
		assertThat(late.reasonCode()).isEqualTo(ReasonCode.OUTSIDE_WINDOW);
		assertThat(late.overriddenBy()).isEqualTo("G3");
	}

	@Test
	void g3TwentyNinthOfFebruary() {
		LocalDate leap = LocalDate.of(2024, 2, 29);
		CaseFacts onFeb28 = CaseFacts.of(leap, Clock.fixed(LocalDate.of(2026, 2, 28).atTime(12, 0).atZone(WARSAW).toInstant(), WARSAW));
		CaseFacts onMar1 = CaseFacts.of(leap, Clock.fixed(LocalDate.of(2026, 3, 1).atTime(12, 0).atZone(WARSAW).toInstant(), WARSAW));
		In a = in().type(CaseType.COMPLAINT).decision(model(Label.ACCEPTED_PRELIMINARY, "C3"));
		a.facts = onFeb28;
		In b = in().type(CaseType.COMPLAINT).decision(model(Label.ACCEPTED_PRELIMINARY, "C3"));
		b.facts = onMar1;
		assertThat(a.run().overriddenBy()).isNull();
		assertThat(b.run().overriddenBy()).isEqualTo("G3");
	}

	@Test
	void g3AppliesOnlyToComplaints() {
		In r = in().purchased(TODAY.minusYears(3)).decision(model(Label.ACCEPTED_PRELIMINARY, "R3.1"));
		// a 3-year-old return is G2 (outside 30 days), never G3
		assertThat(r.run().overriddenBy()).isEqualTo("G2");
	}

	// ---- G4 photo -------------------------------------------------------------------------------------------

	@Test
	void g4UnusablePhotoNeedsMoreInfoAndKeepsModelIds() {
		Decision d = in().analysis(analysis(false, true, false, CauseConfidence.HIGH))
			.decision(model(Label.ACCEPTED_PRELIMINARY, "R3.1", "R5.3"))
			.run();
		assertThat(d.label()).isEqualTo(Label.NEEDS_MORE_INFO);
		assertThat(d.ruleIds()).containsExactly("R3.1", "R5.3");
		assertThat(d.reasonCode()).isEqualTo(ReasonCode.PHOTO_UNUSABLE);
		assertThat(d.overriddenBy()).isEqualTo("G4");
	}

	@Test
	void g4CategoryMismatch() {
		Decision d = in().analysis(analysis(true, false, false, CauseConfidence.HIGH)).run();
		assertThat(d.label()).isEqualTo(Label.NEEDS_MORE_INFO);
		assertThat(d.reasonCode()).isEqualTo(ReasonCode.CATEGORY_MISMATCH);
		assertThat(d.overriddenBy()).isEqualTo("G4");
	}

	@Test
	void g4BothProblemsReportThePhotoFirst() {
		Decision d = in().analysis(analysis(false, false, false, CauseConfidence.HIGH)).run();
		assertThat(d.reasonCode()).isEqualTo(ReasonCode.PHOTO_UNUSABLE);
	}

	@Test
	void g4IsOnlyForTheFirstDecision() {
		Decision d = in().notFirst().analysis(analysis(false, false, false, CauseConfidence.HIGH)).run();
		assertThat(d.overriddenBy()).isNull();
		assertThat(d.label()).isEqualTo(Label.ACCEPTED_PRELIMINARY);
	}

	@Test
	void g4DoesNotBeatSafetyOrWindow() {
		ImageAnalysis bad = analysis(false, true, true, CauseConfidence.HIGH);
		assertThat(in().analysis(bad).run().overriddenBy()).isEqualTo("G1");
		assertThat(in().analysis(analysis(false, true, false, CauseConfidence.HIGH)).purchasedDaysAgo(40).run()
			.overriddenBy()).isEqualTo("G2");
	}

	@Test
	void g4MergesModelIdsUpToFive() {
		Decision d = in().analysis(analysis(false, true, false, CauseConfidence.HIGH))
			.decision(model(Label.ACCEPTED_PRELIMINARY, "R1", "R2", "R3", "R4", "R5"))
			.run();
		assertThat(d.ruleIds()).containsExactly("R1", "R2", "R3", "R4", "R5");
	}

	// ---- G5 -------------------------------------------------------------------------------------------------

	@Test
	void g5LowConfidenceRejectedComplaintIsEscalatedWithC8() {
		Decision d = in().type(CaseType.COMPLAINT)
			.analysis(analysis(true, true, false, CauseConfidence.LOW))
			.decision(model(Label.REJECTED, "C4"))
			.run();
		assertThat(d.label()).isEqualTo(Label.ESCALATED);
		assertThat(d.ruleIds()).containsExactly("C8");
		assertThat(d.reasonCode()).isEqualTo(ReasonCode.UNCERTAIN_CAUSE);
		assertThat(d.overriddenBy()).isEqualTo("G5");
	}

	@Test
	void g5MediumConfidenceKeepsRejected() {
		Decision d = in().type(CaseType.COMPLAINT)
			.analysis(analysis(true, true, false, CauseConfidence.MEDIUM))
			.decision(model(Label.REJECTED, "C4"))
			.run();
		assertThat(d.label()).isEqualTo(Label.REJECTED);
		assertThat(d.overriddenBy()).isNull();
	}

	@Test
	void g5NeedsRejectedLabelAndComplaint() {
		ImageAnalysis low = analysis(true, true, false, CauseConfidence.LOW);
		assertThat(in().type(CaseType.COMPLAINT).analysis(low).decision(model(Label.ACCEPTED_PRELIMINARY, "C3")).run()
			.overriddenBy()).isNull();
		assertThat(in().analysis(low).decision(model(Label.REJECTED, "R3.4")).run().overriddenBy()).isNull();
	}

	// ---- G6 -------------------------------------------------------------------------------------------------

	@Test
	void g6EscalationIsReportedWhenNoOtherGuardrailMatches() {
		Decision d = in().g6(model(Label.ESCALATED, "R6")).run();
		assertThat(d.label()).isEqualTo(Label.ESCALATED);
		assertThat(d.ruleIds()).containsExactly("R6");
		assertThat(d.overriddenBy()).isEqualTo("G6");
	}

	@Test
	void g6UsesItsFixedReasonWhenTheLabelChanges() {
		Decision d = in().notFirst().current(current(Label.ACCEPTED_PRELIMINARY, false)).g6(withReason(Label.ESCALATED, "x", "R6")).run();
		assertThat(d.changed()).isTrue();
		assertThat(d.changeReasonPl()).isEqualTo(REASONS.get("G6"));
	}

	@Test
	void otherGuardrailsStillWinOverG6() {
		Decision d = in().purchasedDaysAgo(45).g6(model(Label.ESCALATED, "R6")).run();
		assertThat(d.overriddenBy()).isEqualTo("G2");
		assertThat(d.label()).isEqualTo(Label.REJECTED);
	}

	// ---- revision rule and changeReasonPl --------------------------------------------------------------------

	@Test
	void changedFlagAndPreviousLabel() {
		Decision d = in().notFirst()
			.current(current(Label.REJECTED, false))
			.decision(withReason(Label.ACCEPTED_PRELIMINARY, "Nowe zdjęcie to zmienia", "R3.1"))
			.run();
		assertThat(d.changed()).isTrue();
		assertThat(d.previousLabel()).isEqualTo(Label.REJECTED);
		assertThat(d.changeReasonPl()).isEqualTo("Nowe zdjęcie to zmienia");
	}

	@Test
	void sameLabelIsNotChanged() {
		Decision d = in().notFirst()
			.current(current(Label.ACCEPTED_PRELIMINARY, false))
			.decision(withReason(Label.ACCEPTED_PRELIMINARY, "model gada", "R3.1"))
			.run();
		assertThat(d.changed()).isFalse();
		assertThat(d.previousLabel()).isEqualTo(Label.ACCEPTED_PRELIMINARY);
		assertThat(d.changeReasonPl()).isEmpty();
	}

	@Test
	void emptyOrBlankOrNullModelReasonFallsBackToModelDefault() {
		for (String reason : new String[] { "", "   ", null }) {
			Decision d = in().notFirst()
				.current(current(Label.REJECTED, false))
				.decision(withReason(Label.ACCEPTED_PRELIMINARY, reason, "R3.1"))
				.run();
			assertThat(d.changed()).isTrue();
			assertThat(d.changeReasonPl()).isEqualTo("Na podstawie nowych informacji zmieniliśmy ocenę Twojego zgłoszenia.");
		}
	}

	@Test
	void longModelReasonIsCutToThreeHundredChars() {
		Decision d = in().notFirst()
			.current(current(Label.REJECTED, false))
			.decision(withReason(Label.ACCEPTED_PRELIMINARY, "x".repeat(450), "R3.1"))
			.run();
		assertThat(d.changeReasonPl()).hasSize(300);
	}

	@Test
	void guardrailSetLabelUsesFixedTextPerGuardrail() {
		Decision rejected = current(Label.REJECTED, false);
		assertThat(in().notFirst().current(rejected).texts("dym z telefonu").run().changeReasonPl())
			.isEqualTo(REASONS.get("G1"));
		assertThat(in().notFirst().current(current(Label.ACCEPTED_PRELIMINARY, false)).purchasedDaysAgo(40).run()
			.changeReasonPl()).isEqualTo(REASONS.get("G2"));
		assertThat(in().notFirst().type(CaseType.COMPLAINT).current(current(Label.ACCEPTED_PRELIMINARY, false))
			.purchased(TODAY.minusYears(3)).run().changeReasonPl()).isEqualTo(REASONS.get("G3"));
		assertThat(in().current(current(Label.ACCEPTED_PRELIMINARY, false))
			.analysis(analysis(false, true, false, CauseConfidence.HIGH)).run().changeReasonPl())
			.isEqualTo(REASONS.get("G4.photo"));
		assertThat(in().current(current(Label.ACCEPTED_PRELIMINARY, false))
			.analysis(analysis(true, false, false, CauseConfidence.HIGH)).run().changeReasonPl())
			.isEqualTo(REASONS.get("G4.category"));
		assertThat(in().notFirst().type(CaseType.COMPLAINT).current(current(Label.REJECTED, false))
			.analysis(analysis(true, true, false, CauseConfidence.LOW)).decision(model(Label.REJECTED, "C4")).run()
			.changeReasonPl()).isEqualTo(REASONS.get("G5"));
	}

	@Test
	void guardrailThatKeepsTheLabelLeavesNoChangeReason() {
		Decision d = in().notFirst().current(current(Label.REJECTED, false)).purchasedDaysAgo(40)
			.decision(model(Label.REJECTED, "R3.4")).run();
		assertThat(d.overriddenBy()).isEqualTo("G2");
		assertThat(d.changed()).isFalse();
		assertThat(d.changeReasonPl()).isEmpty();
	}
}
