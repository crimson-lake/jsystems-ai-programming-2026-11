package com.hsdc.copilot.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Triage settings (ADR-004 section 3.2). */
@ConfigurationProperties("triage")
public record TriageProperties(
        Mode mode,
        String model,
        double lowConfidence,
        double highConfidence,
        double safetyProbability,
        String baseUrl,
        Duration timeout) {

    public enum Mode {
        OFF, ASSIST, FAST_PATH
    }
}
