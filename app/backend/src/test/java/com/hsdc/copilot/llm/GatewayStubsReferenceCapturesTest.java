package com.hsdc.copilot.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** TAC-001-06: every gateway stub references an existing capture and is a verbatim copy of it. */
class GatewayStubsReferenceCapturesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static byte[] bytes(String location) throws IOException {
        try (var in = new ClassPathResource(location).getInputStream()) {
            return in.readAllBytes();
        }
    }

    @Test
    void everyStubReferencesAnExistingCaptureAndCopiesItVerbatim() throws IOException {
        Resource[] mappings = new PathMatchingResourcePatternResolver()
                .getResources("classpath:wiremock/gateway/stubs/*.json");
        assertThat(mappings).as("gateway stubs").hasSizeGreaterThanOrEqualTo(8);

        for (Resource mapping : mappings) {
            JsonNode node = JSON.readTree(new String(mapping.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            String source = node.path("metadata").path("source").asString();
            String name = mapping.getFilename();
            assertThat(new ClassPathResource("openrouter-captures/" + source + ".meta.json").exists())
                    .as(name + " references capture " + source).isTrue();

            String bodyFile = node.path("response").path("bodyFileName").asString(null);
            if (bodyFile == null) {
                assertThat(node.path("response").path("fault").asString()).as(name)
                        .isEqualTo("CONNECTION_RESET_BY_PEER");
                continue;
            }
            assertThat(bodyFile).as(name).startsWith(source);
            assertThat(bytes("wiremock/gateway/__files/" + bodyFile)).as(name + " body is a verbatim copy")
                    .isEqualTo(bytes("openrouter-captures/" + bodyFile));
            JsonNode meta = JSON.readTree(new String(bytes("openrouter-captures/" + source + ".meta.json"),
                    StandardCharsets.UTF_8));
            assertThat(node.path("response").path("status").asInt()).as(name + " status")
                    .isEqualTo(meta.path("httpStatus").asInt());
            node.path("response").path("headers").propertyNames().forEach(header -> assertThat(
                    meta.path("responseHeaders").has(header)).as(name + " header " + header + " is recorded").isTrue());
        }
    }

    @Test
    void onlyTransportFaultsAreAddedToStubs() throws IOException {
        Resource[] mappings = new PathMatchingResourcePatternResolver()
                .getResources("classpath:wiremock/gateway/stubs/*.json");
        for (Resource mapping : mappings) {
            JsonNode node = JSON.readTree(new String(mapping.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            String addition = node.path("metadata").path("addition").asString();
            assertThat(addition).as(mapping.getFilename()).matches("none|fixed delay \\d+ ms|fault CONNECTION_RESET_BY_PEER");
        }
    }
}
