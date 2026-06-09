package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.casework.infrastructure.KnowledgeCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaseExportExcelAppServiceTest {

    private KnowledgeCaseRepository repo;
    private CaseExcelGenerator excelGenerator;
    private CaseExportExcelAppService appService;

    @BeforeEach
    void setUp() {
        repo = mock(KnowledgeCaseRepository.class);
        excelGenerator = new CaseExcelGenerator();
        appService = new CaseExportExcelAppService(repo, excelGenerator);
    }

    @Test
    @DisplayName("空仓库返回空 Excel")
    void emptyRepo_ReturnsEmptyExcel() throws IOException {
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        CaseExportExcelAppService.ExportResult result = appService.exportCasesAsExcel(
                null, null, null, null,
                null, null, null, null, null);

        assertThat(result.recordCount()).isEqualTo(0);
        assertThat(result.data()).isNotEmpty();
        assertThat(result.filename()).endsWith(".xlsx");
    }

    @Test
    @DisplayName("一条数据返回 Excel 含记录")
    void singleRecord_ReturnsExcelWithRecord() throws IOException {
        KnowledgeCase kc = new KnowledgeCase();
        kc.setId(1L);
        kc.setSourceProjectId(100L);
        kc.setSourceProjectName("项目A");
        kc.setScoringPointTitle("评分项A");
        kc.setScoringCategory("技术");
        kc.setCustomerType("国有企业");
        kc.setProjectType("综合");
        kc.setRequirementRaw("需求");
        kc.setResponseText("应答");
        kc.setReuseCount(0);
        kc.setIsPinned(false);
        kc.setStatus("ACTIVE");

        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(kc)));

        CaseExportExcelAppService.ExportResult result = appService.exportCasesAsExcel(
                null, null, null, null,
                null, null, null, null, null);

        assertThat(result.recordCount()).isEqualTo(1);
        assertThat(result.data()).isNotEmpty();
    }

    @Test
    @DisplayName("文件名含 12 位时间戳")
    void filename_HasTimestamp() throws IOException {
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        CaseExportExcelAppService.ExportResult result = appService.exportCasesAsExcel(
                null, null, null, null,
                null, null, null, null, null);

        assertThat(result.filename()).matches("方案管理-案例库台账-\\d{12}\\.xlsx");
    }
}
