package com.hsdc.copilot.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.hsdc.copilot.decision.DecisionResult;
import com.openai.models.responses.ResponseCreateParams;
import org.junit.jupiter.api.Test;

/** Local (no network) check that the strict JSON schemas derive from the contract records. */
class StructuredSchemaTest {

    @Test
    void imageAnalysisResultSchemaPassesLocalValidationAndExcludesSource() {
        var params = ResponseCreateParams.builder().model("m").input("i").text(ImageAnalysisResult.class).build();
        String json = params.rawParams()._text().toString();
        assertThat(json).contains("photoUsable", "triageDisagreements").doesNotContain("\"source\"");
    }

    @Test
    void decisionResultSchemaPassesLocalValidation() {
        var params = ResponseCreateParams.builder().model("m").input("i").text(DecisionResult.class).build();
        assertThat(params.rawParams()._text().toString()).contains("ruleIds", "missingInfoPl");
    }
}
