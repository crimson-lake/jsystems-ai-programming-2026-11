package com.hsdc.copilot.cases.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Chat text rules: 0-2000 chars; 1-2000 non-whitespace chars when no photo is attached (ADR-002 3.6). */
class ChatTextValidatorTest {

	@Test
	void textWithoutPhotoMustHaveNonWhitespaceContent() {
		assertThat(ChatTextValidator.validate(null, false)).contains("chat.message.empty");
		assertThat(ChatTextValidator.validate("", false)).contains("chat.message.empty");
		assertThat(ChatTextValidator.validate(" \t\n ", false)).contains("chat.message.empty");
		assertThat(ChatTextValidator.validate("a", false)).isEmpty();
		assertThat(ChatTextValidator.validate("  a  ", false)).isEmpty();
	}

	@Test
	void photoAllowsEmptyOrBlankText() {
		assertThat(ChatTextValidator.validate(null, true)).isEmpty();
		assertThat(ChatTextValidator.validate("", true)).isEmpty();
		assertThat(ChatTextValidator.validate("   ", true)).isEmpty();
	}

	@Test
	void lengthLimitIsTwoThousandWithOrWithoutPhoto() {
		assertThat(ChatTextValidator.validate("x".repeat(2000), false)).isEmpty();
		assertThat(ChatTextValidator.validate("x".repeat(2001), false)).contains("chat.message.tooLong");
		assertThat(ChatTextValidator.validate("x".repeat(2000), true)).isEmpty();
		assertThat(ChatTextValidator.validate("x".repeat(2001), true)).contains("chat.message.tooLong");
	}
}
