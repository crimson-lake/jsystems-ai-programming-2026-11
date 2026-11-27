package com.hsdc.copilot.cases.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Purchase date is not in the future and not older than 10 years, relative to "today" of the injected
 * {@link Clock} (zone {@code app.zone}). Null is valid here; use {@code @NotNull} for "required".
 * The validator is created by Spring's {@code SpringConstraintValidatorFactory}, so the Clock bean is injected.
 */
@Documented
@Constraint(validatedBy = PurchaseDate.Validator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface PurchaseDate {

	String message() default "{form.purchaseDate.invalid}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<PurchaseDate, LocalDate> {

		private final Clock clock;

		public Validator(Clock clock) {
			this.clock = clock;
		}

		@Override
		public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
			if (value == null) {
				return true;
			}
			LocalDate today = LocalDate.now(clock);
			String key;
			if (value.isAfter(today)) {
				key = "{form.purchaseDate.future}";
			}
			else if (value.isBefore(today.minusYears(10))) {
				key = "{form.purchaseDate.tooOld}";
			}
			else {
				return true;
			}
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(key).addConstraintViolation();
			return false;
		}
	}
}
