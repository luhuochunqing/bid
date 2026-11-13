package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.infrastructure.openai.*;
import com.xiyu.bid.scoreparse.domain.*;
import com.xiyu.bid.scoreparse.entity.*;
import com.xiyu.bid.scoreparse.infrastructure.openai.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScoringEvidenceCoverageTest {
    @Test void partialWordExtractionAcceptsUniqueNativeParagraphSubstring() throws Exception {
        String paragraph = "证书名称：ISO9001质量管理体系认证证书";
        var ref = Map.<String, Object>of("partName", "word/document.xml", "anchor", "p1",
                "kind", "TEXT", "text", paragraph);
        var failedImage = Map.<String, Object>of("partName", "word/document.xml", "anchor", "p2",
                "relationshipId", "rId9");
        var coverage = new ScoringExtractionReport(1, "fixture", "PARTIAL", 2, 2, 1, 0, 1, 0,
                List.of(), List.of(failedImage), List.of(), 0);
        var document = new ScoringDocument(paragraph, List.of(ref), coverage);

        assertThat(document.verifiedQuoteSource("ISO9001质量管理体系认证证书")).isTrue();
        assertThat(assessDocument(document, "具有ISO9001质量管理体系认证证书得10分").getActualScore())
                .isEqualByComparingTo("10");
        var ambiguous = new ScoringDocument(paragraph + "\n\n" + paragraph, List.of(ref), coverage);
        assertThat(ambiguous.verifiedQuoteSource("ISO9001质量管理体系认证证书")).isFalse();
        assertThat(assessDocument(ambiguous, "具有ISO9001质量管理体系认证证书得10分").getActualScore()).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"unread", "failed", "empty"})
    void nativeParagraphSubstringRejectsUnverifiedSource(String kind) {
        String paragraph = "证书名称：ISO9001质量管理体系认证证书";
        var ref = Map.<String, Object>of("partName", "word/document.xml", "anchor", "p1",
                "kind", "TEXT", "text", paragraph);
        var bad = List.of(ref);
        var empty = List.<Map<String, Object>>of();
        boolean unread = "unread".equals(kind), failed = "failed".equals(kind);
        var coverage = new ScoringExtractionReport(1, "fixture", "PARTIAL", 1, unread ? 0 : 1, 0,
                unread ? 1 : 0, failed ? 1 : 0, "empty".equals(kind) ? 1 : 0,
                unread ? bad : empty, failed ? bad : empty, "empty".equals(kind) ? bad : empty, 0);
        assertThat(new ScoringDocument(paragraph, List.of(ref), coverage)
                .verifiedQuoteSource("ISO9001质量管理体系认证证书")).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"RESPONSES", "BAD_REQUEST", "SCHEMA_FALLBACK"})
    void rejectedImageSupplementPreservesTextAssessmentWithinTwoTransmissions(String rejection)throws Exception {
        var textOutput=new ScoreAssessmentOutput();
        textOutput.evidence="正文可读，但证书影像需人工确认";
        textOutput.suggestion="请核验原件证书";
        var usedBudget=new java.util.concurrent.atomic.AtomicReference<OpenAiRequestBudget>();
        var model=new OpenAiScoreAnalyzer(null,null,null) {
            @Override public ScoreAssessmentOutput assessObjective(String detail,BigDecimal weight,String excerpt,
                    List<DocumentImage> images,OpenAiRequestBudget budget) {
                usedBudget.set(budget);
                if(images.isEmpty()) {
                    budget.acquire(Duration.ofSeconds(1));
                    return textOutput;
                }
                if("RESPONSES".equals(rejection))throw new IllegalStateException("multimodal requires CHAT_COMPLETIONS");
                budget.acquire(Duration.ofSeconds(1));
                if("SCHEMA_FALLBACK".equals(rejection))budget.acquire(Duration.ofSeconds(1));
                throw com.openai.errors.BadRequestException.builder()
                        .headers(com.openai.core.http.Headers.builder().build())
                        .build();
            }
        };
        String marker="[word/document.xml#p1 rId1]";
        var document=new ScoringDocument(marker+" 资质证书扫描件",List.of(Map.of(
                "partName","word/document.xml","anchor","p1","relationshipId","rId1","mediaName","word/media/cert.png")),
                new ScoringExtractionReport(1,"fixture","COMPLETE",1,1,1,0,0,0,List.of(),List.of(),List.of(),0));
        var texts=mock(SidecarStreamTextExtractor.class);
        when(texts.extractScoringDocument(any(byte[].class),anyString(),anyLong())).thenReturn(document);
        var png=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",png);
        var bytes=new java.io.ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("word/media/cert.png"));zip.write(png.toByteArray());zip.closeEntry();
        }
        long deadline=System.currentTimeMillis()+10000;
        try(var session=new ScoringFileSession(1L,"hash",new LoadedBidFile.InMemory(bytes.toByteArray(),"bid.docx","docx"),
                texts,mock(SidecarImageExtractor.class),deadline,()->{})) {
            session.extract();
            assertThat(session.imagesFor(marker,new ImageBudget(3,1024))).hasSize(1);
            var style="RESPONSES".equals(rejection)?OpenAiBidAgentApiStyle.RESPONSES:OpenAiBidAgentApiStyle.CHAT_COMPLETIONS;
            var result=new ScoreItemAssessor(model,new ImageBudget(3,1024)).assessScoring(
                    ScoreParseTask.builder().id(1L).build(),ScoreItem.builder().id(2L).detail("提供资质证书得10分")
                            .dim("资质证书").weight(BigDecimal.TEN).scoreType("OBJECTIVE").build(),document,session,deadline,
                    new OpenAiBidAgentRequestConfig("synthetic","http://provider.invalid/v1","synthetic",Duration.ofSeconds(1),style));
            assertThat(result.getActualScore()).isNull();
            assertThat(result.getStatusStage2()).isEqualTo("PENDING");
            assertThat(result.getReuseKind()).isEqualTo("FRESH");
            assertThat(result.getEvidence()).isEqualTo(textOutput.evidence);
            assertThat(result.getSuggestion()).isEqualTo(textOutput.suggestion);
            assertThat(usedBudget.get().attempts()).isEqualTo("RESPONSES".equals(rejection)?1:2);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"unread", "failed", "empty"})
    void boundQuoteAndNativeRowBothRejectAnUnverifiedSourceUnit(String kind) {
        var refs = List.of(
                Map.<String, Object>of("partName", "word/document.xml", "anchor", "p1", "kind", "TEXT", "text", "7"),
                Map.<String, Object>of("partName", "word/document.xml", "anchor", "p2", "kind", "TEXT", "text", "赊销额度"),
                Map.<String, Object>of("partName", "word/document.xml", "anchor", "p3", "kind", "TEXT", "text", "2600万元"));
        var bad = List.of(refs.get(1));
        var empty = List.<Map<String, Object>>of();
        boolean unread = "unread".equals(kind), failed = "failed".equals(kind);
        var coverage = new ScoringExtractionReport(1, "fixture", unread || failed ? "PARTIAL" : "COMPLETE",
                3, unread ? 2 : 3, 0, unread ? 1 : 0, failed ? 1 : 0, "empty".equals(kind) ? 1 : 0,
                unread ? bad : empty, failed ? bad : empty, "empty".equals(kind) ? bad : empty, 0);
        String row = "| 7 | 赊销额度 | 2600万元 |";
        var document = new ScoringDocument(row, refs, coverage);
        assertThat(document.verifiedQuoteSource("赊销额度")).isFalse();
        assertThat(document.verifiedNativeTableRow(row, List.of("7", "赊销额度", "2600万元"))).isFalse();
        var clean = new ScoringDocument(row, refs, new ScoringExtractionReport(
                1, "fixture", "COMPLETE", 3, 3, 0, 0, 0, 0, empty, empty, empty, 0));
        assertThat(clean.verifiedQuoteSource("赊销额度")).isTrue();
        assertThat(clean.verifiedNativeTableRow(row, List.of("7", "赊销额度", "2600万元"))).isTrue();
    }

    @Test void unrelatedEmptyImageDoesNotSuppressVerifiedClosedPositiveCertificate()throws Exception {
        String certificate="ISO9001质量管理体系认证证书";
        var output=new ScoreAssessmentOutput();output.actualScore=10d;output.quote=certificate;
        var model=mock(OpenAiScoreAnalyzer.class);
        when(model.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class))).thenReturn(output);
        var document=new ScoringDocument("[第 1 页]\n"+certificate+"\n\n[第 2 页]",List.of(Map.of("pageNumber",1),Map.of("pageNumber",2)),
                new ScoringExtractionReport(1,"scoring-ocr-v2","COMPLETE",2,2,1,0,0,1,
                        List.of(),List.of(),List.of(Map.of("pageNumber",2)),0));
        try(var session=new ScoringFileSession(1L,"hash",new LoadedBidFile.InMemory(new byte[]{1},"bid.pdf","pdf"),
                mock(SidecarStreamTextExtractor.class),null,System.currentTimeMillis()+5000,()->{})) {
            var result=new ScoreItemAssessor(model,new ImageBudget(3,1024)).assessScoring(
                    ScoreParseTask.builder().id(1L).build(),ScoreItem.builder().id(2L).detail("具有ISO9001质量管理体系认证证书得10分")
                            .dim("资质证书").weight(BigDecimal.TEN).scoreType("OBJECTIVE").build(),document,session,
                    System.currentTimeMillis()+5000,new OpenAiBidAgentRequestConfig("synthetic","http://provider.invalid/v1","synthetic",Duration.ofSeconds(1),OpenAiBidAgentApiStyle.CHAT_COMPLETIONS));
            assertThat(result.getActualScore()).isEqualByComparingTo("10");
        }
    }
    @Test void actualPythonWireReachesAssessorAndSeparatesClosedPositiveFromCumulativeRule()throws Exception {
        com.xiyu.bid.scoreparse.domain.ScoringDocument document;
        try(var input=getClass().getResourceAsStream("/scoreparse/scoring-positive-certificate-wire.json")) {
            document=new ScoringSidecarResponseReader().read(input,10000,System.currentTimeMillis()+5000);
        }
        assertThat(document.extraction().emptyUnitCount()).isEqualTo(1);
        assertThat(assessDocument(document,"具有ISO9001质量管理体系认证证书得10分").getActualScore()).isEqualByComparingTo("10");
        assertThat(assessDocument(document,"每提供一份合同得2分，最多10分").getActualScore()).isNull();
        var partial=new ScoringDocument(document.text(),document.sourceRefs(),new ScoringExtractionReport(1,"scoring-ocr-v2","PARTIAL",
                2,1,0,1,0,0,List.of(Map.of("partName","word/document.xml","anchor","p2","relationshipId","rId9")),List.of(),List.of(),0));
        assertThat(assessDocument(partial,"具有ISO9001质量管理体系认证证书得10分").getActualScore()).isEqualByComparingTo("10");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "投标人须提供认证证书扫描件",
            "投标人须提供在有效期内的认证证书扫描件",
            "投标人须提供认证证书扫描件加盖投标人公章",
            "投标人须提供在有效期内的认证证书扫描件加盖投标人公章"
    })
    void certificateTitleCannotProveUnreadRequiredMaterials(String materials) throws Exception {
        String detail = "具有ISO9001质量管理体系认证证书得10分。证明材料：" + materials;
        assertThat(CertificateScoreRule.parse("资质证书", detail, BigDecimal.TEN).supported()).isTrue();
        ScoringDocument completeWithEmptyOcr;
        try (var input = getClass().getResourceAsStream("/scoreparse/scoring-positive-certificate-wire.json")) {
            completeWithEmptyOcr = new ScoringSidecarResponseReader().read(input, 10000, System.currentTimeMillis() + 5000);
        }
        var partial = new ScoringDocument(completeWithEmptyOcr.text(), completeWithEmptyOcr.sourceRefs(),
                new ScoringExtractionReport(1, "scoring-ocr-v2", "PARTIAL", 2, 1, 0, 1, 0, 0,
                        List.of(Map.of("partName", "word/document.xml", "anchor", "p2", "relationshipId", "rId9")),
                        List.of(), List.of(), 0));
        for (var document : List.of(completeWithEmptyOcr, partial)) {
            var result = assessDocument(document, detail);
            assertThat(result.getActualScore()).as(document.extraction().coverageStatus()).isNull();
            assertThat(result.getStatusStage2()).isEqualTo("PENDING");
            assertThat(result.getMissedReason()).contains("人工确认");
        }
    }

    private ScoreResult assessDocument(ScoringDocument document,String detail)throws Exception {
        var output=new ScoreAssessmentOutput();output.actualScore=10d;output.quote="ISO9001质量管理体系认证证书";
        var model=mock(OpenAiScoreAnalyzer.class);
        when(model.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class))).thenReturn(output);
        try(var session=new ScoringFileSession(1L,"hash",new LoadedBidFile.InMemory(new byte[]{1},"bid.docx","docx"),
                mock(SidecarStreamTextExtractor.class),null,System.currentTimeMillis()+5000,()->{})) {
            return new ScoreItemAssessor(model,new ImageBudget(3,1024)).assessScoring(ScoreParseTask.builder().id(1L).build(),
                    ScoreItem.builder().id(2L).detail(detail).dim("资质证书").weight(BigDecimal.TEN).scoreType("OBJECTIVE").build(),
                    document,session,System.currentTimeMillis()+5000,new OpenAiBidAgentRequestConfig("synthetic","http://provider.invalid/v1","synthetic",Duration.ofSeconds(1),OpenAiBidAgentApiStyle.CHAT_COMPLETIONS));
        }
    }
    @Test void completeWithEmptyOcrCannotTurnUnverifiedAbsenceIntoZero()throws Exception {
        var output=new ScoreAssessmentOutput();output.actualScore=0d;output.quoteMissing=true;
        var model=mock(OpenAiScoreAnalyzer.class);
        when(model.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class))).thenReturn(output);
        var document=new ScoringDocument("[第 1 页] 其他内容",List.of(Map.of("pageNumber",1)),
                new ScoringExtractionReport(1,"scoring-ocr-v2","COMPLETE",2,2,1,0,0,1,
                        List.of(),List.of(),List.of(Map.of("pageNumber",2)),0));
        var texts=mock(SidecarStreamTextExtractor.class);
        try(var session=new ScoringFileSession(1L,"hash",new LoadedBidFile.InMemory(new byte[]{1},"bid.pdf","pdf"),
                texts,null,System.currentTimeMillis()+5000,()->{})) {
            var result=new ScoreItemAssessor(model,new ImageBudget(3,1024)).assessScoring(
                    ScoreParseTask.builder().id(1L).build(),ScoreItem.builder().id(2L).detail("提供合同得10分，否则0分")
                            .dim("业绩").weight(BigDecimal.TEN).scoreType("OBJECTIVE").build(),document,session,
                    System.currentTimeMillis()+5000,new OpenAiBidAgentRequestConfig("synthetic","http://provider.invalid/v1","synthetic",Duration.ofSeconds(1),OpenAiBidAgentApiStyle.CHAT_COMPLETIONS));
            assertThat(result.getActualScore()).isNull();
            assertThat(result.getStatusStage2()).isEqualTo("PENDING");
        }
    }
}
