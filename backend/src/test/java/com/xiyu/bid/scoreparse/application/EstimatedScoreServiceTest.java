package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.application.match.BrandMatchService;
import com.xiyu.bid.scoreparse.application.match.CertMatchService;
import com.xiyu.bid.scoreparse.application.match.PersonMatchService;
import com.xiyu.bid.scoreparse.application.match.ProjectMatchService;
import com.xiyu.bid.scoreparse.application.match.WarehouseMatchService;
import com.xiyu.bid.scoreparse.dto.BrandMatchRequest;
import com.xiyu.bid.scoreparse.dto.CertMatchRequest;
import com.xiyu.bid.scoreparse.dto.CertMatchedItem;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;
import com.xiyu.bid.scoreparse.dto.PersonMatchRequest;
import com.xiyu.bid.scoreparse.dto.ProjectMatchRequest;
import com.xiyu.bid.scoreparse.dto.WarehouseMatchRequest;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
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
    void objectiveCert_fullMatch_estimatesFullScore() {
        ScoreItem cert = item("资质", "具有 ISO9001 质量管理体系认证证书", "8", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(cert));
        when(certMatchService.match(any(CertMatchRequest.class)))
                .thenReturn(new KnowledgeMatchResult("FULL", 100, List.of(), "命中 1 条有效资质"));

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
        when(certMatchService.match(any(CertMatchRequest.class)))
                .thenReturn(new KnowledgeMatchResult("PARTIAL", 50, List.of(), "命中部分资质"));

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
        when(certMatchService.match(any(CertMatchRequest.class)))
                .thenReturn(KnowledgeMatchResult.empty("未命中任何资质证书"));

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
        when(certMatchService.match(any(CertMatchRequest.class))).thenThrow(new IllegalStateException("test"));

        service.estimateForProject(1L);

        assertThat(cert.getEstScore()).isNull();
        assertThat(cert.getKbHit()).isFalse();
        assertThat(cert.getStatusStage1()).isEqualTo("PENDING");
        assertThat(cert.getEstBasis()).contains("计算失败", "待人工确认").doesNotContain("旧的满分依据");
    }

    @Test
    void expiredCert_fullMatch_staysPending() {
        ScoreItem cert = item("资质", "具有 ISO9001 证书", "8", "OBJECTIVE");
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(1L)).thenReturn(List.of(cert));
        when(certMatchService.match(any(CertMatchRequest.class)))
                .thenReturn(new KnowledgeMatchResult("PARTIAL", 100,
                        List.of(new CertMatchedItem(1L, "ISO9001", "一级",
                                LocalDate.now().minusDays(1), true)),
                        "命中 1 条资质，其中含过期证书"));

        service.estimateForProject(1L);

        verify(itemRepository).saveAll(List.of(cert));
        assertThat(cert.getStatusStage1()).isEqualTo("PENDING");
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
        assertThat(project.getEstScore()).isNull();
        assertThat(project.getStatusStage1()).isEqualTo("PENDING");
        assertThat(project.getKbHit()).isFalse();
        assertThat(project.getEstBasis())
                .contains("业绩库", "2 条候选资料", "尚不能确认", "付款银行流水", "对应发票")
                .doesNotContain("tier=", "FULL", "知识库命中");
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
}
