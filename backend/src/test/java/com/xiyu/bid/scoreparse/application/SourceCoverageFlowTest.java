package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SourceCoverageFlowTest {
    @Test void omittedRowRemainsDiagnosableAfterFailedRetry() throws Exception {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        String[][] rows = {{"条款号","评审因素","评审标准","分值"}, {"1","甲","甲项得50分。","50"},
                {"2","乙","乙项得45分。","45"}, {"3","丙","丙项依据年报综合评审。","5"}};
        for (int r=0;r<rows.length;r++) for (int c=0;c<4;c++)
            cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r"+r+"-c"+c,r,c,rows[r][c],rows[r][c],1,1,false,"pdf:page=1",1,"t-r"+r));
        var table = new DocumentArtifact.CanonicalTable("t",1,"第四章 评审办法",4,4,List.of(),cells,true,"PDF");
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc","sha","PDF","2.2"),List.of(table),List.of());
        var sidecar = new SidecarDocumentArtifactExtractor("http://localhost:9","",1,new ObjectMapper()) {
            public DocumentArtifact extract(byte[] bytes,String name) { return artifact; }
        };
        var extractor = new TextAwareScoreFactExtractorTestAdapter() {
            public boolean supportsTextOnly() { return true; }
            public ExtractionResult extractWithTextAndProgress(DocumentArtifact a,String text,Runnable heartbeat) {
                return new ExtractionResult(null,List.of(fact("1","甲",50),fact("2","乙",45)),"first",0);
            }
            public ExtractionResult recheckWithTextAndProgress(DocumentArtifact a,String text,Runnable heartbeat) {
                return new ExtractionResult(null,List.of(),"retry",0);
            }
        };
        var result = MultimodalStructuredExtractionHandler.extract(new TenderTextSource("bid.pdf","memory:0","# 第四章 评审办法\n"),
                new LoadedBidFile.InMemory(new byte[]{1},"bid.pdf","application/pdf"),sidecar,extractor);
        assertThat(result.sourceDiagnostics()).anyMatch(d -> d.startsWith("SOURCE_SCORE_ROW_UNCOVERED:"));
        assertThat(result.originalVerified()).isFalse();
    }
    private static MultimodalScoreFact fact(String code,String name,int weight) {
        String quote=name+"项得"+weight+"分。";
        return new MultimodalScoreFact("第四章 评审办法",code,"",name,BigDecimal.valueOf(weight),quote,"OBJECTIVE",1,
                "t",List.of("t-r"+code+"-c2"),quote,.9,ChildAggregation.SUM);
    }
}
