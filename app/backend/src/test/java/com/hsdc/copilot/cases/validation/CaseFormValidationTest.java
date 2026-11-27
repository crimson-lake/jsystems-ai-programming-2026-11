package com.hsdc.copilot.cases.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import com.hsdc.copilot.cases.CaseForm;
import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.cases.Category;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** Bean Validation of CaseForm (ADR-002 section 3.6; PRD AC-04/05/07/09/10). */
class CaseFormValidationTest {

	private static final LocalValidatorFactoryBean VALIDATOR = TestValidators.validator();
	private static final LocalDate TODAY = TestValidators.TODAY;

	private static final String MODEL_INVALID = "Podaj nazwę lub model urządzenia (2–100 znaków)";
	private static final String REASON_SHORT = "Opisz problem (min. 20 znaków)";
	private static final String REASON_LONG = "Opis może mieć maksymalnie 2000 znaków.";
	private static final String DATE_FUTURE = "Data zakupu nie może być w przyszłości";
	private static final String DATE_OLD = "Data zakupu jest zbyt odległa";

	private static CaseForm form(String model, LocalDate date, CaseType type, String reason) {
		return new CaseForm(type, Category.LAPTOP, model, date, reason);
	}

	private static Map<String, String> validate(CaseForm form) {
		return TestValidators.byPath(VALIDATOR.validate(form));
	}

	@Test
	void validReturnFormHasNoViolations() {
		assertThat(validate(form("ThinkPad T14", TODAY.minusDays(3), CaseType.RETURN, null))).isEmpty();
		assertThat(validate(form("ThinkPad T14", TODAY.minusDays(3), CaseType.RETURN, ""))).isEmpty();
	}

	@Test
	void requiredFieldsAreReportedWithPolishTexts() {
		Map<String, String> v = validate(new CaseForm(null, null, "Galaxy", null, null));
		assertThat(v).containsEntry("caseType", "Wybierz typ zgłoszenia.")
			.containsEntry("category", "Wybierz kategorię sprzętu.")
			.containsEntry("purchaseDate", "Podaj datę zakupu.")
			.hasSize(3);
	}

	@ParameterizedTest
	@CsvSource({ "1,false", "2,true", "100,true", "101,false" })
	void modelLengthBoundaries(int length, boolean valid) {
		Map<String, String> v = validate(form("x".repeat(length), TODAY, CaseType.RETURN, null));
		assertThat(v.containsKey("model")).isEqualTo(!valid);
		if (!valid) {
			assertThat(v).containsEntry("model", MODEL_INVALID);
		}
	}

	@Test
	void modelIsTrimmedBeforeMeasuring() {
		assertThat(validate(form("  a  ", TODAY, CaseType.RETURN, null))).containsEntry("model", MODEL_INVALID);
		assertThat(validate(form("  ab  ", TODAY, CaseType.RETURN, null))).doesNotContainKey("model");
		assertThat(validate(form(" " + "x".repeat(100) + " ", TODAY, CaseType.RETURN, null)))
			.doesNotContainKey("model");
		assertThat(validate(form("   ", TODAY, CaseType.RETURN, null))).containsEntry("model", MODEL_INVALID);
		assertThat(validate(form(null, TODAY, CaseType.RETURN, null))).containsEntry("model", MODEL_INVALID);
	}

	@ParameterizedTest
	@CsvSource({ "19,false", "20,true" })
	void complaintReasonMinimumIsTwentyTrimmedChars(int length, boolean valid) {
		Map<String, String> v = validate(form("Galaxy", TODAY, CaseType.COMPLAINT, "x".repeat(length)));
		assertThat(v.containsKey("reason")).isEqualTo(!valid);
		if (!valid) {
			assertThat(v).containsEntry("reason", REASON_SHORT);
		}
	}

	@Test
	void complaintReasonIsTrimmedAndMayNotBeMissing() {
		assertThat(validate(form("Galaxy", TODAY, CaseType.COMPLAINT, "  " + "x".repeat(19) + "   ")))
			.containsEntry("reason", REASON_SHORT);
		assertThat(validate(form("Galaxy", TODAY, CaseType.COMPLAINT, "  " + "x".repeat(20) + "   ")))
			.doesNotContainKey("reason");
		assertThat(validate(form("Galaxy", TODAY, CaseType.COMPLAINT, null))).containsEntry("reason", REASON_SHORT);
		assertThat(validate(form("Galaxy", TODAY, CaseType.COMPLAINT, "")).get("reason")).isEqualTo(REASON_SHORT);
	}

	@Test
	void shortReasonIsAllowedForReturnButCapIsEnforcedForBoth() {
		assertThat(validate(form("Galaxy", TODAY, CaseType.RETURN, "krótko"))).isEmpty();
		assertThat(validate(form("Galaxy", TODAY, CaseType.RETURN, "x".repeat(2000)))).isEmpty();
		assertThat(validate(form("Galaxy", TODAY, CaseType.RETURN, "x".repeat(2001))))
			.containsEntry("reason", REASON_LONG);
		assertThat(validate(form("Galaxy", TODAY, CaseType.COMPLAINT, "x".repeat(2001))))
			.containsEntry("reason", REASON_LONG);
	}

	@Test
	void purchaseDateBoundaries() {
		assertThat(validate(form("Galaxy", TODAY, CaseType.RETURN, null))).isEmpty();
		assertThat(validate(form("Galaxy", TODAY.plusDays(1), CaseType.RETURN, null)))
			.containsEntry("purchaseDate", DATE_FUTURE);
		assertThat(validate(form("Galaxy", TODAY.minusYears(10), CaseType.RETURN, null))).isEmpty();
		assertThat(validate(form("Galaxy", TODAY.minusYears(10).minusDays(1), CaseType.RETURN, null)))
			.containsEntry("purchaseDate", DATE_OLD);
	}

	@Test
	void todayIsComputedInTheApplicationZone() {
		// 2026-11-25T23:30Z is already 26 November in Europe/Warsaw (UTC+1).
		Clock lateEvening = Clock.fixed(Instant.parse("2026-11-25T23:30:00Z"), TestValidators.ZONE);
		LocalValidatorFactoryBean validator = TestValidators.validator(lateEvening);
		assertThat(TestValidators.byPath(validator.validate(form("Galaxy", TODAY, CaseType.RETURN, null)))).isEmpty();
		assertThat(TestValidators.byPath(validator.validate(form("Galaxy", TODAY.plusDays(1), CaseType.RETURN, null))))
			.containsEntry("purchaseDate", DATE_FUTURE);
	}
}
