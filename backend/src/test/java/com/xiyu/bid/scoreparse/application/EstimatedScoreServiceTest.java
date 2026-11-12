package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.application.match.BrandMatchService;
import com.xiyu.bid.scoreparse.application.match.CertMatchService;
import com.xiyu.bid.scoreparse.application.match.PersonMatchService;
import com.xiyu.bid.scoreparse.application.match.ProjectMatchService;
import com.xiyu.bid.scoreparse.application.match.WarehouseMatchService;
import com.xiyu.bid.scoreparse.dto.BrandMatchRequest;
import com.xiyu.bid.scoreparse.domain.CertificateScorePolicy;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;
import com.xiyu.bid.scoreparse.dto.PersonMatchRequest;
import com.xiyu.bid.scoreparse.dto.ProjectMatchRequest;
import com.xiyu.bid.scoreparse.dto.ProjectMatchedItem;
import com.xiyu.bid.scoreparse.dto.WarehouseMatchRequest;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.dto.WarehouseMatchedItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段 1 预计得分服务测试（spec 041 US3 / FR-011 / FR-014 / FR-015 / FR-018）。
 * <p>mock 五个知识库匹配服务，验证分型调用、策略回填与主观项强制 null。
 */
@ExtendWith(MockitoExtension.class)
class EstimatedScoreServiceTest {

    @Mock
    private ScoreItemRepository itemRepository;
    @Mock
    private CertMatchService certMatchService;
    @Mock
    private PersonMatchService personMatchService;
    @Mock
    private ProjectMatchService projectMatchService;
    @Mock
    private WarehouseMatchService warehouseMatchService;
    @Mock
    private BrandMatchService brandMatchService;

    private EstimatedScoreService service;

    @BeforeEach
    void setUp() {
        service = new EstimatedScoreService(itemRepository,
                certMatchService, personMatchService, projectMatchService,
                warehouseMatchService, brandMatchService);
    }

    private ScoreItem item(String dim, String detail, String weight, String scoreType) {
        return ScoreItem.builder()
                .projectId(1L)
                .parseTaskId(1L)
                .itemIndex(1)
                .code("A1")
                .dim(dim)
                .detail(detail)
                .weight(new BigDecimal(weight))
                .scoreType(scoreType)
                .statusStage1("PENDING")
                .build();
    }

    @Test
    void explicitRefreshWithoutCertificatesRecalculatesWarehouseAndClearsHistoricalBasis() {
        ScoreItem warehouse = item("仓储能力", "六大地区华北东北华东中南西南西北各一个仓库", "25", "OBJECTIVE");
        warehouse.setEstBasis("未抽取到可执行的仓库匹配约束（面积）");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(warehouse));
        when(warehouseMatchService.findActiveWarehouses()).thenReturn(List.of());

        assertThat(service.refreshItemsForProject(1L)).containsExactly(warehouse);

        assertThat(warehouse.getEstScore()).isEqualByComparingTo("0");
        assertThat(warehouse.getEstBasis()).isEqualTo("知识库未匹配到在用仓储数据");
        verify(itemRepository).saveAll(List.of(warehouse));
    }

    @Test
    void objectiveCert_fullMatch_estimatesFullScore() {
        ScoreItem cert = item("资质", "具有 ISO9001 质量管理体系认证证书", "8", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(cert));
        when(certMatchService.estimateScore(any(), any(), any(), any()))
                .thenReturn(new CertMatchService.ScoreEstimate(new CertificateScorePolicy.Assessment(
                        new BigDecimal("8"), "OK", true, "命中 1 条有效资质"), "fixture-input"));

        service.estimateForProject(1L);

        verify(itemRepository).saveAll(List.of(cert));
        assertThat(cert.getEstScore()).isEqualByComparingTo("8");
        assertThat(cert.getStatusStage1()).isEqualTo("OK");
        assertThat(cert.getKbHit()).isTrue();
        assertThat(cert.getEstBasis()).contains("命中 1 条有效资质");
    }

    @Test
    void objectiveCert_partialMatch_estimatesPartialScore() {
        ScoreItem cert = item("资质", "具有 ISO9001 证书", "8", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(cert));
        when(certMatchService.estimateScore(any(), any(), any(), any()))
                .thenReturn(new CertMatchService.ScoreEstimate(new CertificateScorePolicy.Assessment(
                        new BigDecimal("4"), "PENDING", true, "按明确规则获得部分得分"), "fixture-input"));

        service.estimateForProject(1L);

        verify(itemRepository).saveAll(List.of(cert));
        assertThat(cert.getEstScore()).isEqualByComparingTo("4");
        assertThat(cert.getStatusStage1()).isEqualTo("PENDING");
        assertThat(cert.getKbHit()).isTrue();
    }

    @Test
    void objectiveCert_noneMatch_zeroScoreDanger() {
        ScoreItem cert = item("资质", "具有 ISO9001 证书", "8", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(cert));
        when(certMatchService.estimateScore(any(), any(), any(), any()))
                .thenReturn(new CertMatchService.ScoreEstimate(new CertificateScorePolicy.Assessment(
                        BigDecimal.ZERO, "DANGER", false, "未命中任何资质证书"), "fixture-input"));

        service.estimateForProject(1L);

        verify(itemRepository).saveAll(List.of(cert));
        assertThat(cert.getEstScore()).isEqualByComparingTo("0");
        assertThat(cert.getStatusStage1()).isEqualTo("DANGER");
        assertThat(cert.getKbHit()).isFalse();
        assertThat(cert.getEstBasis()).contains("未命中");
    }

    @Test
    void matchFailure_clearsStaleScoreAndBasis() {
        ScoreItem cert = item("资质", "具有 ISO9001 证书", "8", "OBJECTIVE");
        cert.setEstScore(new BigDecimal("8"));
        cert.setKbHit(true);
        cert.setEstBasis("旧的满分依据");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(cert));
        when(certMatchService.estimateScore(any(), any(), any(), any())).thenThrow(new IllegalStateException("test"));

        service.estimateForProject(1L);

        assertThat(cert.getEstScore()).isNull();
        assertThat(cert.getKbHit()).isFalse();
        assertThat(cert.getStatusStage1()).isEqualTo("PENDING");
        assertThat(cert.getEstBasis()).contains("计算失败", "待人工确认").doesNotContain("旧的满分依据");
    }

    @Test
    void expiredCert_cannotSatisfyExplicitValidCertificateRequirement() {
        ScoreItem cert = item("资质", "具有 ISO9001 证书", "8", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(cert));
        when(certMatchService.estimateScore(any(), any(), any(), any()))
                .thenReturn(new CertMatchService.ScoreEstimate(new CertificateScorePolicy.Assessment(
                        BigDecimal.ZERO, "DANGER", true, "ISO9001：证书已过期，不满足有效证书要求"), "fixture-input"));

        service.estimateForProject(1L);

        verify(itemRepository).saveAll(List.of(cert));
        assertThat(cert.getStatusStage1()).isEqualTo("DANGER");
        assertThat(cert.getEstBasis()).contains("过期");
    }

    @Test
    void personItem_callsPersonMatch_withExtractedCount() {
        ScoreItem person = item("人员", "项目团队成员不少于 5 人", "10", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(person));
        when(personMatchService.match(any(PersonMatchRequest.class)))
                .thenReturn(new KnowledgeMatchResult("PARTIAL", 60, List.of(), "符合 3/5"));

        service.estimateForProject(1L);

        ArgumentCaptor<PersonMatchRequest> requestCaptor = ArgumentCaptor.forClass(PersonMatchRequest.class);
        verify(personMatchService).match(requestCaptor.capture());
        assertThat(requestCaptor.getValue().requiredCount()).isEqualTo(5);
        verify(certMatchService, never()).match(any());
        assertThat(person.getEstScore()).isEqualByComparingTo("6");
        assertThat(person.getStatusStage1()).isEqualTo("PENDING");
    }

    @Test
    void projectItem_callsProjectMatch() {
        ScoreItem project = item("业绩", "近三年类似项目业绩不少于 3 个", "6", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(project));
        when(projectMatchService.match(any(ProjectMatchRequest.class)))
                .thenReturn(new KnowledgeMatchResult("FULL", 100, List.of(), "命中 3 条业绩"));

        service.estimateForProject(1L);

        verify(projectMatchService).match(any(ProjectMatchRequest.class));
        assertThat(project.getEstScore()).isEqualByComparingTo("6");
        assertThat(project.getStatusStage1()).isEqualTo("OK");
    }

    @Test
    void project22IncrementalRule_scoresOnlyPerformancesBeyondTheFreeFirstItem() {
        String detail = "供应商具有近三年内（2023年1月1日起至今，供应商具有1\n项大型集团型国央企电子商城办公用品服务业绩的不得分，每\n"
                + "增加1个得1分，最高得7分。";
        ScoreItem project = item("business", detail, "7", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(22L)).thenReturn(List.of(project));
        List<ProjectMatchedItem> matched = java.util.stream.LongStream.rangeClosed(1, 8)
                .mapToObj(id -> new ProjectMatchedItem(id, "合同" + id, "办公用品电子商城", "央企客户" + id,
                        "央企", LocalDate.of(2023, 1, 1), null))
                .toList();
        when(projectMatchService.match(any(ProjectMatchRequest.class)))
                .thenReturn(new KnowledgeMatchResult("FULL", 100, matched, "命中业绩 8/8"));

        service.estimateForProject(22L);

        ArgumentCaptor<ProjectMatchRequest> captor = ArgumentCaptor.forClass(ProjectMatchRequest.class);
        verify(projectMatchService).match(captor.capture());
        assertThat(captor.getValue().requiredCount()).isEqualTo(8);
        assertThat(project.getEstScore()).isEqualByComparingTo("7");
        assertThat(project.getStatusStage1()).isEqualTo("OK");
    }

    @Test
    void unparsedContractAmountThreshold_staysPendingWithoutMatching() {
        ScoreItem project = item("业绩", "合同金额不低于人民币壹仟万元的项目业绩", "6", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(project));

        service.estimateForProject(1L);

        verify(projectMatchService, never()).match(any());
        assertThat(project.getEstScore()).isNull();
        assertThat(project.getKbHit()).isFalse();
        assertThat(project.getEstBasis()).contains("金额门槛", "待人工确认");
    }

    @Test
    void project1117_candidatesWithoutMandatoryDocuments_stayPending() {
        String detail = "每提供一个有效业绩得5分，最高30分。有效业绩须同时满足："
                + "2021年1月1日起签订，合同金额在1000万元以上；"
                + "提供合作客户名单、项目联系人及供货覆盖城市并加盖公章；"
                + "提供付款银行流水及对应发票扫描件。";
        ScoreItem project = item("公司业绩", detail, "30", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1117L)).thenReturn(List.of(project));
        when(projectMatchService.match(any(ProjectMatchRequest.class)))
                .thenReturn(new KnowledgeMatchResult("FULL", 100, List.of("候选1", "候选2"), "命中业绩 131/6"));

        service.estimateForProject(1117L);

        ArgumentCaptor<ProjectMatchRequest> captor = ArgumentCaptor.forClass(ProjectMatchRequest.class);
        verify(projectMatchService).match(captor.capture());
        assertThat(captor.getValue().minContractAmount()).isEqualByComparingTo("10000000");
        assertThat(captor.getValue().requiredCount()).isEqualTo(6);
        assertThat(project.getEstScore()).isEqualByComparingTo("30");
        assertThat(project.getStatusStage1()).isEqualTo("OK");
        assertThat(project.getKbHit()).isTrue();
        assertThat(project.getEstBasis())
                .contains("业绩库", "付款银行流水", "对应发票");
    }

    @Test
    void project1117_warehouseMultiRegion_calculatesScore() {
        String detail = "1、评审标准: 1) 投标人需提供指定六大地区内各一个配送仓库，得15分，缺一个区域扣3分。（①华北地区（北京市、天津市、河北省、山西省、内蒙古）②东北地区（辽宁省、吉林省、黑龙江省）③华东地区（上海市、江苏省、浙江省、安徽省、江西省、福建省、山东省）④中南地区（河南省、湖北省、湖南省、广东省、海南省、广西壮族自治区）⑤西南地区（重庆市、四川省、贵州省、云南省）⑥西北地区（青海省、甘肃省、宁夏省））。2）六大区域每多一个仓库得1分，满分5分；3）按投标人自有及租赁仓储面积进行排序，第一名得分为5分，第二名3分，第三名1分，其余不得分。";
        ScoreItem warehouse = item("仓储能力", detail, "25", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1117L)).thenReturn(List.of(warehouse));
        when(warehouseMatchService.findActiveWarehouses()).thenReturn(List.of(
                new WarehouseMatchedItem(1L, "北京仓", "华北", new BigDecimal("2000"), "IN_USE"),
                new WarehouseMatchedItem(2L, "沈阳仓", "东北", new BigDecimal("2000"), "IN_USE"),
                new WarehouseMatchedItem(3L, "上海仓", "华东", new BigDecimal("5000"), "IN_USE"),
                new WarehouseMatchedItem(4L, "广州仓", "中南", new BigDecimal("3000"), "IN_USE"),
                new WarehouseMatchedItem(5L, "成都仓", "西南", new BigDecimal("3000"), "IN_USE"),
                new WarehouseMatchedItem(6L, "西安仓", "西北", new BigDecimal("2000"), "IN_USE"),
                new WarehouseMatchedItem(7L, "苏州仓", "华东", new BigDecimal("4000"), "IN_USE"),
                new WarehouseMatchedItem(8L, "武汉仓", "中南", new BigDecimal("3000"), "IN_USE"),
                new WarehouseMatchedItem(9L, "天津仓", "华北", new BigDecimal("2000"), "IN_USE"),
                new WarehouseMatchedItem(10L, "重庆仓", "西南", new BigDecimal("2000"), "IN_USE"),
                new WarehouseMatchedItem(11L, "杭州仓", "华东", new BigDecimal("2000"), "IN_USE")
        ));

        service.estimateForProject(1117L);

        assertThat(warehouse.getEstScore()).isEqualByComparingTo("20");
        assertThat(warehouse.getStatusStage1()).isEqualTo("OK");
        assertThat(warehouse.getKbHit()).isTrue();
        assertThat(warehouse.getEstBasis()).contains("覆盖六大区域中 6 个地区", "15", "增分 5 分", "另 5 分仓储面积横向排序");
    }

    @Test
    void centralSOEProjectItem_extractsKeywordsAndSignedAfter_andEstimatesScore() {
        String detail = "在满足资格条件的基础上，每增加1个有效业绩得1分，最高得5分（如多个业绩合同方为同一家公司，则算1个有效业绩）。提供业绩合同关键页复印件。 注：业绩合同内容为：中央企业（集团）全系统范围或中央金融企业的MRO工业品类交易服务。时间为2022年1月至今。";
        ScoreItem project = item("央企案例", detail, "5", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(project));
        when(projectMatchService.match(any(ProjectMatchRequest.class)))
                .thenReturn(new KnowledgeMatchResult("FULL", 100, List.of(), "命中业绩 20/5"));

        service.estimateForProject(1L);

        ArgumentCaptor<ProjectMatchRequest> captor = ArgumentCaptor.forClass(ProjectMatchRequest.class);
        verify(projectMatchService).match(captor.capture());
        ProjectMatchRequest captured = captor.getValue();
        assertThat(captured.projectTypeKeywords()).contains("中央企业", "央企", "MRO");
        assertThat(captured.signedAfter()).isEqualTo(LocalDate.of(2022, 1, 1));
        assertThat(captured.requiredCount()).isEqualTo(5);
        assertThat(project.getEstScore()).isNull();
        assertThat(project.getStatusStage1()).isEqualTo("PENDING");
        assertThat(project.getKbHit()).isFalse();
        assertThat(project.getEstBasis()).contains("合同或中标证明材料");
    }

    @Test
    void subjectiveItem_forcedNull_noMatchCalls() {
        ScoreItem subjective = item("技术方案", "技术方案科学合理", "10", "SUBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(subjective));

        service.estimateForProject(1L);

        verify(certMatchService, never()).match(any());
        verify(personMatchService, never()).match(any());
        verify(projectMatchService, never()).match(any());
        verify(warehouseMatchService, never()).match(any(WarehouseMatchRequest.class));
        verify(brandMatchService, never()).match(any(BrandMatchRequest.class));
        assertThat(subjective.getEstScore()).isNull();
        assertThat(subjective.getKbHit()).isNull();
        assertThat(subjective.getStatusStage1()).isEqualTo("PENDING");
        assertThat(subjective.getEstBasis()).contains("主观");
    }

    @Test
    void otherCategory_noAutoScore_staysPending() {
        ScoreItem other = item("报价", "报价得分以评标基准价计算", "10", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(other));

        service.estimateForProject(1L);

        verify(certMatchService, never()).match(any());
        assertThat(other.getEstScore()).isNull();
        assertThat(other.getStatusStage1()).isEqualTo("PENDING");
        assertThat(other.getEstBasis()).contains("人工");
    }

    @Test
    void warehouseItem_callsWarehouseMatch() {
        ScoreItem warehouse = item("仓储", "自有仓储面积不少于 5000 平方米", "5", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(warehouse));
        when(warehouseMatchService.match(any(WarehouseMatchRequest.class)))
                .thenReturn(new KnowledgeMatchResult("FULL", 100, List.of(), "命中 1 处仓库"));

        service.estimateForProject(1L);

        ArgumentCaptor<WarehouseMatchRequest> captor = ArgumentCaptor.forClass(WarehouseMatchRequest.class);
        verify(warehouseMatchService).match(captor.capture());
        WarehouseMatchRequest request = captor.getValue();
        assertThat(request.minArea()).isEqualByComparingTo("5000");
        assertThat(request.nameKeywords() == null || request.nameKeywords().isEmpty()).isTrue();
        assertThat(request.region()).isNull();
        assertThat(warehouse.getStatusStage1()).isEqualTo("OK");
        assertThat(warehouse.getEstScore()).isEqualByComparingTo("5");
    }

    @Test
    void brandItem_callsBrandMatch() {
        ScoreItem brand = item("品牌", "提供设备品牌厂家授权书", "4", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(brand));
        when(brandMatchService.match(any(BrandMatchRequest.class)))
                .thenReturn(new KnowledgeMatchResult("FULL", 100, List.of(), "命中 1 条授权"));

        service.estimateForProject(1L);

        verify(brandMatchService).match(any(BrandMatchRequest.class));
        assertThat(brand.getEstScore()).isEqualByComparingTo("4");
    }

    @Test
    void emptyItems_noop() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of());

        service.estimateForProject(1L);

        verify(itemRepository).saveAll(List.of());
    }

    @Test
    void financeItem_doesNotCallPersonMatch_evenIfDetailMentionsStaff() {
        ScoreItem finance = item("财务状况",
                "1.人员配置方面：项目经理、技术人员花名册。"
                        + "现金流量净额连续三年为正的得1.5分。须提供财务报表",
                "3", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(finance));

        service.estimateForProject(1L);

        verify(personMatchService, never()).match(any());
        verify(brandMatchService, never()).match(any());
        verify(warehouseMatchService, never()).match(any());
        assertThat(finance.getEstScore()).isNull();
        assertThat(finance.getStatusStage1()).isEqualTo("PENDING");
        assertThat(finance.getEstBasis()).doesNotContain("人员");
    }

    @Test
    void servicePlan_doesNotCallBrandMatch_whenFactorIsScheme() {
        ScoreItem plan = item("针对MRO工业品综合服务方案",
                "商品上架管理品类及主流品牌覆盖管理、厂家授权",
                "10", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(plan));

        service.estimateForProject(1L);

        verify(brandMatchService, never()).match(any());
        assertThat(plan.getStatusStage1()).isEqualTo("PENDING");
    }

    @Test
    void logisticsScheme_doesNotCallWarehouseMatch() {
        ScoreItem scheme = item("针对MRO工业品物流仓储方案",
                "仓储方案须涵盖省、市、区（县）各层级详细内容",
                "8", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(scheme));

        service.estimateForProject(1L);

        verify(warehouseMatchService, never()).match(any());
        assertThat(scheme.getStatusStage1()).isEqualTo("PENDING");
    }

    @Test
    void warehouseItem_withoutAreaConstraint_staysPending_doesNotCallMatch() {
        ScoreItem warehouse = item("服务网点及仓储",
                "供应商须在全国所有省份（含直辖市）中均设有仓储地点的得1.5分。"
                        + "须提供全国各省仓储地点的统计表，仓库面积、类型等信息",
                "3", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(warehouse));

        service.estimateForProject(1L);

        verify(warehouseMatchService, never()).match(any(WarehouseMatchRequest.class));
        assertThat(warehouse.getEstScore()).isNull();
        assertThat(warehouse.getKbHit()).isFalse();
        assertThat(warehouse.getStatusStage1()).isEqualTo("PENDING");
        assertThat(warehouse.getEstBasis()).contains("约束");
        assertThat(warehouse.getEstBasis()).doesNotContain("tier=FULL");
    }

    @Test
    void financialItem_staysPending_withoutCallingMatch() {
        ScoreItem finance = item("财务状况",
                "速动比率=速动资产/流动负债，近三年均大于1的得满分",
                "5", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(finance));

        service.estimateForProject(1L);

        verify(certMatchService, never()).match(any());
        verify(personMatchService, never()).match(any());
        verify(projectMatchService, never()).match(any());
        verify(warehouseMatchService, never()).match(any());
        verify(brandMatchService, never()).match(any());
        assertThat(finance.getEstScore()).isNull();
        assertThat(finance.getKbHit()).isFalse();
        assertThat(finance.getStatusStage1()).isEqualTo("PENDING");
        assertThat(finance.getEstBasis()).contains("财务类评分项目", "待人工确认");
    }

    @Test
    void creditItem_staysPending_withoutCallingMatch() {
        ScoreItem credit = item("信用评价",
                "年度评价为A级纳税信用的得满分，B级得50%分",
                "3", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(credit));

        service.estimateForProject(1L);

        verify(certMatchService, never()).match(any());
        verify(personMatchService, never()).match(any());
        verify(projectMatchService, never()).match(any());
        verify(warehouseMatchService, never()).match(any());
        verify(brandMatchService, never()).match(any());
        assertThat(credit.getEstScore()).isNull();
        assertThat(credit.getKbHit()).isFalse();
        assertThat(credit.getStatusStage1()).isEqualTo("PENDING");
        assertThat(credit.getEstBasis()).contains("信用评价类评分项目", "待人工确认");
    }

    @Test
    void performanceItem_referencesQualification_pullsCrossChapterConstraint() {
        com.xiyu.bid.casework.application.port.TenderQualificationQueryPort qualPort =
                org.mockito.Mockito.mock(com.xiyu.bid.casework.application.port.TenderQualificationQueryPort.class);
        EstimatedScoreService customService = new EstimatedScoreService(itemRepository,
                certMatchService, personMatchService, projectMatchService,
                warehouseMatchService, brandMatchService, qualPort);

        String detail = "满足资格条件的业绩得基础分60分，在此基础上每增加一个满足资格条件的业绩（2023年1月1日至首次报价截止日，以合同签订时间为准）加20分，满分100分。 | 5%";
        ScoreItem item = item("供应商业绩", detail, "5", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(item));

        when(qualPort.findByProjectId(1L)).thenReturn(java.util.Optional.of(
                new com.xiyu.bid.casework.application.port.TenderQualificationQueryPort.TenderQualificationInfo(
                        List.of("投标人自2021年1月1日至今具有工业品供货业绩，且单项合同金额不低于1000万元"),
                        "资格审查标准：需提供合同首页与发票"
                )
        ));

        ProjectMatchedItem matchedItem1 = new ProjectMatchedItem(1L, "合同1", "工业品采购", "集团", "央企", LocalDate.of(2023, 2, 1), new BigDecimal("12000000"));
        ProjectMatchedItem matchedItem2 = new ProjectMatchedItem(2L, "合同2", "工业品供货", "集团", "央企", LocalDate.of(2023, 3, 1), new BigDecimal("15000000"));
        ProjectMatchedItem matchedItem3 = new ProjectMatchedItem(3L, "合同3", "工业品MRO", "集团", "央企", LocalDate.of(2023, 4, 1), new BigDecimal("20000000"));

        when(projectMatchService.match(any())).thenAnswer(invocation -> {
            ProjectMatchRequest req = invocation.getArgument(0);
            assertThat(req.minContractAmount()).isEqualByComparingTo("10000000");
            assertThat(req.projectTypeKeywords()).contains("工业品");
            return new KnowledgeMatchResult("FULL", 100, List.of(matchedItem1, matchedItem2, matchedItem3), "命中3项业绩", List.of());
        });

        customService.estimateForProject(1L);

        assertThat(item.getEstScore()).isEqualByComparingTo("5.00");
        assertThat(item.getKbHit()).isTrue();
        assertThat(item.getStatusStage1()).isEqualTo("OK");
    }
}
