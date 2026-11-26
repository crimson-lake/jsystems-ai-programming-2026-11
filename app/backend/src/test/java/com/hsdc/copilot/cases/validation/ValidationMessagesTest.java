package com.hsdc.copilot.cases.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The backend message table equals the single source texts.pl.json (ADR-002 section 3.6, plan T20). */
class ValidationMessagesTest {

	private static final Path TEXTS = Path.of("../frontend/src/app/shared/texts.pl.json");

	private static final Set<String> BACKEND_KEYS = Set.of(
			"form.caseType.required", "form.category.required", "form.model.invalid",
			"form.purchaseDate.required", "form.purchaseDate.invalid", "form.purchaseDate.future",
			"form.purchaseDate.tooOld", "form.reason.tooShort", "form.reason.tooLong",
			"form.photo.required", "form.photo.type", "form.photo.size",
			"chat.message.empty", "chat.message.tooLong", "chat.photoLimit");

	private static Properties load(String resource) throws IOException {
		Properties properties = new Properties();
		try (InputStream in = ValidationMessagesTest.class.getResourceAsStream(resource);
				Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}

	@Test
	void polishBundleHasExactlyTheBackendKeysWithFrontendTexts() throws IOException {
		Map<String, String> frontend = JsonMapper.builder().build()
			.readValue(Files.readString(TEXTS, StandardCharsets.UTF_8), new TypeReference<Map<String, String>>() {
			});
		Properties backend = load("/ValidationMessages_pl.properties");

		assertThat(new TreeSet<>(backend.stringPropertyNames())).isEqualTo(new TreeSet<>(BACKEND_KEYS));
		for (String key : backend.stringPropertyNames()) {
			assertThat(frontend).as("frontend has key %s", key).containsKey(key);
			assertThat(backend.getProperty(key)).as("text of %s", key).isEqualTo(frontend.get(key));
		}
	}

	@Test
	void defaultBundleCarriesTheSamePolishTexts() throws IOException {
		assertThat(load("/ValidationMessages.properties")).isEqualTo(load("/ValidationMessages_pl.properties"));
	}
}
