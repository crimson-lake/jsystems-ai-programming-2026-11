package com.hsdc.copilot.triage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;

import com.hsdc.copilot.config.OpenRouterProperties;
import com.hsdc.copilot.config.TriageProperties;
import com.hsdc.copilot.llm.ReasoningEffort;
import com.hsdc.copilot.triage.TriageAnswer.Band;
import com.hsdc.copilot.triage.TriageAnswer.Type;

import tools.jackson.databind.json.JsonMapper;

/** Shared fixtures of the triage tests. Wire inputs always come from the recorded S-2 captures. */
final class TriageTestSupport {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String JEV = "typesafe/jev-1.14";

    private TriageTestSupport() {
    }

    static TriageProperties properties(TriageProperties.Mode mode, String baseUrl) {
        return new TriageProperties(mode, JEV, 0.80, 0.95, 0.30, baseUrl, Duration.ofSeconds(8));
    }

    static OpenRouterProperties openRouter() {
        return new OpenRouterProperties("test-dummy-key", "http://localhost:0/api/v1", "test/vision-model",
                "test/decision-model", ReasoningEffort.MEDIUM, "Hardware Service Decision Copilot PoC",
                "http://localhost:8080");
    }

    static String read(String classpathLocation) {
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new AssertionError(classpathLocation, e);
        }
    }

    static String capture(String name) {
        return read("openrouter-captures/" + name);
    }

    /** A synthetic answer for router tests (no wire involved). */
    static TriageAnswer noul(String key, double p, double confidence) {
        return new TriageAnswer(key, Type.NOUL, p >= 0.5, p, null, Map.of("true", p, "false", 1 - p), confidence, false,
                band(confidence), false);
    }

    static TriageAnswer noul(String key, double p) {
        return new TriageAnswer(key, Type.NOUL, p >= 0.5, p, null, Map.of("true", p, "false", 1 - p),
                Math.max(p, 1 - p), true, band(Math.max(p, 1 - p)), false);
    }

    static TriageAnswer choice(String key, String value, double confidence) {
        return new TriageAnswer(key, Type.CHOICE, value, null, null, Map.of(value, confidence), confidence, false,
                band(confidence), false);
    }

    static TriageAnswer score(String key, String value, double confidence) {
        return new TriageAnswer(key, Type.SCORE, value, null, 0.0, Map.of(value, confidence), confidence, false,
                band(confidence), false);
    }

    static TriageAnswer refused(String key, Type type) {
        return new TriageAnswer(key, type, null, null, null, null, 0.0, false, Band.LOW, true);
    }

    static Band band(double confidence) {
        return confidence >= 0.95 ? Band.HIGH : confidence < 0.80 ? Band.LOW : Band.MEDIUM;
    }
}
