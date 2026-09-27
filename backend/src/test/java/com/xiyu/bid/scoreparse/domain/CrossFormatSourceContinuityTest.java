package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CrossFormatSourceContinuityTest {
    @Test void docxNullPagesRemainQualifiedByLogicalRowsAndXmlInEitherMixedOrder() {
        for (boolean pdfFirst : List.of(true,false)) {
            var artifact = artifact("DOCX",pdfFirst);
            var fact = fact();
            var verification = OriginalScoreEvidencePolicy.verifyModel(artifact,"",List.of(fact),BigDecimal.valueOf(5));
            assertThat(verification.qualifications()).singleElement().satisfies(q -> assertThat(q.candidateEligible()).isTrue());
            assertThat(new MultimodalScoreFactValidator().validate(List.of(fact),artifact,BigDecimal.valueOf(5)).violations())
                    .noneMatch(v -> v.code().equals("SOURCE_LOCATOR_INVALID") || v.code().equals("SOURCE_QUOTE_NOT_VERIFIED"));
        }
    }
    @Test void pdfAndUnknownTypesCannotUseDocxXmlExceptionForMissingPages() {
        for (String type : List.of("PDF","UNKNOWN")) {
            var artifact = artifact(type,true);
            assertThat(OriginalScoreEvidencePolicy.verifyModel(artifact,"",List.of(fact()),BigDecimal.valueOf(5))
                    .qualifications()).singleElement().satisfies(q -> assertThat(q.candidateEligible()).isFalse());
        }
    }
    private static MultimodalScoreFact fact() {
        return new MultimodalScoreFact("评分办法","1","","方案",BigDecimal.valueOf(5),"完整规则","OBJECTIVE",null,
                "word-a",List.of("word-c1","word-c2"),"方案最高5分，提供证明材料。",.9);
    }
    private static DocumentArtifact artifact(String type,boolean pdfFirst) {
        var a=table("word-a","word-c1","方案最高5分，",type);
        var b=table("word-b","word-c2","提供证明材料。",type);
        var pdf=table("pdf-a","pdf-c1","其他条款","PDF");
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bundle","","BUNDLE","v2"),
                pdfFirst?List.of(pdf,a,b):List.of(a,b,pdf),List.of(),List.of(),"2.2",List.of(
                new DocumentArtifact.SourceDocument("word-",new DocumentArtifact.ArtifactMeta("word","sha-word",type,"v2")),
                new DocumentArtifact.SourceDocument("pdf-",new DocumentArtifact.ArtifactMeta("pdf","sha-pdf","PDF","v2"))));
    }
    private static DocumentArtifact.CanonicalTable table(String table,String cell,String raw,String type) {
        return new DocumentArtifact.CanonicalTable(table,null,"评分办法",1,1,List.of(),List.of(
                new DocumentArtifact.CanonicalTable.CanonicalCellDto(cell,0,0,raw,raw,1,1,false,
                        "/word/document.xml/"+cell,null,"word-logical")),true,type);
    }
}
