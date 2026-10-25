package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "quality58.bid.pdf", matches = ".+")
class Report58FrozenPdfReplayTest {
    @Test
    void originalScoringRuleIsNotDeviationAndLateEvidencePagesAreRetained() throws Exception {
        Path path = Path.of(System.getProperty("quality58.bid.pdf"));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var in = new DigestInputStream(Files.newInputStream(path), digest)) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
        }
        assertThat(HexFormat.of().formatHex(digest.digest()))
                .isEqualTo("9ec146ad0344b5196d81edce9b59f8ab5485afbea353b72f7f5eedb86a180646");
        List<DocumentContent.PageContent> pages = new ArrayList<>();
        try (PDDocument pdf = PDDocument.load(new File(path.toString()))) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1610);
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page : List.of(5, 8, 9, 10, 11, 12, 1048)) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(new DocumentContent.PageContent(page, stripper.getText(pdf), List.of()));
            }
        }
        var scoring = new DocumentContent("bid.pdf", "frozen", pages.getFirst().text(), List.of(pages.getFirst()));
        assertThat(scoring.fullText()).contains("不满足").contains("不得分");
        var result = ResponsivenessReviewPolicy.check(scoring);
        assertThat(result == null || result.status() != QualityCheckStatus.FAIL).isTrue();
        var bid = new DocumentContent("bid.pdf", "frozen", "frozen selection", pages);
        assertThat(DocumentEvidencePageSelector.select(bid)).contains(11, 1048);
        var commitment = new DocumentContent("bid.pdf", "frozen", pages.getLast().text(), List.of(pages.getLast()));
        assertThat(CommitmentSealCheckPolicy.evaluateCommitmentSignature(commitment).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }
}
