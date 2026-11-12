package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.application.match.BrandMatchService;
import com.xiyu.bid.scoreparse.application.match.CertMatchService;
import com.xiyu.bid.scoreparse.application.match.PersonMatchService;
import com.xiyu.bid.scoreparse.application.match.ProjectMatchService;
import com.xiyu.bid.scoreparse.application.match.WarehouseMatchService;
import com.xiyu.bid.scoreparse.domain.CertificateScorePolicy;
import com.xiyu.bid.scoreparse.dto.BrandMatchedItem;
import com.xiyu.bid.scoreparse.dto.PersonMatchedItem;
import com.xiyu.bid.scoreparse.dto.ProjectMatchedItem;
import com.xiyu.bid.scoreparse.dto.WarehouseMatchedItem;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Spec086 simple estimates replace old exact-rule/whole-item-blocking expectations. */
@ExtendWith(MockitoExtension.class)
class EstimatedScoreServiceTest {
    @Mock ScoreItemRepository items;
    @Mock CertMatchService certs;
    @Mock PersonMatchService people;
    @Mock ProjectMatchService projects;
    @Mock WarehouseMatchService warehouses;
    @Mock BrandMatchService brands;
    EstimatedScoreService service;
    @BeforeEach void setup() { service=new EstimatedScoreService(items,certs,people,projects,warehouses,brands); }
    ScoreItem item(String code,String dim,String detail,String weight,String type) {
        return ScoreItem.builder().id(1L).projectId(1L).parseTaskId(1L).itemIndex(1).code(code).dim(dim).detail(detail)
                .weight(new BigDecimal(weight)).scoreType(type).statusStage1("PENDING").build();
    }
    WarehouseMatchedItem warehouse(long id) { return new WarehouseMatchedItem(id,"现有仓"+id,"华北",BigDecimal.TEN,"IN_USE"); }
    void refresh(ScoreItem row) { when(items.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(row));service.refreshItemsForProject(1L); }

    @Test void simpleWarehouseEstimateCountsAvailableWarehousesEvenWithComplexWording() {
        var row=item("仓储能力","技术","山西区域仓库及服务网点，承诺提供相关材料","10","OBJECTIVE");
        when(warehouses.findActiveWarehouses()).thenReturn(List.of(warehouse(1)));
        refresh(row);
        assertThat(row.getEstScore()).isEqualByComparingTo("10");
        assertThat(row.getEstBasis()).contains("知识库粗估","按有无","未核验");
        assertThat(row.getCode()).isEqualTo("仓储能力");assertThat(row.getDim()).isEqualTo("技术");
        verify(warehouses,never()).match(any());
    }
    @Test void simpleWarehouseEstimateUsesQuantityInsteadOfFullMatchExistence() {
        var row=item("A1","仓储能力","需提供2个仓库","10","OBJECTIVE");
        when(warehouses.findActiveWarehouses()).thenReturn(List.of(warehouse(1)));
        refresh(row);
        assertThat(row.getEstScore()).isEqualByComparingTo("5");assertThat(row.getStatusStage1()).isEqualTo("PENDING");
        assertThat(row.getEstBasis()).contains("要求2项","命中1项","50.00%");
    }
    @Test void zeroIsNumericAndResourceDuplicatesDoNotIncreaseCount() {
        var row=item("A1","仓储能力","需提供2个仓库","10","OBJECTIVE");
        when(warehouses.findActiveWarehouses()).thenReturn(List.of(warehouse(1),warehouse(1)));
        refresh(row);assertThat(row.getEstScore()).isEqualByComparingTo("5");
        when(warehouses.findActiveWarehouses()).thenReturn(List.of());service.refreshItemsForProject(1L);
        assertThat(row.getEstScore()).isZero();assertThat(row.getStatusStage1()).isEqualTo("DANGER");assertThat(row.getKbHit()).isFalse();
    }
    @Test void simpleProjectAndBrandEstimatesDoNotUseGenericFactorsAsNameFilters() {
        var project=item("服务案例及业绩","商务","按业绩排序，需提供发票及合同","20","OBJECTIVE");
        var brand=item("品牌及货源渠道管控及佐证材料","技术","每份品牌授权1分，最多20分","20","OBJECTIVE");
        when(items.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(project,brand));
        when(projects.findEstimateCandidates()).thenReturn(List.of(new ProjectMatchedItem(1L,"合同","工业品",null,BigDecimal.TEN)));
        when(brands.findEstimateCandidates(any())).thenReturn(List.of(new BrandMatchedItem(2L,"品牌A","厂家","INDUSTRIAL",null,false)));
        service.refreshItemsForProject(1L);
        assertThat(project.getEstScore()).isEqualByComparingTo("20");assertThat(project.getEstBasis()).contains("未核验");
        assertThat(brand.getEstScore()).isEqualByComparingTo("1");assertThat(brand.getEstBasis()).contains("要求20项");
        verify(projects,never()).match(any());verify(brands,never()).match(any());
    }
    @Test void numericalCodesCannotUseIncidentalDetailWordsToSelectTheWrongLibrary() {
        var row=item("3.3(2)","技术","方案涉及仓库及品牌授权","10","OBJECTIVE");refresh(row);
        assertThat(row.getEstScore()).isNull();verifyNoInteractions(warehouses,brands,projects);
    }
    @Test void subjectiveAndFinancialRowsRemainSeparateWithoutBlockingSupportedRows() {
        var subjective=item("A","服务方案","根据方案合理性评分","10","SUBJECTIVE");
        var finance=item("B","财务状况","根据财务报表评价","10","OBJECTIVE");
        when(items.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(subjective,finance));service.refreshItemsForProject(1L);
        assertThat(subjective.getEstScore()).isNull();assertThat(subjective.getKbHit()).isNull();
        assertThat(finance.getEstScore()).isNull();assertThat(finance.getEstBasis()).contains("财务");
        verifyNoInteractions(certs,warehouses,projects,brands,people);
    }
    @Test void failedQueryClearsHistoricalFullScoreInsteadOfReturningZero() {
        var row=item("A1","仓储能力","提供仓库","10","OBJECTIVE");row.setEstScore(BigDecimal.TEN);row.setEstBasis("旧满分");
        when(warehouses.findActiveWarehouses()).thenThrow(new IllegalStateException("query failed"));refresh(row);
        assertThat(row.getEstScore()).isNull();assertThat(row.getKbHit()).isFalse();assertThat(row.getEstBasis()).contains("计算失败").doesNotContain("旧满分");
    }
    @Test void certificatePathUsesSimpleEstimateAndSealsItsInput() {
        var row=item("管理体系","商务","ISO9001、ISO14001、ISO45001，缺少一项不得分","15","OBJECTIVE");
        when(certs.estimateSimpleScore(any(),any(),any(),any())).thenReturn(new CertMatchService.ScoreEstimate(
                new CertificateScorePolicy.Assessment(new BigDecimal("10"),"PENDING",true,"知识库粗估：2/3"),"simple-fixture"));
        refresh(row);assertThat(row.getEstScore()).isEqualByComparingTo("10");
        assertThat(row.getEstimateInputFingerprint()).isNotBlank();assertThat(row.getEstimatedAt()).isNotNull();
        verify(certs,never()).estimateScore(any(),any(),any(),any());
    }
    @Test void peopleUseExplicitCountWithoutUsingFactorAsAPositionTitle() {
        var row=item("A1","人员团队","需提供2名人员","10","OBJECTIVE");
        when(people.findEstimateCandidates()).thenReturn(List.of(new PersonMatchedItem(1L,"张三","1","工程师",List.of())));
        refresh(row);assertThat(row.getEstScore()).isEqualByComparingTo("5");
        verify(people,never()).match(any());
    }
}
