package com.hsdc.copilot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Guards ADR-000 section 7: the root .env is never imported under profile "test". */
@SpringBootTest
@ActiveProfiles("test")
class EnvNotLoadedUnderTestIT {

	@Autowired
	private ConfigurableEnvironment environment;

	@Test
	void noDotEnvPropertySourceUnderTestProfile() {
		for (PropertySource<?> source : environment.getPropertySources()) {
			assertThat(source.getName()).as("property source").doesNotContain(".env");
		}
	}

	@Test
	void apiKeyIsTheTestDummy() {
		assertThat(environment.getProperty("openrouter.api-key")).isEqualTo("test-dummy-key");
	}

}
