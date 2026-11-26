package com.hsdc.copilot.decision;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Model output schema of the structured decision step (ADR-001 section 3.4).
 * Limits (1-5 rule IDs, 300 chars) are enforced in code.
 */
@JsonClassDescription("Preliminary decision on a return or complaint case.")
public record DecisionResult(
        @JsonPropertyDescription("Decision label.")
        Label label,
        @JsonPropertyDescription("Policy rule IDs that justify the decision, 1 to 5, only IDs from the applied policy.")
        List<String> ruleIds,
        @JsonPropertyDescription("Main reason code.")
        ReasonCode reasonCode,
        @JsonPropertyDescription("Suggest the other case type if the customer chose the wrong one, otherwise NONE.")
        SuggestOtherCaseType suggestOtherCaseType,
        @JsonPropertyDescription("Polish reason for a changed decision (max 300 chars), empty if not changed.")
        String changeReasonPl,
        @JsonPropertyDescription("Polish description of the photo or information still needed (max 300 chars), may be empty.")
        String missingInfoPl) {

    public enum Label {
        ACCEPTED_PRELIMINARY, REJECTED, NEEDS_MORE_INFO, ESCALATED
    }

    public enum ReasonCode {
        RESELLABLE, NOT_RESELLABLE, OUTSIDE_WINDOW, COVERED_DEFECT, EXCLUDED_DAMAGE, PHOTO_UNUSABLE,
        CATEGORY_MISMATCH, MISSING_INFO, UNCERTAIN_CAUSE, CONTRADICTION, SAFETY, OTHER
    }

    public enum SuggestOtherCaseType {
        NONE, RETURN, COMPLAINT
    }
}
