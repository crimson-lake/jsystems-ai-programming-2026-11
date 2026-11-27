package com.hsdc.copilot.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** TAC-001-01: the OpenAI SDK stays behind the gateway; only package {@code com.hsdc.copilot.llm} imports it. */
class OpenAiImportScanTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path LLM_DIR = MAIN.resolve("com/hsdc/copilot/llm");
    /**
     * The single documented exception: the Spring configuration that builds the one OpenAIClient bean
     * (ADR-001 section 3.1) lives in package config (plan T23 file list).
     */
    private static final Path CLIENT_CONFIG = MAIN.resolve("com/hsdc/copilot/config/OpenAiClientConfig.java");

    @Test
    void onlyTheLlmPackageImportsTheSdk() throws IOException {
        assertThat(MAIN).as("source root, tests run in the module directory").isDirectory();
        List<Path> importers;
        try (Stream<Path> files = Files.walk(MAIN)) {
            importers = files.filter(p -> p.toString().endsWith(".java")).filter(OpenAiImportScanTest::importsSdk).toList();
        }

        assertThat(importers).as("the gateway must import the SDK").isNotEmpty();
        assertThat(importers).allSatisfy(p -> assertThat(p.startsWith(LLM_DIR) || p.equals(CLIENT_CONFIG))
                .as(p + " imports com.openai outside package llm").isTrue());
    }

    private static boolean importsSdk(Path file) {
        try {
            return Files.readAllLines(file).stream().anyMatch(l -> l.strip().matches("import\\s+(static\\s+)?com\\.openai\\..*"));
        } catch (IOException e) {
            throw new IllegalStateException(file.toString(), e);
        }
    }
}
