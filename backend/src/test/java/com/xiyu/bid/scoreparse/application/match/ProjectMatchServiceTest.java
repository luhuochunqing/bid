package com.xiyu.bid.scoreparse.application.match;

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
    @DisplayName("contract_amount NULL 跳过金额比对不失配（research R7）：NULL 行保留")
    void nullContractAmount_skipsAmountCheck() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                record(1L, "存量合同(无金额)", ProjectType.OFFICE, "信息化", null),
                record(2L, "新合同", ProjectType.OFFICE, "信息化", new BigDecimal("5000000"))));
        ProjectMatchRequest request = new ProjectMatchRequest(List.of("信息化"), null,
                new BigDecimal("1000000"), 2);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.matched()).hasSize(2);
        assertThat(result.tier()).isEqualTo("FULL");
        assertThat(((ProjectMatchedItem) result.matched().get(0)).contractAmount()).isNull();
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
    @DisplayName("空结果 → NONE/0 不抛错（FR-024）")
    void noMatch_empty() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());
        ProjectMatchRequest request = new ProjectMatchRequest(List.of("不存在类型"), null, null, 1);

        KnowledgeMatchResult result = service.match(request);

        assertThat(result.tier()).isEqualTo("NONE");
        assertThat(result.matchRatio()).isEqualTo(0);
        assertThat(result.matched()).isEmpty();
    }
}
