package com.hsdc.copilot.cases.validation;

import com.hsdc.copilot.config.AppProperties;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The application clock in zone {@code app.zone}; defines "today" for validation (and later for case facts). */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

	@Bean
	@ConditionalOnMissingBean(Clock.class)
	Clock applicationClock(AppProperties app) {
		return Clock.system(app.zone());
	}
}
