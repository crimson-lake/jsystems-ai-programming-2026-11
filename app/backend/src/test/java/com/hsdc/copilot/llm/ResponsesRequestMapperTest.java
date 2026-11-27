package com.hsdc.copilot.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.hsdc.copilot.analysis.ImageAnalysisResult;
import com.hsdc.copilot.decision.DecisionResult;
import com.hsdc.copilot.llm.LlmRequest.InputItem;
import com.hsdc.copilot.llm.LlmRequest.InputItem.Role;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.ResponseCreateParams;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Unit test of the provider-neutral request to Responses API params mapping (ADR-001 sections 3.3, 3.4). */
class ResponsesRequestMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String DATA_URL = "data:image/jpeg;base64,AAAA";

    private static JsonNode body(ResponseCreateParams params) throws Exception {
        return JSON.readTree(ObjectMappers.jsonMapper().writeValueAsString(params._body()));
    }

    private static LlmRequest request(ReasoningEffort effort, Class<?> schema, InputItem... items) {
        return new LlmRequest("some/model", "INSTRUCTIONS", List.of(items), effort, schema, Duration.ofSeconds(5));
    }

    @Test
    void structuredRequestCarriesModelInstructionsStrictSchemaReasoningAndRequireParameters() throws Exception {
        var params = ResponsesRequestMapper.structured(
                request(ReasoningEffort.MEDIUM, DecisionResult.class, new InputItem(Role.USER, "facts", null)),
                DecisionResult.class);

        JsonNode body = body(params.rawParams());

        assertThat(body.path("model").asString()).isEqualTo("some/model");
        assertThat(body.path("instructions").asString()).isEqualTo("INSTRUCTIONS");
        assertThat(body.path("reasoning").path("effort").asString()).isEqualTo("medium");
        assertThat(body.path("text").path("format").path("type").asString()).isEqualTo("json_schema");
        assertThat(body.path("text").path("format").path("strict").asBoolean()).isTrue();
        assertThat(body.path("text").path("format").path("schema").path("properties").has("ruleIds")).isTrue();
        assertThat(body.path("provider").path("require_parameters").asBoolean()).isTrue();
        assertThat(body.has("store")).isFalse();
        assertThat(body.has("previous_response_id")).isFalse();
        assertThat(body.has("stream")).isFalse();
    }

    @Test
    void reasoningEffortNoneIsSentAsNone() throws Exception {
        var params = ResponsesRequestMapper.structured(
                request(ReasoningEffort.NONE, DecisionResult.class, new InputItem(Role.USER, "x", null)),
                DecisionResult.class);

        assertThat(body(params.rawParams()).path("reasoning").path("effort").asString()).isEqualTo("none");
    }

    @Test
    void nullReasoningEffortOmitsReasoning() throws Exception {
        var params = ResponsesRequestMapper.structured(
                request(null, DecisionResult.class, new InputItem(Role.USER, "x", null)), DecisionResult.class);

        assertThat(body(params.rawParams()).has("reasoning")).isFalse();
    }

    @Test
    void imageItemPutsTextBeforeImageAndUsesTheDataUrl() throws Exception {
        var params = ResponsesRequestMapper.structured(
                request(ReasoningEffort.LOW, ImageAnalysisResult.class, new InputItem(Role.USER, "CASE FACTS", DATA_URL)),
                ImageAnalysisResult.class);

        JsonNode content = body(params.rawParams()).path("input").path(0).path("content");

        assertThat(content).hasSize(2);
        assertThat(content.path(0).path("type").asString()).isEqualTo("input_text");
        assertThat(content.path(0).path("text").asString()).isEqualTo("CASE FACTS");
        assertThat(content.path(1).path("type").asString()).isEqualTo("input_image");
        assertThat(content.path(1).path("image_url").asString()).isEqualTo(DATA_URL);
    }

    @Test
    void inputItemsKeepTheirOrderAndRoles() throws Exception {
        var params = ResponsesRequestMapper.stream(request(ReasoningEffort.LOW, null,
                new InputItem(Role.USER, "CASE FACTS", null), new InputItem(Role.USER, "CUSTOMER REASON", null),
                new InputItem(Role.USER, "u1", null), new InputItem(Role.ASSISTANT, "a1", null),
                new InputItem(Role.USER, "DECISION", null)));

        JsonNode input = body(params).path("input");

        assertThat(input).hasSize(5);
        List<String> texts = List.of("CASE FACTS", "CUSTOMER REASON", "u1", "a1", "DECISION");
        List<String> roles = List.of("user", "user", "user", "assistant", "user");
        for (int i = 0; i < 5; i++) {
            assertThat(input.path(i).path("content").asString()).isEqualTo(texts.get(i));
            assertThat(input.path(i).path("role").asString()).isEqualTo(roles.get(i));
        }
    }

    @Test
    void streamRequestHasNoSchemaAndNoProviderBlock() throws Exception {
        var params = ResponsesRequestMapper.stream(
                request(ReasoningEffort.LOW, null, new InputItem(Role.USER, "x", null)));

        JsonNode body = body(params);

        assertThat(body.has("text")).isFalse();
        assertThat(body.has("provider")).isFalse();
        assertThat(body.path("reasoning").path("effort").asString()).isEqualTo("low");
        assertThat(body.has("store")).isFalse();
    }
}
