package com.hsdc.copilot.policy;

import com.hsdc.copilot.cases.CaseType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Loads the two policies from {@code classpath:policies/} (copied there by the Maven build) and extracts the set
 * of valid rule IDs (ADR-001 section 3.1). A missing or rule-less file fails startup (ADR-000 D-07).
 */
@Component
public class PolicyRepository {

	private static final Pattern SECTION_ID = Pattern.compile("^## ((?:R|C)\\d+)\\.", Pattern.MULTILINE);
	private static final Pattern SUB_RULE_ID = Pattern.compile("^((?:R|C)\\d+\\.\\d+)\\.", Pattern.MULTILINE);

	private final Map<CaseType, String> texts = new EnumMap<>(CaseType.class);
	private final Map<CaseType, Set<String>> ruleIds = new EnumMap<>(CaseType.class);

	public PolicyRepository(ResourceLoader resourceLoader) {
		load(resourceLoader, CaseType.RETURN, "classpath:policies/return-policy.md");
		load(resourceLoader, CaseType.COMPLAINT, "classpath:policies/complaint-policy.md");
	}

	/** Full policy text, injected into the prompts as {@code {{policy}}}. */
	public String text(CaseType type) {
		return texts.get(type);
	}

	/** Valid rule IDs of the policy, e.g. {@code R2.3}, {@code R6}. Unmodifiable. */
	public Set<String> ruleIds(CaseType type) {
		return ruleIds.get(type);
	}

	private void load(ResourceLoader loader, CaseType type, String location) {
		Resource resource = loader.getResource(location);
		String text;
		try (InputStream in = resource.getInputStream()) {
			text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new IllegalStateException("Policy file missing or unreadable: " + location, e);
		}
		Set<String> ids = new LinkedHashSet<>();
		collect(SECTION_ID, text, ids);
		collect(SUB_RULE_ID, text, ids);
		if (ids.isEmpty()) {
			throw new IllegalStateException("Policy file contains no rule IDs: " + location);
		}
		texts.put(type, text);
		ruleIds.put(type, Collections.unmodifiableSet(ids));
	}

	private static void collect(Pattern pattern, String text, Set<String> into) {
		Matcher matcher = pattern.matcher(text);
		while (matcher.find()) {
			into.add(matcher.group(1));
		}
	}
}
