package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SourceArtifactBundleTest {
    @Test void everyDocumentRetainsItsTextBlocksWarningsAndSourceIdentityInEitherOrder() {
        var pdf = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("pdf", "pdf-sha", "PDF", "v2"),
                List.of(), List.of("ocr warning"), List.of(new DocumentArtifact.TextBlockEvidence("b", 4,
                "评分办法", "供应商最高5分", 90d, new DocumentArtifact.BoundingBox(0,0,10,10,20,20))), "2.2");
        var docx = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("docx", "docx-sha", "DOCX", "v2"),
                List.of(), List.of());
        for (var documents : List.of(List.of(pdf, docx), List.of(docx, pdf))) {
            var bundle = SourceArtifactBundle.combine(documents);
            assertThat(bundle.textBlocks()).hasSize(1);
            assertThat(bundle.textBlocks().getFirst().text()).isEqualTo("供应商最高5分");
            assertThat(bundle.warnings()).contains("ocr warning");
            assertThat(bundle.documentFor(bundle.textBlocks().getFirst().blockId()).sha256()).isEqualTo("pdf-sha");
            assertThat(bundle.artifact().fileType()).isEqualTo("BUNDLE");
            var plan = com.xiyu.bid.scoreparse.domain.SourceRecheckPlan.build(bundle,List.of(),List.of());
            assertThat(plan.artifact().textBlocks()).containsExactlyElementsOf(bundle.textBlocks());
            assertThat(plan.artifact().textBlocks().getFirst().bounds()).isEqualTo(pdf.textBlocks().getFirst().bounds());
            var anchor = com.xiyu.bid.scoreparse.domain.SourceAnchorCatalog.build(plan.artifact()).getFirst();
            assertThat(anchor.sourceSpans().getFirst().fileSha256()).isEqualTo("pdf-sha");

        }
    }
}
