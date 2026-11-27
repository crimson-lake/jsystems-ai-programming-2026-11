package com.hsdc.copilot.cases;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Magic-byte photo check (ADR-002 section 3.6). Real example photos are used first; byte-level fixtures appear
 * only in these validator unit tests (PRD section 12 exception).
 */
class PhotoValidatorTest {

	private static final Path EXAMPLES = Path.of("../../assets/example-images");
	private static final int TEN_MB = 10 * 1024 * 1024;

	private static byte[] withHeader(int size, int... header) {
		byte[] bytes = new byte[size];
		for (int i = 0; i < header.length; i++) {
			bytes[i] = (byte) header[i];
		}
		return bytes;
	}

	private static byte[] webp(int size) {
		byte[] bytes = new byte[size];
		System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
		System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, bytes, 8, 4);
		return bytes;
	}

	@ParameterizedTest
	@ValueSource(strings = { "laptop-1.png", "laptop-2.webp", "phone-1.jpg", "phone-2.jpeg", "phone-3.jpeg" })
	void realExamplePhotosAreAccepted(String file) throws IOException {
		assertThat(PhotoValidator.validate(Files.readAllBytes(EXAMPLES.resolve(file)))).isEmpty();
	}

	@Test
	void synthesizedHeadersOfAllowedFormatsAreAccepted() {
		assertThat(PhotoValidator.validate(withHeader(16, 0xFF, 0xD8, 0xFF, 0xE0))).isEmpty();
		assertThat(PhotoValidator.validate(withHeader(16, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))).isEmpty();
		assertThat(PhotoValidator.validate(webp(16))).isEmpty();
	}

	@Test
	void nullAndEmptyAreRequired() {
		assertThat(PhotoValidator.validate(null)).contains("form.photo.required");
		assertThat(PhotoValidator.validate(new byte[0])).contains("form.photo.required");
	}

	@Test
	void otherFormatsAreRejectedAsWrongType() {
		assertThat(PhotoValidator.validate(withHeader(16, 'G', 'I', 'F', '8', '9', 'a'))).contains("form.photo.type");
		// HEIC: ftyp box at offset 4
		byte[] heic = withHeader(24, 0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c');
		assertThat(PhotoValidator.validate(heic)).contains("form.photo.type");
		assertThat(PhotoValidator.validate("to nie jest zdjęcie".getBytes(StandardCharsets.UTF_8)))
			.contains("form.photo.type");
	}

	@Test
	void truncatedOrAlmostMatchingHeadersAreRejected() {
		assertThat(PhotoValidator.validate(withHeader(2, 0xFF, 0xD8))).contains("form.photo.type");
		assertThat(PhotoValidator.validate(withHeader(3, 0x89, 0x50, 0x4E))).contains("form.photo.type");
		assertThat(PhotoValidator.validate(withHeader(16, 0xFF, 0xD8, 0x00))).contains("form.photo.type");
		byte[] riffNotWebp = webp(16);
		riffNotWebp[8] = 'A'; // RIFF....AVI-like
		assertThat(PhotoValidator.validate(riffNotWebp)).contains("form.photo.type");
		assertThat(PhotoValidator.validate(Arrays.copyOf(webp(16), 11))).contains("form.photo.type");
	}

	@Test
	void sizeBoundaryIsTenMegabytesInclusive() {
		assertThat(PhotoValidator.validate(withHeader(TEN_MB, 0xFF, 0xD8, 0xFF))).isEmpty();
		assertThat(PhotoValidator.validate(withHeader(TEN_MB + 1, 0xFF, 0xD8, 0xFF))).contains("form.photo.size");
	}

	@Test
	void oversizedFileIsReportedAsSizeEvenWhenNotAnImage() {
		assertThat(PhotoValidator.validate(new byte[TEN_MB + 1])).contains("form.photo.size");
	}

	@Test
	void resultIsAnOptionalKey() {
		Optional<String> result = PhotoValidator.validate(new byte[0]);
		assertThat(result).isPresent();
	}
}
