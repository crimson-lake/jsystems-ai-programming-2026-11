package com.hsdc.copilot.decision;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Facts computed in code, never asked from the model (ADR-001 section 3.1).
 *
 * @param today                  today in the application zone (the clock's zone)
 * @param daysSincePurchase      day 1 is the day after the purchase date (R2.3)
 * @param returnWindowExceeded   more than 30 days since purchase
 * @param complaintPeriodExceeded today is after purchase date + 2 years (29 February purchases end on 28 February)
 */
public record CaseFacts(LocalDate today, long daysSincePurchase, boolean returnWindowExceeded,
		boolean complaintPeriodExceeded) {

	public static final int RETURN_WINDOW_DAYS = 30;

	public static CaseFacts of(LocalDate purchaseDate, Clock clock) {
		LocalDate today = LocalDate.now(clock);
		long days = ChronoUnit.DAYS.between(purchaseDate, today);
		return new CaseFacts(today, days, days > RETURN_WINDOW_DAYS, today.isAfter(purchaseDate.plusYears(2)));
	}
}
