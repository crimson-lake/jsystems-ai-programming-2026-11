package com.hsdc.copilot.analysis;

import java.util.List;

/**
 * Structured analysis of one photo (ADR-001 section 4, authoritative field table).
 * Held in {@code CaseSession}; the photo bytes are discarded after analysis.
 * The model-facing schema is {@link ImageAnalysisResult}, which has no {@code source}.
 */
public record ImageAnalysis(
        boolean photoUsable,
        PhotoProblem photoProblem,
        boolean matchesCategory,
        boolean damageVisible,
        DamageType damageType,
        String damageLocation,
        LikelyCause likelyCause,
        CauseConfidence causeConfidence,
        boolean signsOfUse,
        List<Accessory> visibleAccessories,
        boolean resellableAsNew,
        boolean safetyHazard,
        String summaryPl,
        List<TriageDisagreement> triageDisagreements,
        Source source) {

    public enum PhotoProblem {
        NONE, BLURRY, TOO_DARK, NO_DEVICE, DEVICE_NOT_FULLY_VISIBLE, DAMAGE_NOT_SHOWN, OTHER
    }

    public enum DamageType {
        NONE, SCREEN_CRACK, IMPACT_DENT, BROKEN_PART, LIQUID, BURN_OR_SWELLING, COSMETIC_WEAR, DISPLAY_DEFECT, OTHER
    }

    public enum LikelyCause {
        MANUFACTURING_DEFECT, IMPACT, LIQUID, WEAR, UNKNOWN
    }

    public enum CauseConfidence {
        HIGH, MEDIUM, LOW
    }

    public enum Accessory {
        PROTECTIVE_FILM, ORIGINAL_BOX, CHARGER, CABLE, MANUAL, OTHER
    }

    /** Where the analysis came from; set by code, never by the model. */
    public enum Source {
        LLM, TRIAGE
    }

    /** A place where the vision model disagrees with a triage signal (ADR-004 section 3.4). */
    public record TriageDisagreement(String key, String triageValue, String llmValue) {
    }
}
