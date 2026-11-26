package com.hsdc.copilot.analysis;

import com.hsdc.copilot.analysis.ImageAnalysis.Accessory;
import com.hsdc.copilot.analysis.ImageAnalysis.CauseConfidence;
import com.hsdc.copilot.analysis.ImageAnalysis.DamageType;
import com.hsdc.copilot.analysis.ImageAnalysis.LikelyCause;
import com.hsdc.copilot.analysis.ImageAnalysis.PhotoProblem;
import com.hsdc.copilot.analysis.ImageAnalysis.Source;
import com.hsdc.copilot.analysis.ImageAnalysis.TriageDisagreement;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Model-facing structured-output schema of the vision step: every field of
 * {@link ImageAnalysis} except {@code source}. Limits (lengths, list sizes) are
 * enforced in code, not in the schema.
 */
@JsonClassDescription("Objective analysis of one customer photo of a hardware device.")
public record ImageAnalysisResult(
        @JsonPropertyDescription("True if the photo is sharp, lit and shows the device well enough to assess.")
        boolean photoUsable,
        @JsonPropertyDescription("Main problem with the photo, NONE if it is usable.")
        PhotoProblem photoProblem,
        @JsonPropertyDescription("True if the device in the photo matches the category declared in the form.")
        boolean matchesCategory,
        @JsonPropertyDescription("True if any damage is visible.")
        boolean damageVisible,
        @JsonPropertyDescription("Type of the most significant visible damage, NONE if none.")
        DamageType damageType,
        @JsonPropertyDescription("Short free-text location of the damage in Polish (max 200 chars), empty if none.")
        String damageLocation,
        @JsonPropertyDescription("Most likely cause of the visible damage, UNKNOWN if it cannot be told.")
        LikelyCause likelyCause,
        @JsonPropertyDescription("Confidence in likelyCause.")
        CauseConfidence causeConfidence,
        @JsonPropertyDescription("True if the device shows signs of use.")
        boolean signsOfUse,
        @JsonPropertyDescription("Accessories visible in the photo (max 6).")
        List<Accessory> visibleAccessories,
        @JsonPropertyDescription("True if the device could be resold as new.")
        boolean resellableAsNew,
        @JsonPropertyDescription("True for a safety hazard such as a swollen battery, burn marks, smoke or exposed wiring.")
        boolean safetyHazard,
        @JsonPropertyDescription("What is visible in the photo, in Polish, max 500 chars.")
        String summaryPl,
        @JsonPropertyDescription("Disagreements with the triage signals, empty list if none (max 10).")
        List<TriageDisagreement> triageDisagreements) {

    /** Converts to the stored analysis, marking where it came from. */
    public ImageAnalysis toAnalysis(Source source) {
        return new ImageAnalysis(photoUsable, photoProblem, matchesCategory, damageVisible, damageType,
                damageLocation, likelyCause, causeConfidence, signsOfUse, visibleAccessories, resellableAsNew,
                safetyHazard, summaryPl, triageDisagreements, source);
    }
}
