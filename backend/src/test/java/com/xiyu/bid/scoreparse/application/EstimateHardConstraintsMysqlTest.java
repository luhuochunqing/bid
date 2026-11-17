package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceRecordEntity;
import com.xiyu.bid.performance.domain.valueobject.ProjectType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Reuses the isolated real-MySQL transaction harness and its permission/CAS checks. */
class EstimateHardConstraintsMysqlTest extends ScoreEstimateRefreshMysqlTest {
    @Test void filteredEstimateCommitsAndReadProjectionRejectsOldFullScore() {
        var row=objective(1,"供应商业绩","需提供2项工业品业绩，2023年1月1日至2025年12月31日，合同金额不少于100万元");
        row.setEstScore(BigDecimal.TEN);row.setStatusStage1("OK");items.saveAndFlush(row);
        assertThat(freshness.status(read(row.getId()),LocalDate.now())).isEqualTo("STALE");
        var before=jdbc.queryForMap("SELECT code,dim,detail,weight,score_type FROM score_item WHERE id=?",row.getId());
        for(int index=0;index<4;index++) {
            LocalDate date=switch(index){case 0->LocalDate.of(2023,1,1);case 1->LocalDate.of(2022,12,31);case 2->null;default->LocalDate.of(2026,1,1);};
            performances.saveAndFlush(PerformanceRecordEntity.builder().contractName("合成合同"+index)
                    .projectType(ProjectType.INDUSTRIAL).signingDate(date).contractAmount(new BigDecimal("1000000")).build());
        }
        authorizeExternalApi();refresh.refresh(project);
        var saved=read(row.getId());assertThat(saved.getEstScore()).isEqualByComparingTo("5.00");
        assertThat(saved.getEstBasis()).contains("命中1项","2023-01-01","2025-12-31");
        assertThat(freshness.status(saved,LocalDate.now())).isEqualTo("FRESH");
        assertThat(jdbc.queryForMap("SELECT code,dim,detail,weight,score_type FROM score_item WHERE id=?",row.getId())).isEqualTo(before);
        var tasks=org.mockito.Mockito.mock(com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository.class);
        var results=org.mockito.Mockito.mock(com.xiyu.bid.scoreparse.repository.ScoreResultRepository.class);
        var reader=new ScoreParseItemsReadSupport(items,tasks,results,
                org.mockito.Mockito.mock(InitiationTenderTextResolver.class),
                org.mockito.Mockito.mock(com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository.class),
                org.mockito.Mockito.mock(com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository.class),freshness);
        var api=reader.read(project,null,null);
        assertThat(api.items()).singleElement().satisfies(i->assertThat(i.estScore()).isEqualByComparingTo("5"));
        saved.setEstimateInputFingerprint(null);items.saveAndFlush(saved);
        assertThat(reader.read(project,null,null).items()).singleElement().satisfies(i->{
            assertThat(i.estScore()).isNull();assertThat(i.estimateStatus()).isEqualTo("STALE");
        });
    }
    @Test void brandConditionsMustPrecedeIdentityDeduplication() {
        var day=LocalDate.now();var deadline=day.plusYears(2);
        String detail="需提供1个品牌授权；品牌为合成品牌；产品线为办公；授权有效期至"+deadline;
        var row=objective(1,"品牌授权",detail);
        for(boolean eligibleFirst:List.of(false,true)) {
            brandRecords.deleteAll();
            for(int index:eligibleFirst?List.of(1,0,2):List.of(0,1,2)) {
                brandRecords.saveAndFlush(com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.ManufacturerAuthorizationEntity.builder()
                    .brandId("one-synthetic-brand").brandName("合成品牌").manufacturerName("合成厂家").importDomestic("国产")
                    .productLine(index==0?com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine.TOOLS
                            :com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine.OFFICE)
                    .authStartDate(day.minusYears(1)).authEndDate(index==0?day.plusDays(1):day.plusYears(index+2))
                    .status(com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus.ACTIVE).build());
            }
            row=read(row.getId());row.setDetail(detail);items.saveAndFlush(row);
            authorizeExternalApi();refresh.refresh(project);
            assertThat(read(row.getId()).getEstScore()).as("eligibleFirst=%s",eligibleFirst).isEqualByComparingTo("10");
            row=read(row.getId());row.setDetail(detail.replace("1个","2个"));items.saveAndFlush(row);
            refresh.refresh(project);
            assertThat(read(row.getId()).getEstScore()).as("one brand must count once").isEqualByComparingTo("5");
        }
    }
}
