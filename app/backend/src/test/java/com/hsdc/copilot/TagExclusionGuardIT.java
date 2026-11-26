package com.hsdc.copilot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Runs only with -Dtest.excludedGroups= -Dgroups=eval. If it runs in a default build, the tag
 * exclusion is broken and real-API tests would hit the network.
 */
@Tag("eval")
class TagExclusionGuardIT {

	@Test
	void runsOnlyWhenEvalGroupIsExplicitlySelected() {
		assertThat(true).isTrue();
	}

}
