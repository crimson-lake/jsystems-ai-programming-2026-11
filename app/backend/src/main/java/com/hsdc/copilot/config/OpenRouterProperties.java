package com.hsdc.copilot.config;

import com.hsdc.copilot.llm.ReasoningEffort;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** OpenRouter settings (ADR-001 section 3.1). The key is required: startup fails without it. */
@Validated
@ConfigurationProperties("openrouter")
public record OpenRouterProperties(
        @NotBlank String apiKey,
        String baseUrl,
        @NotBlank String visionModel,
        @NotBlank String decisionModel,
        ReasoningEffort decisionReasoningEffort,
        String appTitle,
        String appUrl) {
}
