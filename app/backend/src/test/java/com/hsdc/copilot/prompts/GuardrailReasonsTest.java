package com.hsdc.copilot.prompts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

/** Fixed Polish reason texts per guardrail (ADR-001 section 3.4). */
class GuardrailReasonsTest {

	private static final List<String> KEYS = List.of("G1", "G2", "G3", "G4.photo", "G4.category", "G5", "G6",
			"model.default");

	private final GuardrailReasons reasons = new GuardrailReasons(new DefaultResourceLoader());

	@Test
	void allKeysArePresentAndNotBlank() {
		for (String key : KEYS) {
			assertThat(reasons.get(key)).as(key).isNotBlank();
		}
	}

	@Test
	void polishDiacriticsAreIntact() {
		assertThat(reasons.get("model.default"))
			.isEqualTo("Na podstawie nowych informacji zmieniliśmy ocenę Twojego zgłoszenia.");
		assertThat(reasons.get("G2")).contains("więc zwrot jest poza terminem");
		assertThat(reasons.get("G1")).contains("zagrożenie bezpieczeństwa");
	}

	@Test
	void unknownKeyIsRejected() {
		assertThatThrownBy(() -> reasons.get("G9")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void missingFileFailsStartup(@TempDir Path classpath) throws IOException {
		DefaultResourceLoader loader = new DefaultResourceLoader(
				new URLClassLoader(new URL[] { classpath.toUri().toURL() }, null));
		assertThatThrownBy(() -> new GuardrailReasons(loader)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("guardrail-reasons.pl.properties");
	}

	@Test
	void missingKeyFailsStartup(@TempDir Path classpath) throws IOException {
		Files.createDirectories(classpath.resolve("messages"));
		Files.writeString(classpath.resolve("messages/guardrail-reasons.pl.properties"), "G1=tekst\n",
				StandardCharsets.UTF_8);
		DefaultResourceLoader loader = new DefaultResourceLoader(
				new URLClassLoader(new URL[] { classpath.toUri().toURL() }, null));
		assertThatThrownBy(() -> new GuardrailReasons(loader)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("G2");
	}
}
