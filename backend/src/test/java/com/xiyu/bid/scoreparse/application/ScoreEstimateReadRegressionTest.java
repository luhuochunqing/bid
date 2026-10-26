package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ScoreEstimateReadRegressionTest {
    @Test void explicitCertificateDimensionRoutesUnknownSyntaxIndependently() {
        for (String dim : List.of("企业荣誉", "管理体系", "企业资质")) {
            for (String detail : List.of("投标人具有中国品牌信誉评价认证五星级证书得5分", "须具备有关证书", "其他未解释条件", "")) {
                var row = ScoreItem.builder().id(228L).projectId(1117L).parseTaskId(119L).code("4")
                        .dim(dim).detail(detail).weight(BigDecimal.TEN).scoreType("OBJECTIVE")
                        .estScore(BigDecimal.TEN).statusStage1("OK").kbHit(true).build();
                assertThat(CertificateScoreEstimateSupport.applies(row)).as(dim + ":" + detail).isTrue();
                var projected = new Fixture(row).reader().read(1117L,null,null);
                assertThat(projected.items().getFirst().estScore()).isNull();
                assertThat(projected.summary().totalEstScore()).isEqualByComparingTo("0");
            }
        }
        for (String dim : List.of("证书培训", "人员认证", "企业荣誉方案", "管理体系实施计划", "团队证书", "财务状况", "信用评价", "品牌", "仓储", "业绩")) {
            var row = ScoreItem.builder().dim(dim).detail("具有ISO9001证书得10分").scoreType("OBJECTIVE").build();
            assertThat(CertificateScoreEstimateSupport.applies(row)).as(dim).isFalse();
        }
    }
    @Test void unknownProofConditionsStillRouteToCertificateRefresh() {
        var row = ScoreItem.builder().id(228L).projectId(1117L).parseTaskId(119L).code("4").dim("企业荣誉").scoreType("OBJECTIVE")
                .detail("具有中国品牌信誉评价认证五星级证书；2、证明文件：投标人须提供认证证书扫描件，原件备查并须另具CMMI证书")
                .estScore(BigDecimal.TEN).statusStage1("OK").kbHit(true).build();
        assertThat(CertificateScoreEstimateSupport.applies(row)).isTrue();
        var freshness = new ScoreEstimateFreshness(mock(com.xiyu.bid.scoreparse.application.match.CertMatchService.class));
        assertThat(freshness.status(row, java.time.LocalDate.of(2026,10,26))).isEqualTo("STALE");
        var projected = new Fixture(row).reader().read(1117L, null, null);
        assertThat(projected.items().getFirst().estScore()).isNull();
        assertThat(projected.items().getFirst().status()).isEqualTo("PENDING");
        assertThat(projected.summary().totalEstScore()).isEqualByComparingTo("0");
        assertThat(com.xiyu.bid.scoreparse.domain.CertificateScorePolicy.assess(row.getDim(),row.getDetail(),
                BigDecimal.TEN,java.time.LocalDate.of(2026,10,26),List.of()).status()).isEqualTo("PENDING");
    }
    @Test void legacyCertificateFullScoreWithoutCalculationIdentityIsNotTrusted() {
        var row = ScoreItem.builder().id(225L).projectId(1117L).parseTaskId(119L).code("1")
                .dim("管理体系").detail("ISO9001、ISO14001、ISO45001三项齐全得10分，缺少一项不得分")
                .weight(BigDecimal.TEN).scoreType("OBJECTIVE").statusStage1("OK")
                .estScore(BigDecimal.TEN).kbHit(true).estBasis("历史关键词命中满分").build();
        var fixture = new Fixture(row);
        var result = fixture.reader().read(1117L, null, null);
        assertThat(result.items().getFirst().estScore()).isNull();
        assertThat(result.items().getFirst().status()).isEqualTo("PENDING");
        assertThat(result.items().getFirst().estBasis()).contains("更新");
        assertThat(result.summary().totalEstScore()).isEqualByComparingTo("0");
        assertThat(row.getEstScore()).isEqualByComparingTo("10");
    }

    @Test void legacyRuleSourceTimeBelongsToItsActualTaskNotTheNewestCandidate() {
        var row=ScoreItem.builder().id(225L).projectId(1117L).parseTaskId(119L).code("1")
                .dim("服务方案").detail("横向比较").weight(BigDecimal.TEN)
                .scoreType("SUBJECTIVE").statusStage1("PENDING").build();
        var f=new Fixture(row);
        var activeTime=LocalDateTime.parse("2026-09-22T19:05:59");
        when(f.tasks.findById(119L)).thenReturn(Optional.of(ScoreParseTask.builder().id(119L)
                .fileName("旧正式规则.pdf").completedAt(activeTime).build()));
        var candidate=ScoreParseTask.builder().id(148L).status("COMPLETED")
                .decisionStatus("REVIEW_REQUIRED").completedAt(LocalDateTime.parse("2026-10-25T22:32:57")).build();
        var result=f.reader().read(1117L,candidate,null);
        assertThat(result.meta().parseTime()).isEqualTo(activeTime);
        assertThat(result.meta().sourceFileName()).isEqualTo("旧正式规则.pdf");
        assertThat(result.meta().decisionStatus()).isEqualTo("REVIEW_REQUIRED");
    }

    static class Fixture {
        final ScoreItemRepository items=mock(ScoreItemRepository.class);
        final ScoreParseTaskRepository tasks=mock(ScoreParseTaskRepository.class);
        final ScoreResultRepository results=mock(ScoreResultRepository.class);
        final InitiationTenderTextResolver resolver=mock(InitiationTenderTextResolver.class);
        final BidTenderDocumentSnapshotRepository snapshots=mock(BidTenderDocumentSnapshotRepository.class);
        final ProjectDocumentRepository documents=mock(ProjectDocumentRepository.class);
        Fixture(ScoreItem row) { when(items.findByProjectIdOrderByItemIndexAsc(1117L)).thenReturn(List.of(row)); }
        ScoreParseItemsReadSupport reader() { return new ScoreParseItemsReadSupport(items,tasks,results,resolver,snapshots,documents); }
    }
}
