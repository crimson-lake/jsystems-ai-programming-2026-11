package com.hsdc.copilot.cases.validation;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import jakarta.validation.ConstraintViolation;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** Builds a Spring-backed Validator (SpringConstraintValidatorFactory) with a fixed Clock bean. */
final class TestValidators {

	static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
	/** "Today" in the application zone for all tests. */
	static final LocalDate TODAY = LocalDate.of(2026, 11, 26);
	static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), ZONE);

	private TestValidators() {
	}

	static LocalValidatorFactoryBean validator(Clock clock) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.registerBean(Clock.class, () -> clock);
		context.refresh();
		LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
		factory.setApplicationContext(context);
		factory.afterPropertiesSet();
		return factory;
	}

	static LocalValidatorFactoryBean validator() {
		return validator(CLOCK);
	}

	/** Violations as sorted "path: message" strings. */
	static Set<String> messages(Set<? extends ConstraintViolation<?>> violations) {
		Set<String> out = new TreeSet<>();
		violations.forEach(v -> out.add(v.getPropertyPath() + ": " + v.getMessage()));
		return out;
	}

	static Map<String, String> byPath(Set<? extends ConstraintViolation<?>> violations) {
		Map<String, String> out = new TreeMap<>();
		violations.forEach(v -> out.put(v.getPropertyPath().toString(), v.getMessage()));
		return out;
	}

	static {
		Locale.setDefault(Locale.forLanguageTag("pl-PL"));
	}
}
