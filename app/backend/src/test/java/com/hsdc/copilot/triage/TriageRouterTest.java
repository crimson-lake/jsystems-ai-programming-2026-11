package com.hsdc.copilot.triage;

import static com.hsdc.copilot.triage.TriageTestSupport.choice;
import static com.hsdc.copilot.triage.TriageTestSupport.noul;
import static com.hsdc.copilot.triage.TriageTestSupport.refused;
import static com.hsdc.copilot.triage.TriageTestSupport.score;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.hsdc.copilot.cases.CaseForm;
import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.cases.Category;
import com.hsdc.copilot.config.TriageProperties.Mode;
import com.hsdc.copilot.decision.CaseFacts;
import com.hsdc.copilot.decision.DecisionResult.Label;
import com.hsdc.copilot.triage.RouteDecision.Route;
import com.hsdc.copilot.triage.TriageAnswer.Type;

/** ADR-004 section 3.5 router table with synthetic {@link TriageResult}s (no wire involved). */
class TriageRouterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);
    private final TriageRouter router = new TriageRouter(TriageTestSupport.properties(Mode.ASSIST, "x"));

    /** All answers HIGH and on the T5 side for a RETURN with a reason. */
    private static Map<String, TriageAnswer> cleanReturn() {
        Map<String, TriageAnswer> a = new LinkedHashMap<>();
        a.put("photo_usable", noul("photo_usable", 0.99));
        a.put("device_category", choice("device_category", "SMARTPHONE", 0.99));
        a.put("damage_visible", noul("damage_visible", 0.02));
        a.put("condition", score("condition", "NEW_UNUSED", 0.97));
        a.put("safety_hazard", noul("safety_hazard", 0.01));
        a.put("description_consistent", noul("description_consistent", 0.98));
        a.put("protective_film_visible", noul("protective_film_visible", 0.97));
        a.put("reason_reports_defect", noul("reason_reports_defect", 0.02));
        return a;
    }

    private static TriageResult ok(Map<String, TriageAnswer> answers) {
        return new TriageResult("typesafe/jev-1.14-20261014", answers, 900, 0.0, TriageResult.Status.OK);
    }

    private static Map<String, TriageAnswer> with(Consumer<Map<String, TriageAnswer>> change) {
        Map<String, TriageAnswer> a = cleanReturn();
        change.accept(a);
        return a;
    }

    private static CaseForm form(CaseType type, Category category, int daysAgo, String reason) {
        return new CaseForm(type, category, "Model X", TODAY.minusDays(daysAgo), reason);
    }

    private static CaseFacts facts(int daysAgo) {
        LocalDate purchase = TODAY.minusDays(daysAgo);
        return new CaseFacts(TODAY, daysAgo, daysAgo > 30, TODAY.isAfter(purchase.plusYears(2)));
    }

    private RouteDecision route(Map<String, TriageAnswer> answers, CaseType type, Category category, int daysAgo,
            Mode mode) {
        return router.route(ok(answers), form(type, category, daysAgo, "Zmiana zdania."), facts(daysAgo), mode, false);
    }

    private RouteDecision returnRoute(Map<String, TriageAnswer> answers) {
        return route(answers, CaseType.RETURN, Category.SMARTPHONE, 10, Mode.ASSIST);
    }

    // ------------------------------------------------------------------ T0

    @Test
    void t0_modeOff_failure_timeout_orAnyRefusal() {
        assertThat(route(cleanReturn(), CaseType.RETURN, Category.SMARTPHONE, 10, Mode.OFF).ruleId()).isEqualTo("T0");
        for (TriageResult.Status status : new TriageResult.Status[] { TriageResult.Status.UNAVAILABLE,
                TriageResult.Status.TIMEOUT, TriageResult.Status.DISABLED }) {
            RouteDecision r = router.route(new TriageResult(null, Map.of(), 0, null, status),
                    form(CaseType.RETURN, Category.SMARTPHONE, 10, ""), facts(10), Mode.ASSIST, true);
            assertThat(r.ruleId()).as(status.name()).isEqualTo("T0");
            assertThat(r.route()).isEqualTo(Route.LLM_PATH);
        }
        assertThat(router.route(null, form(CaseType.RETURN, Category.SMARTPHONE, 10, ""), facts(10), Mode.ASSIST, false)
            .ruleId()).isEqualTo("T0");
        assertThat(returnRoute(with(a -> a.put("condition", refused("condition", Type.SCORE)))).ruleId())
            .isEqualTo("T0");
    }

    // ------------------------------------------------------------------ T1 safety (asymmetric)

    @Test
    void t1_safetyProbabilityAtAnyConfidence_beforeTheTimeWindow() {
        RouteDecision r = route(with(a -> a.put("safety_hazard", noul("safety_hazard", 0.31, 0.40))), CaseType.RETURN,
                Category.SMARTPHONE, 45, Mode.ASSIST);
        assertThat(r.ruleId()).isEqualTo("T1");
        assertThat(r.route()).as("assist never takes the fast path").isEqualTo(Route.LLM_PATH);
        assertThat(r.fastPathDecision()).isNull();
        assertThat(FastPathRuleTable.label("T1")).isEqualTo(Label.ESCALATED);

        assertThat(returnRoute(with(a -> a.put("safety_hazard", noul("safety_hazard", 0.29, 0.40)))).ruleId())
            .isNotEqualTo("T1");
        assertThat(returnRoute(with(a -> a.put("safety_hazard", noul("safety_hazard", 0.30)))).ruleId())
            .as("0.30 inclusive").isEqualTo("T1");
    }

    @Test
    void t1_safetyLexiconOnTheReason() {
        RouteDecision r = router.route(ok(cleanReturn()), form(CaseType.RETURN, Category.SMARTPHONE, 10, "x"),
                facts(10), Mode.ASSIST, true);
        assertThat(r.ruleId()).isEqualTo("T1");
    }

    @Test
    void t1_inFastPathMode_isTaken_withSafetyRuleIdPerCaseType() {
        RouteDecision ret = route(with(a -> a.put("safety_hazard", noul("safety_hazard", 0.6))), CaseType.RETURN,
                Category.SMARTPHONE, 45, Mode.FAST_PATH);
        assertThat(ret.route()).isEqualTo(Route.FAST_PATH);
        assertThat(ret.fastPathDecision().label()).isEqualTo(Label.ESCALATED);
        assertThat(ret.fastPathDecision().ruleIds()).containsExactly("R7.1");
        RouteDecision complaint = route(with(a -> a.put("safety_hazard", noul("safety_hazard", 0.6))),
                CaseType.COMPLAINT, Category.LAPTOP, 10, Mode.FAST_PATH);
        assertThat(complaint.fastPathDecision().ruleIds()).containsExactly("C7.1");
    }

    // ------------------------------------------------------------------ T2 window

    @Test
    void t2_timeWindow() {
        assertThat(route(cleanReturn(), CaseType.RETURN, Category.SMARTPHONE, 31, Mode.ASSIST).ruleId()).isEqualTo("T2");
        assertThat(route(cleanReturn(), CaseType.RETURN, Category.SMARTPHONE, 30, Mode.ASSIST).ruleId())
            .isNotEqualTo("T2");
        RouteDecision complaint = route(cleanReturn(), CaseType.COMPLAINT, Category.SMARTPHONE, 800, Mode.FAST_PATH);
        assertThat(complaint.ruleId()).isEqualTo("T2");
        assertThat(complaint.fastPathDecision().label()).isEqualTo(Label.REJECTED);
        assertThat(complaint.fastPathDecision().ruleIds()).containsExactly("C2.3");
        assertThat(route(cleanReturn(), CaseType.RETURN, Category.SMARTPHONE, 31, Mode.FAST_PATH).fastPathDecision()
            .ruleIds()).containsExactly("R2.3");
    }

    // ------------------------------------------------------------------ T3, T4

    @Test
    void t3_unusablePhoto_withHighConfidence() {
        assertThat(returnRoute(with(a -> a.put("photo_usable", noul("photo_usable", 0.05)))).ruleId()).isEqualTo("T3");
        assertThat(FastPathRuleTable.label("T3")).isEqualTo(Label.NEEDS_MORE_INFO);
        assertThat(returnRoute(with(a -> a.put("photo_usable", noul("photo_usable", 0.06)))).ruleId())
            .as("confidence 0.94 is below H").isNotEqualTo("T3");
    }

    @Test
    void t4_categoryMismatchOrNotADevice() {
        assertThat(returnRoute(with(a -> a.put("device_category", choice("device_category", "LAPTOP", 0.99)))).ruleId())
            .isEqualTo("T4");
        assertThat(returnRoute(with(a -> a.put("device_category", choice("device_category", "NOT_A_DEVICE", 0.95))))
            .ruleId()).isEqualTo("T4");
        assertThat(returnRoute(with(a -> a.put("device_category", choice("device_category", "LAPTOP", 0.9))))
            .ruleId()).isNotEqualTo("T4");
        assertThat(FastPathRuleTable.label("T4")).isEqualTo(Label.NEEDS_MORE_INFO);
    }

    // ------------------------------------------------------------------ T5

    @Test
    void t5_cleanReturn_newOrLightUse_withRuleIds() {
        RouteDecision assist = returnRoute(cleanReturn());
        assertThat(assist.ruleId()).isEqualTo("T5");
        assertThat(assist.route()).isEqualTo(Route.LLM_PATH);

        RouteDecision fast = route(cleanReturn(), CaseType.RETURN, Category.SMARTPHONE, 10, Mode.FAST_PATH);
        assertThat(fast.route()).isEqualTo(Route.FAST_PATH);
        assertThat(fast.fastPathDecision().label()).isEqualTo(Label.ACCEPTED_PRELIMINARY);
        assertThat(fast.fastPathDecision().ruleIds()).containsExactly("R2.2", "R3.1", "R5.3");

        RouteDecision light = route(with(a -> a.put("condition", score("condition", "LIGHT_SIGNS_OF_USE", 0.96))),
                CaseType.RETURN, Category.SMARTPHONE, 10, Mode.FAST_PATH);
        assertThat(light.fastPathDecision().ruleIds()).containsExactly("R2.2", "R3.3", "R5.3");
    }

    @ParameterizedTest
    @CsvSource({ "0.95, T5", "0.9499, T7", "0.80, T7", "0.7999, T6" })
    void highAndLowBounds_areInclusive(double confidence, String rule) {
        assertThat(returnRoute(with(a -> a.put("device_category", choice("device_category", "SMARTPHONE", confidence))))
            .ruleId()).isEqualTo(rule);
    }

    @ParameterizedTest
    @EnumSource(value = Category.class, names = { "AUDIO", "OTHER" })
    void audioAndOther_neverT5(Category category) {
        RouteDecision r = route(with(a -> a.put("device_category", choice("device_category", category.name(), 0.99))),
                CaseType.RETURN, category, 10, Mode.FAST_PATH);
        assertThat(r.ruleId()).isEqualTo("T7");
        assertThat(r.route()).isEqualTo(Route.LLM_PATH);
    }

    @Test
    void complaint_neverT5() {
        RouteDecision r = route(cleanReturn(), CaseType.COMPLAINT, Category.SMARTPHONE, 10, Mode.FAST_PATH);
        assertThat(r.ruleId()).isEqualTo("T7");
        assertThat(r.fastPathDecision()).isNull();
    }

    @Test
    void defectInTheReturnReason_isNotT5_butEmptyReasonAllowsT5() {
        assertThat(returnRoute(with(a -> a.put("reason_reports_defect", noul("reason_reports_defect", 0.98))))
            .ruleId()).isEqualTo("T7");
        Map<String, TriageAnswer> noReason = with(a -> {
            a.remove("reason_reports_defect");
            a.remove("description_consistent");
        });
        assertThat(router.route(ok(noReason), form(CaseType.RETURN, Category.SMARTPHONE, 10, ""), facts(10),
                Mode.ASSIST, false).ruleId()).isEqualTo("T5");
    }

    @Test
    void t5_otherBlockers() {
        assertThat(returnRoute(with(a -> a.put("damage_visible", noul("damage_visible", 0.97)))).ruleId())
            .isEqualTo("T7");
        assertThat(returnRoute(with(a -> a.put("condition", score("condition", "HEAVY_WEAR", 0.99)))).ruleId())
            .isEqualTo("T7");
        assertThat(returnRoute(with(a -> a.put("description_consistent", noul("description_consistent", 0.03))))
            .ruleId()).isEqualTo("T7");
        assertThat(returnRoute(with(a -> a.remove("condition"))).ruleId()).as("missing answer").isEqualTo("T7");
    }

    @Test
    void t5_doesNotRequireTheProtectiveFilmAnswer_qT2() {
        assertThat(returnRoute(with(a -> a.put("protective_film_visible", noul("protective_film_visible", 0.6))))
            .ruleId()).isEqualTo("T5");
    }

    // ------------------------------------------------------------------ T6, T7

    @Test
    void t6_anyLowConfidence_listsTheLowQuestions() {
        RouteDecision r = returnRoute(with(a -> a.put("damage_visible", noul("damage_visible", 0.35))));
        assertThat(r.ruleId()).isEqualTo("T6");
        assertThat(r.reasons()).containsExactly("damage_visible");
        assertThat(r.route()).isEqualTo(Route.LLM_PATH);
        assertThat(FastPathRuleTable.label("T6")).isNull();
    }

    @Test
    void t7_mediumConfidence_listsQuestionsBelowHigh() {
        RouteDecision r = returnRoute(with(a -> a.put("damage_visible", noul("damage_visible", 0.1))));
        assertThat(r.ruleId()).isEqualTo("T7");
        assertThat(r.reasons()).containsExactly("damage_visible");
    }
}
