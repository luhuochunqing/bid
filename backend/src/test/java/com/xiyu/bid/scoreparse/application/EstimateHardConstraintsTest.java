package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.dto.*;
import com.xiyu.bid.scoreparse.domain.CertificateScorePolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class EstimateHardConstraintsTest extends EstimatedScoreServiceTest {
    private static final LocalDate DAY=LocalDate.of(2026,11,17);
    ProjectMatchedItem contract(long id,String type,LocalDate date,String amount) {
        return new ProjectMatchedItem(id,"合成合同"+id,type,date,amount==null?null:new BigDecimal(amount));
    }
    @Test void projectFiltersDateWindowAmountAndTypeBeforeCounting() {
        var row=item("供应商业绩","商务","需提供2项工业品业绩，2023年1月1日至2025年12月31日，合同金额不少于100万元","10","OBJECTIVE");
        when(projects.findEstimateCandidates()).thenReturn(List.of(
            contract(1,"工业品",LocalDate.of(2023,1,1),"1000000"),
            contract(2,"工业品",LocalDate.of(2022,12,31),"2000000"),
            contract(3,"工业品",null,"2000000"),
            contract(4,"工业品",LocalDate.of(2026,1,1),"2000000"),
            contract(5,"办公",LocalDate.of(2024,1,1),"2000000"),
            contract(6,"工业品",LocalDate.of(2024,1,1),null)));
        refresh(row);
        assertThat(row.getEstScore()).isEqualByComparingTo("5");
        assertThat(row.getEstBasis()).contains("2023-01-01","2025-12-31","命中1项");
    }
    @Test void collectiveRequirementDoesNotCountOfficeContracts() {
        var row=item("供应商业绩","商务","需提供2项集采业绩","10","OBJECTIVE");
        when(projects.findEstimateCandidates()).thenReturn(List.of(
            contract(1,"集采",DAY,"100"),contract(2,"办公",DAY,"100"),contract(3,"办公",DAY,"100")));
        refresh(row);
        assertThat(row.getEstScore()).isEqualByComparingTo("5");
        assertThat(row.getEstBasis()).contains("命中1项","集采");
    }
    @Test void privateEnterpriseRequirementCountsCanonicalPrivateCustomersOnly() {
        var row=item("供应商业绩","商务","需提供2项民营企业业绩","10","OBJECTIVE");
        when(projects.findEstimateCandidates()).thenReturn(List.of(
            new ProjectMatchedItem(1L,"甲合同","办公","甲客户","民企",DAY,BigDecimal.TEN),
            new ProjectMatchedItem(2L,"乙合同","办公","乙客户","央企",DAY,BigDecimal.TEN)));
        refresh(row);
        assertThat(row.getEstScore()).isEqualByComparingTo("5");
        assertThat(row.getEstBasis()).contains("命中1项");
    }
    @Test void baseScoreUsesExistingIncrementalTargetInsteadOfDividingHundredByTwenty() {
        var row=item("供应商业绩","商务","满足资格条件的业绩得基础分60分，在此基础上每增加一个满足资格条件的业绩（2023年1月1日至首次报价截止日，以合同签订时间为准）加20分，满分100分。","5","OBJECTIVE");
        when(projects.findEstimateCandidates()).thenReturn(List.of(contract(1,"工业品",LocalDate.of(2023,1,1),"100")));
        refresh(row);
        assertThat(row.getEstBasis()).contains("要求3项","首次报价截止日");
        assertThat(row.getEstScore()).isEqualByComparingTo("1.67");
    }
    @Test void explicitWarehouseAreaAndRegionConstrainAvailableRecords() {
        var row=item("仓储能力","技术","需提供2个华北地区仓库，面积不少于5000平方米","10","OBJECTIVE");
        when(warehouses.findActiveWarehouses()).thenReturn(List.of(
            new WarehouseMatchedItem(1L,"A","华北",new BigDecimal("6000"),"IN_USE"),
            new WarehouseMatchedItem(2L,"B","华南",new BigDecimal("6000"),"IN_USE"),
            new WarehouseMatchedItem(3L,"C","华北",new BigDecimal("3000"),"IN_USE")));
        refresh(row);assertThat(row.getEstBasis()).contains("命中1项");
    }
    @Test void explicitPositionCannotBeSatisfiedByUnrelatedActiveStaff() {
        var row=item("人员团队","技术","需提供2名高级工程师","10","OBJECTIVE");
        when(people.findEstimateCandidates()).thenReturn(List.of(
            new PersonMatchedItem(1L,"甲","1","高级工程师",List.of()),
            new PersonMatchedItem(2L,"乙","2","文员",List.of())));
        refresh(row);assertThat(row.getEstScore()).isEqualByComparingTo("5");
    }
    @Test void namedBrandCannotBeSatisfiedByAnotherBrand() {
        var row=item("品牌授权","技术","需提供2个品牌授权，品牌为施耐德","10","OBJECTIVE");
        when(brands.findEstimateCandidates(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
            new BrandMatchedItem(1L,"施耐德","甲",null,DAY.plusYears(1),false),
            new BrandMatchedItem(2L,"西门子","乙",null,DAY.plusYears(1),false)));
        refresh(row);assertThat(row.getEstScore()).isEqualByComparingTo("5");
    }
    @Test void brandProductLineFilterKeepsOnlyDeclaredLinesAndAllowsUnconstrainedRecords() {
        var office=new BrandMatchedItem(1L,"甲品牌","甲","OFFICE",null,false);
        var tools=new BrandMatchedItem(2L,"乙品牌","乙","TOOLS",null,false);
        var unknown=new BrandMatchedItem(3L,"丙品牌","丙",null,null,false);
        var candidates=List.of(office,tools,unknown);
        for (String detail : new String[]{null,"需提供品牌授权"}) {
            var constraints=com.xiyu.bid.scoreparse.domain.KnowledgeEvidenceConstraints.parse("BRAND",detail,DAY);
            assertThat(EstimateEvidenceSupport.filter(candidates,constraints,EstimateEvidenceSupport.brandConditions(constraints,detail))).isEqualTo(candidates);
        }
        String detail="产品线：办公；品牌授权。";
        var constraints=com.xiyu.bid.scoreparse.domain.KnowledgeEvidenceConstraints.parse("BRAND",detail,DAY);
        assertThat(EstimateEvidenceSupport.filter(candidates,constraints,EstimateEvidenceSupport.brandConditions(constraints,detail))).isEqualTo(List.of(office));
    }
    @Test void lowerGradeCannotSatisfyFiveStarIdentity() {
        var facts=List.of(new CertificateScorePolicy.Fact("商品售后服务评价认证三星级证书","三星",DAY.plusYears(1),"IN_STOCK",false,null));
        assertThat(CertificateScorePolicy.assessSimple("企业资质","具有商品售后服务评价认证五星级证书得5分",new BigDecimal("5"),DAY,facts).score()).isZero();
    }
    @Test void namedFiveStarCertificateCanProveGradeWithoutSeparateLevelColumn() {
        var facts=List.of(new CertificateScorePolicy.Fact("商品售后服务评价认证五星级证书",null,DAY.plusYears(1),"IN_STOCK",false,null));
        assertThat(CertificateScorePolicy.assessSimple("企业资质","具有商品售后服务评价认证五星级证书得5分",new BigDecimal("5"),DAY,facts).score()).isEqualByComparingTo("5");
    }
    @Test void genericBrandQuantityModifierIsNotAnExplicitBrandName() {
        var row=item("品牌授权","技术","二级类目每份品牌授权得1分，最多10分","10","OBJECTIVE");
        when(brands.findEstimateCandidates(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
            new BrandMatchedItem(1L,"施耐德","甲",null,LocalDate.now().plusYears(1),false)));
        refresh(row);assertThat(row.getEstScore()).isEqualByComparingTo("1");
    }
    @Test void brandAuthorizationMustReachExplicitFutureValidityDate() {
        var deadline=LocalDate.now().plusYears(2);
        var row=item("品牌授权","技术","需提供1个品牌授权，授权有效期至"+deadline,"10","OBJECTIVE");
        when(brands.findEstimateCandidates(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
            new BrandMatchedItem(1L,"施耐德","甲",null,LocalDate.now().plusDays(1),false)));
        refresh(row);assertThat(row.getEstScore()).isZero();
        assertThat(row.getEstBasis()).contains(deadline.toString());
    }
    @Test void invoiceThresholdDoesNotBecomeAContractAmountProof() {
        var row=item("供应商业绩","商务","需提供1项业绩，业绩合同应提供单张金额不低于20万元的增值税发票","10","OBJECTIVE");
        when(projects.findEstimateCandidates()).thenReturn(List.of(contract(1,"工业品",DAY,"10")));
        refresh(row);assertThat(row.getEstScore()).isEqualByComparingTo("10");
        assertThat(row.getStatusStage1()).isEqualTo("PENDING");
        assertThat(row.getEstBasis()).contains("发票").doesNotContain("合同金额不少于200000");
    }
}
