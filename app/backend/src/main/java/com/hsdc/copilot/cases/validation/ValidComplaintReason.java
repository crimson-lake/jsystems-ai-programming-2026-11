package com.hsdc.copilot.cases.validation;

import com.hsdc.copilot.cases.CaseForm;
import com.hsdc.copilot.cases.CaseType;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Class-level rule: a COMPLAINT needs a reason of at least 20 characters after trimming (AC-09). */
@Documented
@Constraint(validatedBy = ValidComplaintReason.Validator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidComplaintReason {

	int MIN_LENGTH = 20;

	String message() default "{form.reason.tooShort}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<ValidComplaintReason, CaseForm> {

		@Override
		public boolean isValid(CaseForm form, ConstraintValidatorContext context) {
			if (form == null || form.caseType() != CaseType.COMPLAINT) {
				return true;
			}
			String reason = form.reason();
			if (reason != null && reason.strip().length() >= MIN_LENGTH) {
				return true;
			}
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
				.addPropertyNode("reason")
				.addConstraintViolation();
			return false;
		}
	}
}
