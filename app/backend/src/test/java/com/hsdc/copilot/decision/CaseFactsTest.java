package com.hsdc.copilot.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/** Day counting (R2.3: day 1 is the day after purchase; C2.3: two years incl. 29 February). */
class CaseFactsTest {

	private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

	private static Clock on(LocalDate day) {
		return Clock.fixed(day.atTime(12, 0).atZone(WARSAW).toInstant(), WARSAW);
	}

	@Test
	void purchaseTodayIsDayZero() {
		LocalDate today = LocalDate.of(2026, 11, 26);
		CaseFacts facts = CaseFacts.of(today, on(today));
		assertThat(facts.today()).isEqualTo(today);
		assertThat(facts.daysSincePurchase()).isZero();
		assertThat(facts.returnWindowExceeded()).isFalse();
		assertThat(facts.complaintPeriodExceeded()).isFalse();
	}

	@Test
	void day30IsInsideAndDay31IsOutsideTheReturnWindow() {
		LocalDate today = LocalDate.of(2026, 11, 26);
		CaseFacts day30 = CaseFacts.of(today.minusDays(30), on(today));
		CaseFacts day31 = CaseFacts.of(today.minusDays(31), on(today));
		assertThat(day30.daysSincePurchase()).isEqualTo(30);
		assertThat(day30.returnWindowExceeded()).isFalse();
		assertThat(day31.daysSincePurchase()).isEqualTo(31);
		assertThat(day31.returnWindowExceeded()).isTrue();
	}

	@Test
	void complaintPeriodEndsTwoYearsAfterPurchase() {
		LocalDate today = LocalDate.of(2026, 11, 26);
		assertThat(CaseFacts.of(today.minusYears(2), on(today)).complaintPeriodExceeded()).isFalse();
		assertThat(CaseFacts.of(today.minusYears(2).minusDays(1), on(today)).complaintPeriodExceeded()).isTrue();
		assertThat(CaseFacts.of(today.minusYears(2).plusDays(1), on(today)).complaintPeriodExceeded()).isFalse();
	}

	@Test
	void twentyNinthOfFebruaryPurchase() {
		LocalDate purchase = LocalDate.of(2024, 2, 29);
		// plusYears(2) = 2026-02-28: the last day inside the period is 28 February 2026.
		assertThat(CaseFacts.of(purchase, on(LocalDate.of(2026, 2, 28))).complaintPeriodExceeded()).isFalse();
		assertThat(CaseFacts.of(purchase, on(LocalDate.of(2026, 3, 1))).complaintPeriodExceeded()).isTrue();
	}

	@Test
	void todayUsesTheClockZone() {
		// 23:30 UTC on 25 Nov is already 26 Nov in Warsaw.
		Clock clock = Clock.fixed(Instant.parse("2026-11-25T23:30:00Z"), WARSAW);
		CaseFacts facts = CaseFacts.of(LocalDate.of(2026, 11, 25), clock);
		assertThat(facts.today()).isEqualTo(LocalDate.of(2026, 11, 26));
		assertThat(facts.daysSincePurchase()).isEqualTo(1);
	}
}
