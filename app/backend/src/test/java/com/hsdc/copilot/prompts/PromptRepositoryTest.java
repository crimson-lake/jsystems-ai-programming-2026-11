package com.hsdc.copilot.prompts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

/** Prompt loading and rendering (ADR-001 sections 3.1-3.2). */
class PromptRepositoryTest {

	private static final List<String> ALL = List.of(
			"decision-return", "decision-complaint", "message-first", "message-reply", "vision-return",
			"vision-complaint");

	private final PromptRepository repository = new PromptRepository(new DefaultResourceLoader());

	@Test
	void allSixPromptsLoadAndHaveNoPlaceholderMarker() {
		assertThat(repository.names()).containsExactlyInAnyOrderElementsOf(ALL);
		for (String name : ALL) {
			assertThat(repository.raw(name)).as(name).isNotBlank().doesNotContain("PLACEHOLDER");
		}
	}

	@Test
	void decisionAndMessagePromptsContainThePolicyPlaceholder() {
		for (String name : List.of("decision-return", "decision-complaint", "message-first", "message-reply")) {
			assertThat(repository.raw(name)).as(name).contains("{{policy}}");
		}
	}

	@Test
	void visionPromptsContainNoPlaceholders() {
		for (String name : List.of("vision-return", "vision-complaint")) {
			assertThat(repository.raw(name)).as(name).doesNotContain("{{");
		}
	}

	@Test
	void renderReplacesEveryOccurrence() {
		String rendered = repository.render("decision-return", Map.of("policy", "POLICY-TEXT"));
		assertThat(rendered).contains("POLICY-TEXT").doesNotContain("{{").doesNotContain("}}");
	}

	@Test
	void renderIsPlainReplacementNotRegex() {
		String rendered = repository.render("message-first", Map.of("policy", "R2.3 $1 \\ costs $5"));
		assertThat(rendered).contains("R2.3 $1 \\ costs $5");
	}

	@Test
	void renderFailsOnUnknownPromptOrUnfilledPlaceholder() {
		assertThatThrownBy(() -> repository.render("nope", Map.of()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> repository.raw("nope")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> repository.render("decision-return", Map.of()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("{{policy}}");
	}

	@Test
	void renderLeavesTextWithoutPlaceholdersUnchanged() {
		assertThat(repository.render("vision-return", Map.of("unused", "x"))).isEqualTo(repository.raw("vision-return"));
	}

	@Test
	void missingPromptFileFailsStartup(@TempDir Path classpath) throws IOException {
		Files.createDirectories(classpath.resolve("prompts"));
		for (String name : ALL.subList(1, ALL.size())) {
			Files.writeString(classpath.resolve("prompts/" + name + ".md"), "text");
		}
		DefaultResourceLoader loader = new DefaultResourceLoader(
				new URLClassLoader(new URL[] { classpath.toUri().toURL() }, null));
		assertThatThrownBy(() -> new PromptRepository(loader))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("prompts/decision-return.md");
	}

	@Test
	void emptyClasspathFailsStartup(@TempDir Path classpath) throws IOException {
		DefaultResourceLoader loader = new DefaultResourceLoader(
				new URLClassLoader(new URL[] { classpath.toUri().toURL() }, null));
		assertThatThrownBy(() -> new PromptRepository(loader)).isInstanceOf(IllegalStateException.class);
	}
}
