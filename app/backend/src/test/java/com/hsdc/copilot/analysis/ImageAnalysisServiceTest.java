package com.hsdc.copilot.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hsdc.copilot.analysis.ImageAnalysis.Accessory;
import com.hsdc.copilot.analysis.ImageAnalysis.CauseConfidence;
import com.hsdc.copilot.analysis.ImageAnalysis.DamageType;
import com.hsdc.copilot.analysis.ImageAnalysis.LikelyCause;
import com.hsdc.copilot.analysis.ImageAnalysis.PhotoProblem;
import com.hsdc.copilot.analysis.ImageAnalysis.Source;
import com.hsdc.copilot.analysis.ImageAnalysis.TriageDisagreement;

class ImageAnalysisServiceTest {

    @Test
    void normalizeEnforcesLengthAndListLimitsAndMarksTheSource() {
        List<Accessory> accessories = new java.util.ArrayList<>(List.of(Accessory.values()));
        accessories.add(Accessory.CABLE);
        accessories.add(null);
        List<TriageDisagreement> disagreements = Collections.nCopies(12, new TriageDisagreement("k", "a", "b"));
        ImageAnalysisResult raw = new ImageAnalysisResult(true, PhotoProblem.NONE, true, true, DamageType.SCREEN_CRACK,
                "x".repeat(250), LikelyCause.IMPACT, CauseConfidence.HIGH, true, accessories, false, false,
                "y".repeat(600), disagreements);

        ImageAnalysis a = ImageAnalysisService.normalize(raw);

        assertThat(a.damageLocation()).hasSize(200);
        assertThat(a.summaryPl()).hasSize(500);
        assertThat(a.visibleAccessories()).hasSize(6).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(a.triageDisagreements()).hasSize(10);
        assertThat(a.source()).isEqualTo(Source.LLM);
    }

    @Test
    void normalizeFillsMissingValuesConservatively() {
        ImageAnalysisResult raw = new ImageAnalysisResult(false, null, true, true, null, null, null, null, false, null,
                false, false, null, null);

        ImageAnalysis a = ImageAnalysisService.normalize(raw);

        assertThat(a.photoProblem()).isEqualTo(PhotoProblem.OTHER);
        assertThat(a.damageType()).isEqualTo(DamageType.OTHER);
        assertThat(a.likelyCause()).isEqualTo(LikelyCause.UNKNOWN);
        assertThat(a.causeConfidence()).isEqualTo(CauseConfidence.LOW);
        assertThat(a.damageLocation()).isEmpty();
        assertThat(a.summaryPl()).isEmpty();
        assertThat(a.visibleAccessories()).isEmpty();
        assertThat(a.triageDisagreements()).isEmpty();
    }

    @Test
    void dataUrlUsesTheMimeTypeOfTheMagicBytes() {
        assertThat(ImageAnalysisService.dataUrl(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1 }))
            .startsWith("data:image/jpeg;base64,");
        assertThat(ImageAnalysisService.mimeType(new byte[] { (byte) 0x89, 'P', 'N', 'G' })).isEqualTo("image/png");
        assertThat(ImageAnalysisService.mimeType("RIFF0000WEBPVP8 ".getBytes())).isEqualTo("image/webp");
    }
}
