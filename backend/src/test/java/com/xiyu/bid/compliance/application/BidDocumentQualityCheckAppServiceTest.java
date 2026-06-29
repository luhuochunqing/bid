package com.xiyu.bid.compliance.application;

import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.ai.dto.BidDocumentQualityAiPreviewDTO;
import com.xiyu.bid.compliance.dto.ComplianceCheckResultDTO;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.compliance.repository.ComplianceCheckResultRepository;
import com.xiyu.bid.compliance.service.ComplianceTargetLoader;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BidDocumentQualityCheckAppServiceTest {

    private ComplianceCheckResultRepository resultRepository;
    private ProjectRepository projectRepository;
    private AiProvider aiProvider;
    private BidDocumentQualityCheckAppService appService;

    @BeforeEach
    void setUp() {
        resultRepository = mock(ComplianceCheckResultRepository.class);
        projectRepository = mock(ProjectRepository.class);
        TenderRepository tenderRepository = mock(TenderRepository.class);
        aiProvider = mock(AiProvider.class);
        ComplianceTargetLoader targetLoader = new ComplianceTargetLoader(projectRepository, tenderRepository);
        appService = new BidDocumentQualityCheckAppService(resultRepository, targetLoader, aiProvider);
    }

    @Test
    void checkBidDocumentQuality_aiSuccess_shouldIncludeAiPreview() {
        Project project = Project.builder().id(1L).name("测试项目").tenderId(10L)
                .description("营业执照 资质 业绩 技术方案 报价").build();
        BidDocumentQualityAiPreviewDTO preview = BidDocumentQualityAiPreviewDTO.builder()
                .overallAssessment("标书整体质量良好。")
                .keyRisks(List.of("风险1", "风险2")).build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(aiProvider.previewBidDocumentQuality(anyString(), anyString())).thenReturn(preview);
        when(resultRepository.save(any(ComplianceCheckResult.class))).thenAnswer(invocation -> {
            ComplianceCheckResult r = invocation.getArgument(0);
            r.setId(100L);
            return r;
        });

        ComplianceCheckResultDTO result = appService.checkBidDocumentQuality(1L);

        assertThat(result.getAiOverallAssessment()).isEqualTo("标书整体质量良好。");
        assertThat(result.getAiKeyRisks()).hasSize(2);
        assertThat(result.getIssues()).isNotEmpty();
        verify(aiProvider).previewBidDocumentQuality(anyString(), anyString());
    }

    @Test
    void checkBidDocumentQuality_aiFailure_shouldFallbackGracefully() {
        Project project = Project.builder().id(2L).name("测试项目2").tenderId(20L)
                .description("投标内容").build();
        when(projectRepository.findById(2L)).thenReturn(Optional.of(project));
        when(aiProvider.previewBidDocumentQuality(anyString(), anyString()))
                .thenThrow(new RuntimeException("AI unavailable"));
        when(resultRepository.save(any(ComplianceCheckResult.class))).thenAnswer(invocation -> {
            ComplianceCheckResult r = invocation.getArgument(0);
            r.setId(200L);
            return r;
        });

        ComplianceCheckResultDTO result = appService.checkBidDocumentQuality(2L);

        assertThat(result.getAiOverallAssessment()).isNull();
        assertThat(result.getAiKeyRisks()).isNull();
        assertThat(result.getIssues()).isNotEmpty();
        assertThat(result.getOverallStatus()).isNotNull();
    }
}
