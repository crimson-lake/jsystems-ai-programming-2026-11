package com.hsdc.copilot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BuildSanityTest {

	@Test
	void runsOnJava25() {
		assertThat(Runtime.version().feature()).isEqualTo(25);
	}

}
