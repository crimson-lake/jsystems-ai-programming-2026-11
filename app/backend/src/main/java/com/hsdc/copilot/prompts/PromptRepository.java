package com.hsdc.copilot.prompts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Loads the six prompt files from {@code classpath:prompts/} at startup and fails fast if one is missing
 * (ADR-001 section 3.1). Placeholders {@code {{name}}} are filled by plain string replacement.
 */
@Component
public class PromptRepository {

	static final List<String> NAMES = List.of("decision-return", "decision-complaint", "message-first",
			"message-reply", "vision-return", "vision-complaint");

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[A-Za-z][A-Za-z0-9_]*}}");

	private final Map<String, String> prompts = new LinkedHashMap<>();

	public PromptRepository(ResourceLoader resourceLoader) {
		for (String name : NAMES) {
			String location = "classpath:prompts/" + name + ".md";
			try (InputStream in = resourceLoader.getResource(location).getInputStream()) {
				prompts.put(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
			}
			catch (IOException e) {
				throw new IllegalStateException("Prompt file missing or unreadable: prompts/" + name + ".md", e);
			}
		}
	}

	public Set<String> names() {
		return Set.copyOf(prompts.keySet());
	}

	/** The prompt text with placeholders intact. */
	public String raw(String name) {
		String text = prompts.get(name);
		if (text == null) {
			throw new IllegalArgumentException("Unknown prompt: " + name);
		}
		return text;
	}

	/**
	 * Replaces every {@code {{key}}} of the map; the value is inserted literally. A placeholder left unfilled
	 * is a programming error and fails with {@link IllegalStateException}.
	 */
	public String render(String name, Map<String, String> values) {
		String text = raw(name);
		for (Map.Entry<String, String> entry : values.entrySet()) {
			text = text.replace("{{" + entry.getKey() + "}}", entry.getValue());
		}
		Matcher left = PLACEHOLDER.matcher(text);
		if (left.find()) {
			throw new IllegalStateException("Unfilled placeholder " + left.group() + " in prompt " + name);
		}
		return text;
	}
}
