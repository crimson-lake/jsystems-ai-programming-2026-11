package com.hsdc.copilot.cases;

import com.hsdc.copilot.cases.validation.PurchaseDate;
import com.hsdc.copilot.cases.validation.TrimmedLength;
import com.hsdc.copilot.cases.validation.ValidComplaintReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Form data of a case (request part {@code form}, ADR-002 section 4).
 * Validation messages are keys of {@code ValidationMessages_pl.properties} (ADR-002 section 3.6).
 */
@ValidComplaintReason(message = "{form.reason.tooShort}")
public record CaseForm(
        @NotNull(message = "{form.caseType.required}")
        CaseType caseType,
        @NotNull(message = "{form.category.required}")
        Category category,
        @TrimmedLength(min = 2, max = 100, message = "{form.model.invalid}")
        String model,
        @NotNull(message = "{form.purchaseDate.required}")
        @PurchaseDate
        LocalDate purchaseDate,
        @Size(max = 2000, message = "{form.reason.tooLong}")
        String reason) {
}
