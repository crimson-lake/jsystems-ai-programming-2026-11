package com.hsdc.copilot.triage;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.hsdc.copilot.triage.TriageTestSupport.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.config.TriageProperties.Mode;
import com.hsdc.copilot.triage.DecisionsApiClient.DecisionsResponse;

import tools.jackson.databind.JsonNode;

/**
 * ADR-004 section 8 "Fallback" at the client level: WireMock serves the verbatim S-2 captures
 * ({@code wiremock/triage/**}); errors, an 8 s timeout and a connection reset become {@link TriageUnavailableException}.
 */
class DecisionsApiClientIT {

    private static final String DIR = "wiremock/triage/";
    private static WireMockServer wm;

    private final TriageQuestionSet questions = new TriageQuestionSet(JSON);
    private DecisionsApiClient client;

    @BeforeAll
    static void start() {
        wm = new WireMockServer(options().dynamicPort().usingFilesUnderClasspath("wiremock/triage"));
        wm.start();
    }

    @AfterAll
    static void stop() {
        wm.stop();
    }

    @BeforeEach
    void reset() {
        wm.resetAll();
        client = new DecisionsApiClient(TriageTestSupport.properties(Mode.ASSIST, wm.baseUrl() + "/api/alpha/decisions"),
                TriageTestSupport.openRouter(), JSON);
    }

    private static void load(String... names) {
        for (String name : names) {
            wm.addStubMapping(StubMapping.buildFrom(TriageTestSupport.read(DIR + "stubs/" + name + ".json")));
        }
    }

    private static byte[] photo(String name) throws IOException {
        return Files.readAllBytes(Path.of("../../assets/example-images").resolve(name));
    }

    private static Map<String, Object> facts(String caseType, String category, String reason) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("caseType", caseType);
        facts.put("formCategory", category);
        facts.put("model", "Galaxy S23");
        facts.put("daysSincePurchase", 10);
        facts.put("reason", reason);
        return facts;
    }

    private DecisionsResponse askReturn() throws IOException {
        return client.ask("typesafe/jev-1.14", facts("RETURN", "SMARTPHONE", "Zmiana zdania."), photo("phone-1.jpg"),
                questions.forCase(CaseType.RETURN, "Zmiana zdania."));
    }

    // ------------------------------------------------------------------ success

    @Test
    void recordedReturnAnswer_isMapped_andTheRequestCarriesKeyAttributionAndAsset() throws IOException {
        load("return-ok", "complaint-ok");

        DecisionsResponse response = askReturn();

        assertThat(response.model()).isEqualTo("typesafe/jev-1.14-20261014");
        assertThat(response.answers()).hasSize(8);
        assertThat(response.answers().get("condition").value()).isEqualTo("DAMAGED");
        assertThat(wm.getAllServeEvents()).hasSize(1);
        LoggedRequest request = wm.getAllServeEvents().get(0).getRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-dummy-key");
        assertThat(request.getHeader("HTTP-Referer")).isEqualTo("http://localhost:8080");
        assertThat(request.getHeader("X-OpenRouter-Title")).isEqualTo("Hardware Service Decision Copilot PoC");
        assertThat(request.getHeader("Content-Type")).startsWith("application/json");
        JsonNode body = JSON.readTree(request.getBodyAsString());
        String assetId = body.path("state").get(1).asString();
        assertThat(body.path("assets").path(assetId).path("data_base64").asString()).doesNotStartWith("data:");
        assertThat(body.path("state").get(0).path("reason").asString()).isEqualTo("Zmiana zdania.");
    }

    @Test
    void recordedComplaintAnswer_isMapped() throws IOException {
        load("return-ok", "complaint-ok");
        String reason = "Urządzenie nie działa prawidłowo od kilku dni.";

        DecisionsResponse response = client.ask("typesafe/jev-1.14", facts("COMPLAINT", "LAPTOP", reason),
                photo("laptop-1.png"), questions.forCase(CaseType.COMPLAINT, reason));

        assertThat(response.answers().get("likely_cause").value()).isEqualTo("IMPACT");
        assertThat(response.inputTokens()).isEqualTo(7425);
    }

    // ------------------------------------------------------------------ fallbacks

    @Test
    void recorded401_isUnavailable() {
        load("error-401");
        assertThatThrownBy(this::askReturn).isInstanceOf(TriageUnavailableException.class)
            .satisfies(e -> assertThat(((TriageUnavailableException) e).isTimeout()).isFalse())
            .hasMessageNotContaining("User not found");
    }

    @Test
    void recorded400InvalidModel_isUnavailable() {
        load("error-400-invalid-model");
        assertThatThrownBy(this::askReturn).isInstanceOf(TriageUnavailableException.class);
    }

    @Test
    void delayPastEightSeconds_isATimeout_withoutRetry() {
        load("return-delayed");
        long start = System.nanoTime();

        assertThatThrownBy(this::askReturn).isInstanceOf(TriageUnavailableException.class)
            .satisfies(e -> assertThat(((TriageUnavailableException) e).isTimeout()).isTrue());

        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofSeconds(7)).isLessThan(Duration.ofSeconds(9));
        assertThat(wm.getAllServeEvents()).as("no retry").hasSizeLessThanOrEqualTo(1);
    }

    @Test
    void connectionReset_isUnavailable() {
        load("connection-reset");
        assertThatThrownBy(this::askReturn).isInstanceOf(TriageUnavailableException.class)
            .satisfies(e -> assertThat(((TriageUnavailableException) e).isTimeout()).isFalse());
        assertThat(wm.getAllServeEvents()).as("no retry").hasSize(1);
    }

    // ------------------------------------------------------------------ stub policy (plan section 4.5)

    @Test
    void everyStubIsAVerbatimCapture() throws IOException {
        Resource[] mappings = new PathMatchingResourcePatternResolver().getResources("classpath:" + DIR + "stubs/*.json");
        assertThat(mappings).hasSize(6);
        for (Resource mapping : mappings) {
            JsonNode node = JSON.readTree(new String(mapping.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            String source = node.path("metadata").path("source").asString();
            assertThat(source).startsWith("decisions-jev-");
            JsonNode meta = JSON.readTree(TriageTestSupport.capture(source + ".meta.json"));
            assertThat(node.path("metadata").path("addition").asString())
                .matches("none|fixed delay \\d+ ms|fault CONNECTION_RESET_BY_PEER");
            String bodyFile = node.path("response").path("bodyFileName").asString(null);
            if (bodyFile == null) {
                assertThat(node.path("response").path("fault").asString()).isEqualTo("CONNECTION_RESET_BY_PEER");
                continue;
            }
            assertThat(new ClassPathResource(DIR + "__files/" + bodyFile).getContentAsByteArray())
                .as(bodyFile + " is verbatim")
                .isEqualTo(new ClassPathResource("openrouter-captures/" + bodyFile).getContentAsByteArray());
            assertThat(node.path("response").path("status").asInt()).isEqualTo(meta.path("httpStatus").asInt());
            List<String> headers = node.path("response").path("headers").propertyNames().stream().toList();
            headers.forEach(h -> assertThat(meta.path("responseHeaders").path(h).asString())
                .isEqualTo(node.path("response").path("headers").path(h).asString()));
        }
    }
}
