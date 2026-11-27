package com.hsdc.copilot.triage;

import static com.hsdc.copilot.triage.TriageTestSupport.JSON;
import static com.hsdc.copilot.triage.TriageTestSupport.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.triage.DecisionsApiClient.DecisionsResponse;
import com.hsdc.copilot.triage.TriageAnswer.Band;
import com.hsdc.copilot.triage.TriageAnswer.Type;

import tools.jackson.databind.node.ObjectNode;

/**
 * ADR-004 section 4: mapping of the Jev 1.14 wire answers, from the verbatim S-2 captures {@code decisions-jev-*}.
 * The asserted numbers are the recorded wire values, not expectations about what the photos show.
 */
class DecisionsWireMappingTest {

    private static final TriageQuestionSet QUESTIONS = new TriageQuestionSet(JSON);
    private static final List<TriageQuestion> RETURN_QS = QUESTIONS.forCase(CaseType.RETURN, "Zmiana zdania.");
    private static final List<TriageQuestion> COMPLAINT_QS = QUESTIONS.forCase(CaseType.COMPLAINT,
            "Urządzenie nie działa prawidłowo od kilku dni.");

    private static DecisionsResponse parse(String body, List<TriageQuestion> questions) {
        return DecisionsApiClient.parse(body, questions, 0.80, 0.95, JSON);
    }

    @Test
    void returnCapture_mapsNoulChoiceAndScore_andKeepsTheDatedModel() {
        DecisionsResponse r = parse(capture("decisions-jev-return-phone-1.json"), RETURN_QS);

        assertThat(r.model()).isEqualTo("typesafe/jev-1.14-20261014");
        assertThat(r.inputTokens()).isEqualTo(3922);
        assertThat(r.outputTokens()).isEqualTo(233);
        assertThat(r.cost()).isEqualTo(0.0);
        assertThat(r.answers()).containsOnlyKeys(RETURN_QS.stream().map(TriageQuestion::key).toList());

        TriageAnswer usable = r.answers().get("photo_usable");
        assertThat(usable.type()).isEqualTo(Type.NOUL);
        assertThat(usable.probabilityTrue()).isEqualTo(0.79);
        assertThat(usable.value()).isEqualTo(true);
        assertThat(usable.confidence()).isEqualTo(0.79);
        assertThat(usable.confidenceDerived()).isTrue();
        assertThat(usable.band()).isEqualTo(Band.LOW);
        assertThat(usable.probabilities().get("true")).isEqualTo(0.79);
        assertThat(usable.probabilities().get("false")).isCloseTo(0.21, within(1e-9));
        assertThat(usable.refused()).isFalse();

        TriageAnswer safety = r.answers().get("safety_hazard");
        assertThat(safety.value()).isEqualTo(false);
        assertThat(safety.probabilityTrue()).isEqualTo(0.13);
        assertThat(safety.confidence()).isEqualTo(0.87);
        assertThat(safety.band()).isEqualTo(Band.MEDIUM);

        TriageAnswer category = r.answers().get("device_category");
        assertThat(category.type()).isEqualTo(Type.CHOICE);
        assertThat(category.value()).isEqualTo("SMARTPHONE");
        assertThat(category.confidence()).isEqualTo(1.0);
        assertThat(category.confidenceDerived()).isFalse();
        assertThat(category.band()).isEqualTo(Band.HIGH);
        assertThat(category.probabilities()).containsEntry("SMARTPHONE", 1.0).containsEntry("NOT_A_DEVICE", 0.0);
        assertThat(category.probabilityTrue()).isNull();

        TriageAnswer condition = r.answers().get("condition");
        assertThat(condition.type()).isEqualTo(Type.SCORE);
        assertThat(condition.score()).isEqualTo(2.96);
        assertThat(condition.value()).as("legend label with the highest probability").isEqualTo("DAMAGED");
        assertThat(condition.probabilities()).containsOnlyKeys("NEW_UNUSED", "LIGHT_SIGNS_OF_USE", "HEAVY_WEAR",
                "DAMAGED");
        assertThat(condition.probabilities()).containsEntry("DAMAGED", 0.96).containsEntry("LIGHT_SIGNS_OF_USE", 0.01);
        assertThat(condition.confidence()).isEqualTo(0.95);
        assertThat(condition.band()).as("0.95 is HIGH (inclusive)").isEqualTo(Band.HIGH);

        TriageAnswer defect = r.answers().get("reason_reports_defect");
        assertThat(defect.value()).isEqualTo(false);
        assertThat(defect.band()).isEqualTo(Band.HIGH);
    }

    @Test
    void complaintCapture_mapsComplaintQuestions() {
        DecisionsResponse r = parse(capture("decisions-jev-complaint-laptop-1.json"), COMPLAINT_QS);

        assertThat(r.model()).isEqualTo("typesafe/jev-1.14-20261014");
        assertThat(r.answers()).containsKeys("damage_type", "likely_cause").doesNotContainKey("protective_film_visible");
        assertThat(r.answers().get("likely_cause").value()).isEqualTo("IMPACT");
        assertThat(r.answers().get("likely_cause").confidence()).isEqualTo(0.99);
        assertThat(r.answers().get("condition").confidence()).isEqualTo(0.94);
        assertThat(r.answers().get("condition").band()).isEqualTo(Band.MEDIUM);
        assertThat(r.answers().get("description_consistent").band()).isEqualTo(Band.LOW);
        assertThat(r.inputTokens()).isEqualTo(7425);
    }

    // ------------------------------------------------------------------ fail closed (derived from the captures)

    private static String withAnswer(String key, String answerJson) {
        ObjectNode root = (ObjectNode) JSON.readTree(capture("decisions-jev-return-phone-1.json"));
        ObjectNode answers = (ObjectNode) root.get("answers");
        if (answerJson == null) {
            answers.remove(key);
        }
        else {
            answers.set(key, JSON.readTree(answerJson));
        }
        return JSON.writeValueAsString(root);
    }

    @Test
    void refusal_missingKey_andUnexpectedTypes_areRefused() {
        Map<String, String> variants = Map.of(
                "safety_hazard", "{\"type\":\"refusal\"}",
                "photo_usable", "{\"type\":\"choice\",\"choice\":\"x\",\"confidence\":1}",
                "damage_visible", "{\"type\":\"noul\",\"noul\":\"yes\"}",
                "condition", "{\"type\":\"score\",\"score\":2.96,\"probabilities\":{\"0\":1},\"confidence\":0.9}",
                "device_category", "{\"type\":\"choice\",\"choice\":\"SMARTPHONE\"}");
        variants.forEach((key, json) -> {
            TriageAnswer a = parse(withAnswer(key, json), RETURN_QS).answers().get(key);
            assertThat(a.refused()).as(key + " " + json).isTrue();
            assertThat(a.value()).isNull();
            assertThat(a.band()).isEqualTo(Band.LOW);
        });
        TriageAnswer missing = parse(withAnswer("protective_film_visible", null), RETURN_QS).answers()
            .get("protective_film_visible");
        assertThat(missing.refused()).isTrue();
        assertThat(missing.type()).isEqualTo(Type.NOUL);
    }

    @Test
    void noulOutsideZeroToOne_isRefused() {
        assertThat(parse(withAnswer("safety_hazard", "{\"type\":\"noul\",\"noul\":1.3}"), RETURN_QS).answers()
            .get("safety_hazard").refused()).isTrue();
    }

    @Test
    void malformedBodies_areUnavailable() {
        for (String body : List.of("not json", "{\"model\":\"typesafe/jev-1.14-20261014\"}", "[]", "")) {
            assertThatThrownBy(() -> parse(body, RETURN_QS)).as(body).isInstanceOf(TriageUnavailableException.class)
                .extracting(e -> ((TriageUnavailableException) e).isTimeout()).isEqualTo(false);
        }
    }
}
