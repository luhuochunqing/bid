package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.application.CaseZipPackager;
import com.xiyu.bid.casework.domain.model.CaseExportResult;
import com.xiyu.bid.casework.domain.policy.CaseExportPolicy;
import com.xiyu.bid.casework.dto.CaseExportQuery;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.casework.infrastructure.KnowledgeCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaseExportAppServiceTest {

    private KnowledgeCaseRepository repo;
    private CaseExportPolicy policy;
    private CaseZipPackager packager;
    private CaseExportAppService appService;

    @BeforeEach
    void setUp() {
        repo = mock(KnowledgeCaseRepository.class);
        policy = new CaseExportPolicy();
        packager = new CaseZipPackager();
        appService = new CaseExportAppService(repo, policy, packager);
    }

    @Test
    @DisplayName("空仓库抛出 IllegalStateException（validateExportRequest 拒绝空列表）")
    void exportCases_EmptyRepository_ThrowsException() {
        CaseExportQuery query = new CaseExportQuery(
                null, null, null, null, null,
                null, null, null, null, null);

        when(repo.count(any(Specification.class))).thenReturn(0L);

        assertThatThrownBy(() -> appService.exportCases(query, "操作员"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有可导出的案例");
    }

    @Test
    @DisplayName("成功导出：ZIP 非空、文件名格式正确、案例数正确")
    void exportCases_WithData_ReturnsPopulatedZip() {
        KnowledgeCase kc1 = createCase("评分项A", "技术", "国有企业", "综合");
        KnowledgeCase kc2 = createCase("评分项B", "商务", "民营企业", "工程");

        CaseExportQuery query = new CaseExportQuery(
                null, null, null, null, null,
                null, null, null, null, null);

        when(repo.count(any(Specification.class))).thenReturn(2L);
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(kc1, kc2)));

        CaseExportResult result = appService.exportCases(query, "操作员");
        assertThat(result.caseCount()).isEqualTo(2);
        assertThat(result.zipBytes()).isNotEmpty();
        assertThat(result.totalSize()).isPositive();
        assertThat(result.zipFileName()).startsWith("方案管理-案例库文件包-");
        assertThat(result.zipFileName()).endsWith(".zip");
        assertThat(result.zipFileName()).matches("方案管理-案例库文件包-\\d{14}\\.zip");
    }

    private static KnowledgeCase createCase(
            String title, String category, String customerType, String projectType) {
        KnowledgeCase kc = new KnowledgeCase();
        kc.setId(1L);
        kc.setSourceProjectId(100L);
        kc.setSourceProjectName("测试项目");
        kc.setScoringPointTitle(title);
        kc.setScoringCategory(category);
        kc.setCustomerType(customerType);
        kc.setProjectType(projectType);
        kc.setRequirementRaw("默认需求");
        kc.setResponseText("默认应答全文，用于验证导出流程");
        kc.setReuseCount(0);
        kc.setIsPinned(false);
        kc.setStatus("ACTIVE");
        return kc;
    }
}
