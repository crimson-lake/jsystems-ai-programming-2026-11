package com.hsdc.copilot.decision;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces AC-23 on the streamed text (ADR-001 section 3.1): bracketed rule IDs that are not in
 * {@code decision.ruleIds} are removed. Only the exact shape {@code [R2.3]} / {@code [C8]}, or a bracketed list of
 * such IDs separated by commas or semicolons ({@code [R2.2, R9.9]}), is touched, so model names such as "LG C3" or
 * "Canon R5" and other brackets stay. In a list the disallowed IDs are dropped (the whole bracket when none is left). A partial token at the end of a delta is held back until it completes or proves
 * not to be an ID. One instance per streamed message; not thread-safe.
 */
public final class RuleIdStreamFilter {

	private static final Logger LOG = LoggerFactory.getLogger(RuleIdStreamFilter.class);
	private static final String ID = "[RC]\\d{1,2}(?:\\.\\d{1,2})?";
	private static final Pattern TOKEN = Pattern.compile("\\[(" + ID + "(?:\\s*[,;]\\s*" + ID + ")*)]");
	private static final Pattern SEPARATOR = Pattern.compile("\\s*[,;]\\s*");
	private static final Pattern FIRST_SEPARATOR = Pattern.compile("[,;]");
	private static final Pattern UNBRACKETED = Pattern.compile("(?<!\\[)\\b[RC]\\d{1,2}(?:\\.\\d{1,2})?\\b(?!])");

	private final UUID caseId;
	private final Set<String> allowed;
	private final StringBuilder emitted = new StringBuilder();
	private StringBuilder pending;
	private StringBuilder current;
	private int removed;

	public RuleIdStreamFilter(UUID caseId, Collection<String> allowedIds) {
		this.caseId = caseId;
		this.allowed = Set.copyOf(allowedIds);
	}

	/** Consumes one delta and returns the text that may be sent to the customer now. */
	public String accept(String delta) {
		current = new StringBuilder();
		for (int i = 0; i < delta.length(); i++) {
			feed(delta.charAt(i));
		}
		emitted.append(current);
		return current.toString();
	}

	/** Ends the stream: a held-back partial token is not an ID and is released as it is. */
	public String flush() {
		String rest = pending == null ? "" : pending.toString();
		pending = null;
		emitted.append(rest);
		return rest;
	}

	/** Everything delivered so far; this is the text stored in history and used for replay. */
	public String filteredText() {
		return emitted.toString();
	}

	public int removedCount() {
		return removed;
	}

	/** Number of ID-like words without brackets in the delivered text (reported by evals, never removed). */
	public int unbracketedIdLikeCount() {
		Matcher matcher = UNBRACKETED.matcher(emitted);
		int count = 0;
		while (matcher.find()) {
			count++;
		}
		return count;
	}

	private void feed(char c) {
		if (pending == null) {
			startOrEmit(c);
			return;
		}
		pending.append(c);
		Matcher matcher = TOKEN.matcher(pending);
		if (matcher.matches()) {
			resolve(matcher.group(1));
		}
		else if (!matcher.hitEnd()) {
			String text = pending.substring(0, pending.length() - 1);
			pending = null;
			current.append(text);
			startOrEmit(c);
		}
	}

	private void startOrEmit(char c) {
		if (c == '[') {
			pending = new StringBuilder("[");
		}
		else {
			current.append(c);
		}
	}

	/** {@code content} is one ID or an ID list; the bracket stays verbatim when every ID is allowed. */
	private void resolve(String content) {
		String[] ids = SEPARATOR.split(content);
		List<String> kept = new ArrayList<>();
		for (String id : ids) {
			if (allowed.contains(id)) {
				kept.add(id);
			}
			else {
				removed++;
				LOG.warn("Removed rule ID [{}] not in decision.ruleIds from streamed text, caseId={}", id, caseId);
			}
		}
		String all = pending.toString();
		pending = null;
		if (kept.isEmpty()) {
			return;
		}
		if (kept.size() == ids.length) {
			current.append(all);
			return;
		}
		// a partial list always has a separator: rebuild it with the first one used
		Matcher first = FIRST_SEPARATOR.matcher(content);
		first.find();
		current.append('[').append(String.join(first.group() + " ", kept)).append(']');
	}
}
