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

/** The string, trimmed, has {@code min}..{@code max} characters. A missing value is invalid. */
@Documented
@Constraint(validatedBy = TrimmedLength.Validator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface TrimmedLength {

	int min() default 0;

	int max() default Integer.MAX_VALUE;

	String message() default "{form.model.invalid}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<TrimmedLength, String> {

		private int min;
		private int max;

		@Override
		public void initialize(TrimmedLength annotation) {
			this.min = annotation.min();
			this.max = annotation.max();
		}

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			if (value == null) {
				return false;
			}
			int length = value.strip().length();
			return length >= min && length <= max;
		}
	}
}
