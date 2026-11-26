package com.hsdc.copilot.decision;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Safety wording of G1 (ADR-001 section 3.4). Text is lower-cased and stripped of diacritics before matching.
 * A word ending in {@code *} is a stem (any word starting with it); other words must match a whole word.
 */
final class SafetyLexicon {

	private static final List<String> ENTRIES = List.of("spuch*", "napuch*", "puchnie", "wybrzusz*", "dym*", "iskr*",
			"przypal*", "nadpal*", "spalon*", "spalenizn*", "swad*", "pali sie", "zapalil*", "ogien", "pozar",
			"przegrzew*", "goraca bateria", "odslonie* przewod*");

	private static final List<Pattern> PATTERNS = ENTRIES.stream().map(SafetyLexicon::compile).toList();

	private SafetyLexicon() {
	}

	static boolean matches(String text) {
		String normalized = normalize(text);
		return PATTERNS.stream().anyMatch(p -> p.matcher(normalized).find());
	}

	static String normalize(String text) {
		String lower = text.toLowerCase(Locale.ROOT).replace('ł', 'l');
		return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
	}

	private static Pattern compile(String entry) {
		StringBuilder regex = new StringBuilder("(?<![\\p{L}\\p{N}])");
		String[] words = entry.split(" ");
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			boolean stem = word.endsWith("*");
			regex.append(i == 0 ? "" : "\\s+")
				.append(Pattern.quote(stem ? word.substring(0, word.length() - 1) : word))
				.append(stem ? "\\p{L}*" : "");
		}
		return Pattern.compile(regex.append("(?![\\p{L}\\p{N}])").toString());
	}
}
