package com.hsdc.copilot.triage;

import static com.hsdc.copilot.triage.TriageTestSupport.JSON;
import static com.hsdc.copilot.triage.TriageTestSupport.capture;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.config.TriageProperties.Mode;

import tools.jackson.databind.JsonNode;

/** ADR-004 section 3.3: request building, compared with the request recorded in S-2. */
class DecisionsRequestTest {

    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3 };
    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 1, 2 };

    private final TriageQuestionSet questions = new TriageQuestionSet(JSON);
    private final DecisionsApiClient client = new DecisionsApiClient(
            TriageTestSupport.properties(Mode.ASSIST, "http://localhost:0/api/alpha/decisions"),
            TriageTestSupport.openRouter(), JSON);

    private static Map<String, Object> facts(String caseType, String category, String reason) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("caseType", caseType);
        facts.put("formCategory", category);
        facts.put("model", "Galaxy S23");
        facts.put("daysSincePurchase", 10);
        facts.put("reason", reason);
        return facts;
    }

    private static List<String> keys(JsonNode questionsNode) {
        List<String> keys = new ArrayList<>();
        questionsNode.propertyNames().forEach(keys::add);
        return keys;
    }

    @Test
    void returnRequest_matchesTheRecordedS2Request_exceptTheAssetBytesAndUuid() {
        JsonNode recorded = JSON.readTree(capture("decisions-jev-return-phone-1.request.json"));
        String assetId = recorded.path("state").get(1).asString();

        JsonNode body = client.requestBody("typesafe/jev-1.14", facts("RETURN", "SMARTPHONE", "Zmiana zdania."),
                assetId, JPEG, questions.forCase(CaseType.RETURN, "Zmiana zdania."));

        assertThat(body.path("model").asString()).isEqualTo("typesafe/jev-1.14");
        assertThat(body.path("state")).isEqualTo(recorded.path("state"));
        assertThat(body.path("questions")).as("question texts are the S-2 texts").isEqualTo(recorded.path("questions"));
        assertThat(keys(body.path("questions"))).isEqualTo(keys(recorded.path("questions")));
        assertThat(body.path("assets").path(assetId).path("type").asString()).isEqualTo("image");
        assertThat(body.path("assets").path(assetId).path("format").asString())
            .isEqualTo(recorded.path("assets").path(assetId).path("format").asString());
    }

    @Test
    void complaintRequest_matchesTheRecordedS2Questions() {
        JsonNode recorded = JSON.readTree(capture("decisions-jev-complaint-laptop-1.request.json"));
        String reason = "Urządzenie nie działa prawidłowo od kilku dni.";

        JsonNode body = client.requestBody("typesafe/jev-1.14", facts("COMPLAINT", "LAPTOP", reason), "id", JPEG,
                questions.forCase(CaseType.COMPLAINT, reason));

        assertThat(body.path("questions")).isEqualTo(recorded.path("questions"));
    }

    @Test
    void asset_isStandardBase64WithoutDataPrefix_andReferencedAsAWholeStateItem() {
        JsonNode body = client.requestBody("m", facts("RETURN", "SMARTPHONE", ""), "1b4e28ba-2fa1-11d2-883f-0016d3cca427",
                JPEG, questions.forCase(CaseType.RETURN, ""));

        JsonNode asset = body.path("assets").path("1b4e28ba-2fa1-11d2-883f-0016d3cca427");
        String data = asset.path("data_base64").asString();
        assertThat(data).doesNotStartWith("data:");
        assertThat(Base64.getDecoder().decode(data)).isEqualTo(JPEG);
        assertThat(asset.path("format").asString()).isEqualTo("jpeg");
        assertThat(body.path("state").size()).isEqualTo(2);
        assertThat(body.path("state").get(1).asString()).isEqualTo("1b4e28ba-2fa1-11d2-883f-0016d3cca427");
        assertThat(body.path("assets").size()).as("every asset is referenced").isEqualTo(1);
        assertThat(body.has("stream")).isFalse();
    }

    @Test
    void pngAsset_isSentAsPng() {
        JsonNode body = client.requestBody("m", facts("RETURN", "SMARTPHONE", ""), "id", PNG,
                questions.forCase(CaseType.RETURN, ""));
        assertThat(body.path("assets").path("id").path("format").asString()).isEqualTo("png");
    }

    @Test
    void questionSets_perCaseType_andReasonDependentQuestions() {
        assertThat(questions.forCase(CaseType.RETURN, "Zmiana zdania.")).extracting(TriageQuestion::key)
            .containsExactly("photo_usable", "device_category", "damage_visible", "condition", "safety_hazard",
                    "description_consistent", "protective_film_visible", "reason_reports_defect");
        assertThat(questions.forCase(CaseType.RETURN, "  ")).extracting(TriageQuestion::key)
            .doesNotContain("description_consistent", "reason_reports_defect").contains("protective_film_visible");
        assertThat(questions.forCase(CaseType.RETURN, null)).extracting(TriageQuestion::key)
            .doesNotContain("description_consistent", "reason_reports_defect");
        assertThat(questions.forCase(CaseType.COMPLAINT, "Nie działa ekran od tygodnia."))
            .extracting(TriageQuestion::key)
            .containsExactly("photo_usable", "device_category", "damage_visible", "condition", "safety_hazard",
                    "description_consistent", "damage_type", "likely_cause");
        assertThat(questions.forCase(CaseType.COMPLAINT, "")).extracting(TriageQuestion::key)
            .doesNotContain("description_consistent", "reason_reports_defect", "protective_film_visible");
    }
}
