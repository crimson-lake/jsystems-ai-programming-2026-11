package com.hsdc.copilot.cases;

import java.util.Optional;

/** Photo check by magic bytes and size (ADR-002 section 3.6); the file name and content type are never trusted. */
public final class PhotoValidator {

	public static final int MAX_BYTES = 10 * 1024 * 1024;

	private PhotoValidator() {
	}

	/** Returns the message key of the violated rule, or empty when the photo is acceptable. */
	public static Optional<String> validate(byte[] content) {
		if (content == null || content.length == 0) {
			return Optional.of("form.photo.required");
		}
		if (content.length > MAX_BYTES) {
			return Optional.of("form.photo.size");
		}
		if (isJpeg(content) || isPng(content) || isWebp(content)) {
			return Optional.empty();
		}
		return Optional.of("form.photo.type");
	}

	private static boolean isJpeg(byte[] b) {
		return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
	}

	private static boolean isPng(byte[] b) {
		return b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
	}

	private static boolean isWebp(byte[] b) {
		return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
				&& b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
	}
}
