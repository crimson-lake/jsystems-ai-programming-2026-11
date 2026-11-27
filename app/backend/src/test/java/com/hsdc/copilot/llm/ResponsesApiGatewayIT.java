package com.hsdc.copilot.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.hsdc.copilot.analysis.ImageAnalysisResult;
import com.hsdc.copilot.analysis.ImageAnalysis.CauseConfidence;
import com.hsdc.copilot.analysis.ImageAnalysis.DamageType;
import com.hsdc.copilot.analysis.ImageAnalysis.LikelyCause;
import com.hsdc.copilot.analysis.ImageAnalysis.PhotoProblem;
import com.hsdc.copilot.decision.DecisionResult;
import com.hsdc.copilot.llm.LlmRequest.InputItem;
import com.hsdc.copilot.llm.LlmRequest.InputItem.Role;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T23: the Responses gateway against WireMock stubs copied verbatim from the S-1 captures
 * (wiremock/gateway/**). Only fixed delays and CONNECTION_RESET_BY_PEER are added to recorded behavior.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EnableWireMock(@ConfigureWireMock(name = "openrouter", filesUnderClasspath = "wiremock/gateway",
        baseUrlProperties = "it.wiremock.url"))
@TestPropertySource(properties = "openrouter.base-url=${it.wiremock.url}/api/v1")
class ResponsesApiGatewayIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String DATA_URL = "data:image/jpeg;base64,/9j/4AAQSkZJRg==";

    @Autowired
    private LlmGateway gateway;

    @InjectWireMock("openrouter")
    private WireMockServer wm;

    private ListAppender<ILoggingEvent> logs;
    private Logger gatewayLogger;

    @BeforeEach
    void setUp() {
        wm.resetAll();
        gatewayLogger = (Logger) LoggerFactory.getLogger(ResponsesApiGateway.class);
        logs = new ListAppender<>();
        logs.start();
        gatewayLogger.addAppender(logs);
        MDC.put(AiCallContext.CASE_ID_KEY, "case-123");
    }

    @AfterEach
    void tearDown() {
        gatewayLogger.detachAppender(logs);
        MDC.clear();
    }

    private void stubs(String... names) {
        for (String name : names) {
            wm.addStubMapping(StubMapping.buildFrom(read("wiremock/gateway/stubs/" + name + ".json")));
        }
    }

    private static String read(String location) {
        try (InputStream in = new ClassPathResource(location).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(location, e);
        }
    }

    private static LlmRequest visionRequest(Duration timeout) {
        return new LlmRequest("test/vision-model", "VISION INSTRUCTIONS",
                List.of(new InputItem(Role.USER, "CASE FACTS", DATA_URL)), ReasoningEffort.LOW,
                ImageAnalysisResult.class, timeout);
    }

    private static LlmRequest decisionRequest() {
        return new LlmRequest("test/decision-model", "DECISION INSTRUCTIONS",
                List.of(new InputItem(Role.USER, "CASE FACTS", null), new InputItem(Role.USER, "CUSTOMER REASON", null)),
                ReasoningEffort.MEDIUM, DecisionResult.class, Duration.ofSeconds(20));
    }

    private static LlmRequest streamRequest(Duration timeout) {
        return new LlmRequest("test/decision-model", "STREAM INSTRUCTIONS",
                List.of(new InputItem(Role.USER, "DECISION", null)), ReasoningEffort.LOW, null, timeout);
    }

    private List<LoggedRequest> requests() {
        return wm.getAllServeEvents().stream().map(e -> e.getRequest())
                .sorted((a, b) -> a.getLoggedDate().compareTo(b.getLoggedDate())).toList();
    }

    private List<String> aiCallLines() {
        return logs.list.stream().filter(e -> e.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage).filter(m -> m.startsWith("ai.call ")).toList();
    }

    private static String captureStreamText(String file) {
        StringBuilder sb = new StringBuilder();
        for (String line : read("openrouter-captures/" + file).split("\n")) {
            if (line.startsWith("data: {") && line.contains("\"type\":\"response.output_text.delta\"")) {
                sb.append(JSON.readTree(line.substring(6)).path("delta").asString());
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ structured

    @Test
    void parsesCaptureAIntoImageAnalysisResult() {
        stubs("vision-ok");

        ImageAnalysisResult result = gateway.structured(visionRequest(Duration.ofSeconds(20)), ImageAnalysisResult.class);

        assertThat(result.photoUsable()).isTrue();
        assertThat(result.photoProblem()).isEqualTo(PhotoProblem.NONE);
        assertThat(result.damageVisible()).isTrue();
        assertThat(result.damageType()).isEqualTo(DamageType.SCREEN_CRACK);
        assertThat(result.likelyCause()).isEqualTo(LikelyCause.IMPACT);
        assertThat(result.causeConfidence()).isEqualTo(CauseConfidence.HIGH);
        assertThat(result.safetyHazard()).isFalse();
        assertThat(result.triageDisagreements()).isEmpty();
        assertThat(result.summaryPl()).startsWith("Widoczny smartfon");
    }

    @Test
    void parsesCaptureBIntoDecisionResult() {
        stubs("decision-ok");

        DecisionResult result = gateway.structured(decisionRequest(), DecisionResult.class);

        assertThat(result.label()).isEqualTo(DecisionResult.Label.ACCEPTED_PRELIMINARY);
        assertThat(result.ruleIds()).containsExactly("R1");
        assertThat(result.reasonCode()).isEqualTo(DecisionResult.ReasonCode.RESELLABLE);
        assertThat(result.suggestOtherCaseType()).isEqualTo(DecisionResult.SuggestOtherCaseType.NONE);
    }

    @Test
    void structuredRequestSendsHeadersSchemaImageAndRequireParameters() {
        stubs("vision-ok", "decision-ok");

        gateway.structured(visionRequest(Duration.ofSeconds(20)), ImageAnalysisResult.class);
        gateway.structured(decisionRequest(), DecisionResult.class);

        List<LoggedRequest> sent = requests();
        assertThat(sent).hasSize(2);
        LoggedRequest vision = sent.get(0);
        assertThat(vision.getHeader("HTTP-Referer")).isEqualTo("http://localhost:8080");
        assertThat(vision.getHeader("X-OpenRouter-Title")).isEqualTo("Hardware Service Decision Copilot PoC");
        assertThat(vision.getHeader("Authorization")).isEqualTo("Bearer test-dummy-key");
        JsonNode v = JSON.readTree(vision.getBodyAsString());
        assertThat(v.path("model").asString()).isEqualTo("test/vision-model");
        assertThat(v.path("instructions").asString()).isEqualTo("VISION INSTRUCTIONS");
        JsonNode content = v.path("input").path(0).path("content");
        assertThat(content.path(0).path("type").asString()).isEqualTo("input_text");
        assertThat(content.path(1).path("type").asString()).isEqualTo("input_image");
        assertThat(content.path(1).path("image_url").asString()).isEqualTo(DATA_URL);
        assertThat(v.path("text").path("format").path("type").asString()).isEqualTo("json_schema");
        JsonNode d = JSON.readTree(sent.get(1).getBodyAsString());
        assertThat(d.path("provider").path("require_parameters").asBoolean()).isTrue();
        assertThat(d.path("reasoning").path("effort").asString()).isEqualTo("medium");
    }

    // ------------------------------------------------------------------ streaming

    @Test
    void forwardsOnlyOutputTextDeltasInOrderAndReturnsUsage() {
        stubs("stream-ok");
        List<String> deltas = new ArrayList<>();

        StreamResult result = gateway.stream(streamRequest(Duration.ofSeconds(20)), deltas::add, new CancelToken());

        String expected = captureStreamText("responses-c-streaming-text.sse");
        assertThat(expected).isNotBlank();
        assertThat(String.join("", deltas)).isEqualTo(expected);
        assertThat(deltas).hasSizeGreaterThan(10);
        assertThat(result.fullText()).isEqualTo(expected);
        assertThat(result.finishStatus()).isEqualTo(StreamResult.FinishStatus.COMPLETED);
        assertThat(result.inputTokens()).isPositive();
        assertThat(result.outputTokens()).isPositive();
        assertThat(String.join("", deltas)).doesNotContain("reasoning", "rs_");
    }

    @Test
    void incompleteStreamIsUnavailable() {
        stubs("stream-incomplete");

        assertThatThrownBy(() -> gateway.stream(streamRequest(Duration.ofSeconds(20)), d -> { }, new CancelToken()))
                .isInstanceOf(LlmUnavailableException.class);
        assertThat(aiCallLines()).singleElement().asString().contains("status=ERROR");
    }

    @Test
    void cancelTokenClosesTheStreamAndThrowsCancelled() {
        stubs("stream-ok");
        CancelToken token = new CancelToken();
        AtomicInteger deltas = new AtomicInteger();

        assertThatThrownBy(() -> gateway.stream(streamRequest(Duration.ofSeconds(20)), d -> {
            if (deltas.incrementAndGet() == 3) {
                token.cancel();
            }
        }, token)).isInstanceOf(LlmCancelledException.class);

        assertThat(deltas.get()).as("no deltas after cancellation took effect").isLessThan(30);
        assertThat(aiCallLines()).singleElement().asString().contains("status=CANCELLED");
    }

    @Test
    void alreadyCancelledTokenSendsNoRequest() {
        stubs("stream-ok");
        CancelToken token = new CancelToken();
        token.cancel();

        assertThatThrownBy(() -> gateway.stream(streamRequest(Duration.ofSeconds(20)), d -> { }, token))
                .isInstanceOf(LlmCancelledException.class);
        assertThat(requests()).isEmpty();
    }

    // ------------------------------------------------------------------ errors

    @Test
    void recorded401IsUnavailableLoggedAsAiConfigWithoutRetry() {
        stubs("vision-401");

        assertThatThrownBy(() -> gateway.structured(visionRequest(Duration.ofSeconds(20)), ImageAnalysisResult.class))
                .isInstanceOf(LlmUnavailableException.class);

        assertThat(requests()).as("no SDK retry").hasSize(1);
        assertThat(logs.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.ERROR);
            assertThat(e.getFormattedMessage()).contains("AI_CONFIG").contains("401").doesNotContain("test-dummy-key");
        });
    }

    @Test
    void recorded400IsUnavailableWithoutRetry() {
        stubs("vision-400");

        assertThatThrownBy(() -> gateway.structured(visionRequest(Duration.ofSeconds(20)), ImageAnalysisResult.class))
                .isInstanceOf(LlmUnavailableException.class);

        assertThat(requests()).hasSize(1);
        assertThat(logs.list).noneSatisfy(e -> assertThat(e.getFormattedMessage()).contains("AI_CONFIG"));
    }

    @Test
    void delayBeyondTheCallTimeoutIsTimeoutForStructuredCalls() {
        stubs("vision-delayed");

        assertThatThrownBy(() -> gateway.structured(visionRequest(Duration.ofSeconds(1)), ImageAnalysisResult.class))
                .isInstanceOf(LlmTimeoutException.class);
        assertThat(aiCallLines()).singleElement().asString().contains("status=TIMEOUT");
        assertThat(requests()).hasSize(1);
    }

    @Test
    void delayBeyondTheCallTimeoutIsTimeoutForStreamingCalls() {
        stubs("stream-delayed");

        assertThatThrownBy(() -> gateway.stream(streamRequest(Duration.ofSeconds(1)), d -> { }, new CancelToken()))
                .isInstanceOf(LlmTimeoutException.class);
    }

    @Test
    void connectionResetIsUnavailable() {
        stubs("stream-reset");

        assertThatThrownBy(() -> gateway.stream(streamRequest(Duration.ofSeconds(20)), d -> { }, new CancelToken()))
                .isInstanceOf(LlmUnavailableException.class);
        assertThat(requests()).as("no SDK retry").hasSize(1);
    }

    // ------------------------------------------------------------------ hygiene

    @Test
    void historyIsReplayedInOrderAndNothingIsStored() {
        stubs("history-ok", "stream-ok");
        LlmRequest history = new LlmRequest("test/decision-model", "Odpowiadaj po polsku.",
                List.of(new InputItem(Role.USER, "CASE FACTS", null), new InputItem(Role.USER, "CUSTOMER REASON", null),
                        new InputItem(Role.USER, "pierwsza wiadomosc", null),
                        new InputItem(Role.ASSISTANT, "odpowiedz asystenta", null),
                        new InputItem(Role.USER, "DECISION", null)),
                ReasoningEffort.LOW, null, Duration.ofSeconds(20));

        gateway.stream(history, d -> { }, new CancelToken());
        gateway.stream(streamRequest(Duration.ofSeconds(20)), d -> { }, new CancelToken());

        for (LoggedRequest request : requests()) {
            assertThat(request.getBodyAsString()).doesNotContain("\"store\":true").doesNotContain("previous_response_id");
        }
        JsonNode input = JSON.readTree(requests().get(0).getBodyAsString()).path("input");
        assertThat(input).hasSize(5);
        assertThat(input.path(0).path("content").asString()).isEqualTo("CASE FACTS");
        assertThat(input.path(1).path("content").asString()).isEqualTo("CUSTOMER REASON");
        assertThat(input.path(3).path("role").asString()).isEqualTo("assistant");
        assertThat(input.path(3).path("content").asString()).isEqualTo("odpowiedz asystenta");
        assertThat(input.path(4).path("content").asString()).isEqualTo("DECISION");
    }

    @Test
    void everySuccessfulAndFailedCallWritesExactlyOneEvidenceLine() {
        stubs("vision-ok", "decision-ok", "stream-ok");
        MDC.put(AiCallContext.STEP_KEY, AiStep.VISION.name());
        gateway.structured(visionRequest(Duration.ofSeconds(20)), ImageAnalysisResult.class);
        MDC.put(AiCallContext.STEP_KEY, AiStep.DECISION.name());
        gateway.structured(decisionRequest(), DecisionResult.class);
        MDC.put(AiCallContext.STEP_KEY, AiStep.STREAM.name());
        gateway.stream(streamRequest(Duration.ofSeconds(20)), d -> { }, new CancelToken());

        List<String> lines = aiCallLines();

        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).matches("ai\\.call step=VISION caseId=case-123 model=test/vision-model status=OK "
                + "latencyMs=\\d+ inTokens=1921 outTokens=279");
        assertThat(lines.get(1)).matches("ai\\.call step=DECISION caseId=case-123 model=test/decision-model status=OK "
                + "latencyMs=\\d+ inTokens=413 outTokens=326");
        assertThat(lines.get(2)).matches("ai\\.call step=STREAM caseId=case-123 model=test/decision-model status=OK "
                + "latencyMs=\\d+ inTokens=\\d+ outTokens=\\d+");
        assertThat(String.join("\n", lines)).doesNotContain("CASE FACTS", "VISION INSTRUCTIONS");
    }

    @Test
    void aiCallContextRestoresPreviousStep() {
        MDC.put(AiCallContext.STEP_KEY, "OUTER");

        String inside = AiCallContext.withStep(AiStep.DECISION_REASK, () -> MDC.get(AiCallContext.STEP_KEY));

        assertThat(inside).isEqualTo("DECISION_REASK");
        assertThat(MDC.get(AiCallContext.STEP_KEY)).isEqualTo("OUTER");
    }

    @Test
    void describeNamesModelsButNoSecrets() {
        assertThat(gateway.describe()).contains("test/vision-model", "test/decision-model").doesNotContain("test-dummy-key");
    }
}
