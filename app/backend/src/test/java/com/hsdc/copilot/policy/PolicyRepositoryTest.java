package com.hsdc.copilot.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import com.hsdc.copilot.cases.CaseType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

/** Rule-ID extraction from the packaged policies (ADR-001 section 3.1; ADR-000 D-07). */
class PolicyRepositoryTest {

	/** From docs/policies/return-policy.md: sections R1-R7 and sub-rules R1.1...R5.3, R7.1 (R6 has bullets only). */
	private static final Set<String> RETURN_IDS = Set.of(
			"R1", "R2", "R3", "R4", "R5", "R6", "R7",
			"R1.1", "R1.2", "R2.1", "R2.2", "R2.3", "R3.1", "R3.2", "R3.3", "R3.4",
			"R4.1", "R4.2", "R5.1", "R5.2", "R5.3", "R7.1");

	/** From docs/policies/complaint-policy.md: sections C1-C8 and sub-rules C1.1...C7.1 (C3, C4, C8 have bullets only). */
	private static final Set<String> COMPLAINT_IDS = Set.of(
			"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8",
			"C1.1", "C1.2", "C2.1", "C2.2", "C2.3", "C5.1", "C5.2", "C5.3",
			"C6.1", "C6.2", "C6.3", "C7.1");

	private final PolicyRepository repository = new PolicyRepository(new DefaultResourceLoader());

	@Test
	void returnRuleIdsEqualThePolicyFile() {
		assertThat(repository.ruleIds(CaseType.RETURN)).isEqualTo(RETURN_IDS);
	}

	@Test
	void complaintRuleIdsEqualThePolicyFile() {
		assertThat(repository.ruleIds(CaseType.COMPLAINT)).isEqualTo(COMPLAINT_IDS);
	}

	@Test
	void textIsTheWholePolicyFile() {
		assertThat(repository.text(CaseType.RETURN)).startsWith("#").contains("R2.3. Day 1 is the day after");
		assertThat(repository.text(CaseType.COMPLAINT)).startsWith("#").contains("C2.3. A complaint about goods");
	}

	@Test
	void idSetsAreUnmodifiable() {
		Set<String> ids = repository.ruleIds(CaseType.RETURN);
		assertThatThrownBy(() -> ids.add("R99")).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void missingPolicyFileFailsStartup(@TempDir Path emptyClasspath) throws IOException {
		DefaultResourceLoader loader = new DefaultResourceLoader(classLoaderOf(emptyClasspath));
		assertThatThrownBy(() -> new PolicyRepository(loader))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("policies/return-policy.md");
	}

	@Test
	void onlyOnePolicyFileMissingStillFailsStartup(@TempDir Path classpath) throws IOException {
		Files.createDirectories(classpath.resolve("policies"));
		Files.writeString(classpath.resolve("policies/return-policy.md"), "## R1. Scope\nR1.1. x\n");
		DefaultResourceLoader loader = new DefaultResourceLoader(classLoaderOf(classpath));
		assertThatThrownBy(() -> new PolicyRepository(loader))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("policies/complaint-policy.md");
	}

	@Test
	void policyWithoutAnyRuleIdFailsStartup(@TempDir Path classpath) throws IOException {
		Files.createDirectories(classpath.resolve("policies"));
		Files.writeString(classpath.resolve("policies/return-policy.md"), "no rules here\n");
		Files.writeString(classpath.resolve("policies/complaint-policy.md"), "## C1. Scope\nC1.1. x\n");
		DefaultResourceLoader loader = new DefaultResourceLoader(classLoaderOf(classpath));
		assertThatThrownBy(() -> new PolicyRepository(loader))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("return-policy.md");
	}

	static ClassLoader classLoaderOf(Path dir) throws IOException {
		return new URLClassLoader(new URL[] { dir.toUri().toURL() }, null);
	}
}
