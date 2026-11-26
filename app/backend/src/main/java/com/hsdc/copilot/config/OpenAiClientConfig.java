package com.hsdc.copilot.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * The one {@link OpenAIClient} bean (ADR-001 section 3.1): settings only from {@link OpenRouterProperties}, never
 * from the SDK's {@code OPENAI_*} environment defaults; no SDK retries; per-call timeouts are set per request.
 */
@Configuration(proxyBeanMethods = false)
class OpenAiClientConfig {

    static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(60);

    @Bean(destroyMethod = "close")
    OpenAIClient openAiClient(OpenRouterProperties properties) {
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .maxRetries(0)
                .timeout(CLIENT_TIMEOUT);
        if (StringUtils.hasText(properties.appUrl())) {
            builder.putHeader("HTTP-Referer", properties.appUrl());
        }
        if (StringUtils.hasText(properties.appTitle())) {
            builder.putHeader("X-OpenRouter-Title", properties.appTitle());
        }
        return builder.build();
    }
}
