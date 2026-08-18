package com.xiyu.bid.compliance.controller;

import com.xiyu.bid.compliance.application.BidDocumentQualityCheckAppService;
import com.xiyu.bid.compliance.application.BidQualityCheckTaskAppService;
import com.xiyu.bid.compliance.dto.QualityCheckReportDTO;
import com.xiyu.bid.compliance.dto.QualityCheckTaskDTO;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.compliance.service.ComplianceCheckService;
import com.xiyu.bid.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceControllerTest {

    @Mock
    private ComplianceCheckService complianceCheckService;

    @Mock
    private BidDocumentQualityCheckAppService bidDocumentQualityCheckAppService;

    @Mock
    private BidQualityCheckTaskAppService qualityCheckTaskAppService;

    private ComplianceController controller;

    @BeforeEach
    void setUp() {
        controller = new ComplianceController(
                complianceCheckService,
                bidDocumentQualityCheckAppService,
                qualityCheckTaskAppService);
    }

    @Test
    @DisplayName("触发标书质量核查端点测试")
    void testTriggerBidQualityCheck() {
        Long projectId = 100L;
        QualityCheckTaskDTO taskDTO = QualityCheckTaskDTO.builder()
                .taskId("task_123456")
                .projectId(projectId)
                .status(ComplianceCheckResult.TaskStatus.INIT)
                .progress(0)
                .build();

        when(qualityCheckTaskAppService.triggerQualityCheck(eq(projectId), eq(false)))
                .thenReturn(taskDTO);

        ResponseEntity<ApiResponse<QualityCheckTaskDTO>> response =
                controller.triggerBidQualityCheck(projectId, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getTaskId()).isEqualTo("task_123456");
        verify(qualityCheckTaskAppService).triggerQualityCheck(eq(projectId), eq(false));
    }

    @Test
    @DisplayName("查询标书质量核查进度端点测试")
    void testGetBidQualityCheckProgress() {
        String taskId = "task_123456";
        QualityCheckTaskDTO taskDTO = QualityCheckTaskDTO.builder()
                .taskId(taskId)
                .status(ComplianceCheckResult.TaskStatus.CHECKING)
                .progress(60)
                .build();

        when(qualityCheckTaskAppService.getTaskProgress(eq(taskId)))
                .thenReturn(taskDTO);

        ResponseEntity<ApiResponse<QualityCheckTaskDTO>> response =
                controller.getBidQualityCheckProgress(taskId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getProgress()).isEqualTo(60);
    }

    @Test
    @DisplayName("查询项目最新质量报告端点测试")
    void testGetBidQualityReport() {
        Long projectId = 100L;
        QualityCheckReportDTO reportDTO = QualityCheckReportDTO.builder()
                .projectId(projectId)
                .taskId("task_123456")
                .passedCount(22)
                .failedCount(2)
                .manualReviewCount(1)
                .build();

        when(qualityCheckTaskAppService.getLatestQualityReport(eq(projectId)))
                .thenReturn(reportDTO);

        ResponseEntity<ApiResponse<QualityCheckReportDTO>> response =
                controller.getBidQualityReport(projectId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getPassedCount()).isEqualTo(22);
    }
}
