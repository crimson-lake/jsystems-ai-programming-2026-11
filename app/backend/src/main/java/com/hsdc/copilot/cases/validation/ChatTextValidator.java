package com.hsdc.copilot.cases.validation;

import java.util.Optional;

/** Chat text rules (ADR-002 section 3.6): at most 2000 chars; without a photo at least one non-whitespace char. */
public final class ChatTextValidator {

	public static final int MAX_LENGTH = 2000;

	private ChatTextValidator() {
	}

	/** Returns the message key of the violated rule, or empty when the text is acceptable. */
	public static Optional<String> validate(String text, boolean hasPhoto) {
		String value = text == null ? "" : text;
		if (value.length() > MAX_LENGTH) {
			return Optional.of("chat.message.tooLong");
		}
		if (!hasPhoto && value.isBlank()) {
			return Optional.of("chat.message.empty");
		}
		return Optional.empty();
	}
}
