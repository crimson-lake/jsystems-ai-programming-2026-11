package com.hsdc.copilot.prompts;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Fixed Polish "change reason" texts per guardrail, from {@code messages/guardrail-reasons.pl.properties} (UTF-8). */
@Component
public class GuardrailReasons {

	static final List<String> KEYS = List.of("G1", "G2", "G3", "G4.photo", "G4.category", "G5", "G6",
			"model.default");

	private final Properties texts = new Properties();

	public GuardrailReasons(ResourceLoader resourceLoader) {
		String location = "classpath:messages/guardrail-reasons.pl.properties";
		try (InputStream in = resourceLoader.getResource(location).getInputStream();
				Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			texts.load(reader);
		}
		catch (IOException e) {
			throw new IllegalStateException("Guardrail reasons file missing or unreadable: messages/guardrail-reasons.pl.properties", e);
		}
		for (String key : KEYS) {
			if (texts.getProperty(key, "").isBlank()) {
				throw new IllegalStateException("Guardrail reason missing: " + key);
			}
		}
	}

	public String get(String key) {
		String text = texts.getProperty(key);
		if (text == null) {
			throw new IllegalArgumentException("Unknown guardrail reason key: " + key);
		}
		return text;
	}
}
