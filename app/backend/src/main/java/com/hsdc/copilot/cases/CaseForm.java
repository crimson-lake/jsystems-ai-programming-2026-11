package com.hsdc.copilot.cases;

import java.time.LocalDate;

/**
 * Form data of a case (request part {@code form}, ADR-002 section 4).
 * No validation annotations yet: task T20 adds them.
 */
public record CaseForm(
        CaseType caseType,
        Category category,
        String model,
        LocalDate purchaseDate,
        String reason) {
}
