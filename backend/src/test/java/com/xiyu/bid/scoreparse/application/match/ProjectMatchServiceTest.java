package com.xiyu.bid.scoreparse.application.match;

import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import com.xiyu.bid.performance.domain.valueobject.ProjectType;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceRecordEntity;
import com.xiyu.bid.performance.infrastructure.persistence.repository.PerformanceRecordJpaRepository;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;
import com.xiyu.bid.scoreparse.dto.ProjectMatchRequest;
import com.xiyu.bid.scoreparse.dto.ProjectMatchedItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProjectMatchService - 项目业绩匹配（FR-010）")
class ProjectMatchServiceTest {

    @Mock
    private PerformanceRecordJpaRepository repository;

    private ProjectMatchService service;

    @BeforeEach
    void setUp() {
        service = new ProjectMatchService(repository);
    }

    private PerformanceRecordEntity record(Long id, String contractName, ProjectType type,
                                           String industry, BigDecimal amount) {
        return PerformanceRecordEntity.builder()
                .id(id).contractName(contractName).projectType(type).industry(industry)
                .signingDate(LocalDate.now().minusYears(1)).contractAmount(amount)
                .build();
    }

    @Test
    @DisplayName("数量比例：3/3 → FULL/100")
    void fullMatch_threeOfThree() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                record(1L, "合同A", ProjectType.OFFICE, "信息化", new BigDecimal("2000000")),
                record(2L, "合同B", ProjectType.COMPREHENSIVE, "信息化", new BigDecimal("1500000")),
                record(3L, "合同C", ProjectType.OFFICE, null, new BigDecimal("3000000"))));
        ProjectMatchRequest request = new ProjectMatchRequest(List.of("信息化"), null,
                new BigDecimal("1000000"), 3);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.tier()).isEqualTo("FULL");
        assertThat(result.matchRatio()).isEqualTo(100);
        assertThat(result.matched()).hasSize(3);
    }

    @Test
    @DisplayName("数量比例：2/3 → PARTIAL/67")
    void partialMatch_twoOfThree() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                record(1L, "合同A", ProjectType.OFFICE, "信息化", null),
                record(2L, "合同B", ProjectType.COMPREHENSIVE, "信息化", null)));
        ProjectMatchRequest request = new ProjectMatchRequest(List.of("信息化"), null, null, 3);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.tier()).isEqualTo("PARTIAL");
        assertThat(result.matchRatio()).isEqualTo(67);
    }

    @Test
    @DisplayName("要求合同金额时 NULL 记录缺少证据，不能计入得分")
    void nullContractAmount_isExcludedWhenAmountRequired() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                record(1L, "存量合同(无金额)", ProjectType.OFFICE, "信息化", null),
                record(2L, "新合同", ProjectType.OFFICE, "信息化", new BigDecimal("5000000"))));
        ProjectMatchRequest request = new ProjectMatchRequest(List.of("信息化"), null,
                new BigDecimal("1000000"), 2);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.matched()).hasSize(1);
        assertThat(result.tier()).isEqualTo("PARTIAL");
        assertThat(((ProjectMatchedItem) result.matched().get(0)).contractAmount())
                .isEqualByComparingTo("5000000");
    }

    @Test
    @DisplayName("金额低于下限 → 剔除")
    void belowMinAmount_excluded() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                record(1L, "小合同", ProjectType.OFFICE, "信息化", new BigDecimal("500000")),
                record(2L, "大合同", ProjectType.OFFICE, "信息化", new BigDecimal("5000000"))));
        ProjectMatchRequest request = new ProjectMatchRequest(List.of("信息化"), null,
                new BigDecimal("1000000"), 2);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.matched()).hasSize(1);
        assertThat(((ProjectMatchedItem) result.matched().get(0)).contractName()).isEqualTo("大合同");
    }

    @Test
    @DisplayName("每条业绩返回日期、金额和客户逐条件证明")
    void matchedProject_returnsAuditableProofs() {
        PerformanceRecordEntity row = PerformanceRecordEntity.builder()
                .id(9L).contractName("央企采购合同").projectType(ProjectType.INDUSTRIAL)
                .groupCompany("某央企").customerType(CustomerType.CENTRAL_SOE)
                .signingDate(LocalDate.of(2025, 1, 1)).contractAmount(new BigDecimal("2000000"))
                .build();
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(row));

        KnowledgeMatchResult result = service.match(new ProjectMatchRequest(
                List.of("工业品"), LocalDate.of(2024, 1, 1), new BigDecimal("1000000"), 1));

        assertThat(result.proofs()).hasSize(1);
        assertThat(result.proofs().get(0).recordId()).isEqualTo("9");
        assertThat(result.proofs().get(0).conditions())
                .extracting(KnowledgeMatchResult.ConditionProof::condition)
                .contains("签约日期不早于 2024-01-01", "合同金额不少于 1000000", "客户");
        assertThat(result.proofs().get(0).conditions())
                .allMatch(proof -> "PASS".equals(proof.status()));
    }

    @Test
    @DisplayName("空结果 → NONE/0 不抛错（FR-024）")
    void noMatch_empty() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());
        ProjectMatchRequest request = new ProjectMatchRequest(List.of("不存在类型"), null, null, 1);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.tier()).isEqualTo("NONE");
        assertThat(result.matchRatio()).isEqualTo(0);
        assertThat(result.matched()).isEmpty();
    }

    @Test
    @DisplayName("匹配服务：无约束请求（关键词全被过滤或为空且无时间约束）严禁全量匹配返回 FULL，应返回 NONE 待人工确认")
    void noConstraints_returnsNoneRatherThanFull() {
        ProjectMatchRequest request = new ProjectMatchRequest(List.of("优秀得7分", "情况"), null, null, 1);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.tier()).isEqualTo("NONE");
        assertThat(result.matchRatio()).isEqualTo(0);
        assertThat(result.matched()).isEmpty();
        assertThat(result.matchDetail()).contains("未抽取到可执行的业绩匹配约束");
        verify(repository, never()).findAll(any(Specification.class));
    }

    @Test
    @DisplayName("匹配服务：入参为 null 时优雅返回空匹配并阻断全表查询")
    void match_nullRequest_returnsEmptyAndNeverCallsRepository() {
        KnowledgeMatchResult result = service.match(null);
        assertThat(result.tier()).isEqualTo("NONE");
        assertThat(result.matched()).isEmpty();
        verify(repository, never()).findAll(any(Specification.class));
    }

    @Test
    @DisplayName("匹配服务：长分句关键词走 match() 全被过滤 → NONE 且不查表（Warehouse 全通用词用例的对等项）")
    void longClauseKeywords_returnsNoneAndNeverCallsRepository() {
        ProjectMatchRequest request = new ProjectMatchRequest(List.of(
                "为央企二级单位及以上或省级及以上政府机关所提供的与本项目类似采购供货服务数量进行评价"), null, null, 1);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.tier()).isEqualTo("NONE");
        assertThat(result.matchRatio()).isEqualTo(0);
        assertThat(result.matched()).isEmpty();
        verify(repository, never()).findAll(any(Specification.class));
    }

    @Test
    @DisplayName("过滤通用评分短语与长分句，防止长子句污染业绩行业匹配，同时保留合法业务词")
    void filterGenericKeywords_filtersClauseText() {
        List<String> raw = List.of(
                "对应答人在2023年1月1日至2025年12月31日",
                "为央企二级单位及以上或省级及以上政府机关所提供的与本项目类似采购供货服务数量进行评价",
                "优秀得7分",
                "电力",
                "电商",
                "电力供应情况",
                "一般纳税人"
        );
        List<String> filtered = ProjectMatchService.filterSpecificProjectKeywords(raw);
        assertThat(filtered).contains("电力", "电商", "电力供应情况", "一般纳税人");
        assertThat(filtered).doesNotContain("优秀得7分");
    }

    @Test
    @DisplayName("客户类型解析：央企/中央企业/地方国企/政府")
    void parseCustomerType_recognizesKeywords() {
        assertThat(ProjectMatchService.parseCustomerType("央企")).isEqualTo(CustomerType.CENTRAL_SOE);
        assertThat(ProjectMatchService.parseCustomerType("中央企业")).isEqualTo(CustomerType.CENTRAL_SOE);
        assertThat(ProjectMatchService.parseCustomerType("中央金融企业")).isEqualTo(CustomerType.CENTRAL_SOE);
        assertThat(ProjectMatchService.parseCustomerType("国企")).isEqualTo(CustomerType.LOCAL_SOE);
        assertThat(ProjectMatchService.parseCustomerType("地方国企")).isEqualTo(CustomerType.LOCAL_SOE);
        assertThat(ProjectMatchService.parseCustomerType("长句含有央企但超过10字")).isNull();
    }

    @Test
    @DisplayName("项目类型解析：办公用品按办公业绩精确匹配")
    void parseProjectType_officeSupplies_mapsToOffice() {
        assertThat(ProjectMatchService.parseProjectType("办公用品")).isEqualTo(ProjectType.OFFICE);
        assertThat(ProjectMatchService.parseProjectType("办公类")).isEqualTo(ProjectType.OFFICE);
    }

    @Test
    @DisplayName("1374 央企业绩匹配：央企+MRO+2022年起 → 命中央企MRO合同")
    void match_centralSOE_matchesCentralSOEContracts() {
        PerformanceRecordEntity row1 = PerformanceRecordEntity.builder()
                .id(908L)
                .contractName("中核集团电子商城 MRO工业品电商引入项目框架协议")
                .customerType(CustomerType.CENTRAL_SOE)
                .industry("国防军工")
                .projectType(ProjectType.INDUSTRIAL)
                .signingDate(LocalDate.of(2023, 1, 1))
                .build();
        PerformanceRecordEntity row2 = PerformanceRecordEntity.builder()
                .id(956L)
                .contractName("中国大唐集团有限公司MRO工业品电商项目2023-2026年度长协采购协议")
                .customerType(CustomerType.CENTRAL_SOE)
                .industry("电力")
                .projectType(ProjectType.INDUSTRIAL)
                .signingDate(LocalDate.of(2023, 5, 1))
                .build();

        when(repository.findAll(any(Specification.class))).thenReturn(List.of(row1, row2));

        ProjectMatchRequest request = new ProjectMatchRequest(
                List.of("央企", "MRO"),
                LocalDate.of(2022, 1, 1),
                null,
                2
        );

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.tier()).isEqualTo("FULL");
        assertThat(result.matchRatio()).isEqualTo(100);
        assertThat(result.matched()).hasSize(2);
        assertThat(result.matchDetail()).contains("命中业绩 2/2");
    }
}
