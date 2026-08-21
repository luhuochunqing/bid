package com.xiyu.bid.compliance.application;

import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.compliance.repository.ComplianceCheckResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidQualityCheckProgressUpdaterTest {

    @Mock
    private ComplianceCheckResultRepository resultRepository;

    private BidQualityCheckProgressUpdater progressUpdater;

    @BeforeEach
    void setUp() {
        progressUpdater = new BidQualityCheckProgressUpdater(resultRepository);
    }

    @Test
    @DisplayName("updateProgress 正确更新状态和进度")
    void testUpdateProgress() {
        Long resultId = 1L;
        ComplianceCheckResult entity = ComplianceCheckResult.builder().id(resultId).build();
        when(resultRepository.findById(resultId)).thenReturn(Optional.of(entity));

        progressUpdater.updateProgress(resultId, ComplianceCheckResult.TaskStatus.PARSING, 20, "正在解析");

        ArgumentCaptor<ComplianceCheckResult> captor = ArgumentCaptor.forClass(ComplianceCheckResult.class);
        verify(resultRepository).save(captor.capture());
        ComplianceCheckResult saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ComplianceCheckResult.TaskStatus.PARSING);
        assertThat(saved.getProgress()).isEqualTo(20);
        assertThat(saved.getCurrentStep()).isEqualTo("正在解析");
    }

    @Test
    @DisplayName("completeTask 正确更新完成指标与状态")
    void testCompleteTask() {
        Long resultId = 1L;
        ComplianceCheckResult entity = ComplianceCheckResult.builder().id(resultId).build();
        when(resultRepository.findById(resultId)).thenReturn(Optional.of(entity));

        progressUpdater.completeTask(resultId, ComplianceCheckResult.Status.COMPLIANT, 0, "[]", 25, 0, 0, 25);

        ArgumentCaptor<ComplianceCheckResult> captor = ArgumentCaptor.forClass(ComplianceCheckResult.class);
        verify(resultRepository).save(captor.capture());
        ComplianceCheckResult saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ComplianceCheckResult.TaskStatus.COMPLETED);
        assertThat(saved.getProgress()).isEqualTo(100);
        assertThat(saved.getPassedCount()).isEqualTo(25);
        assertThat(saved.getFailedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("markFailed 正确标记失败并记录错误信息")
    void testMarkFailed() {
        Long resultId = 1L;
        ComplianceCheckResult entity = ComplianceCheckResult.builder().id(resultId).build();
        when(resultRepository.findById(resultId)).thenReturn(Optional.of(entity));

        progressUpdater.markFailed(resultId, "文件解析异常");

        ArgumentCaptor<ComplianceCheckResult> captor = ArgumentCaptor.forClass(ComplianceCheckResult.class);
        verify(resultRepository).save(captor.capture());
        ComplianceCheckResult saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ComplianceCheckResult.TaskStatus.FAILED);
        assertThat(saved.getProgress()).isEqualTo(100);
        assertThat(saved.getErrorMessage()).isEqualTo("文件解析异常");
    }
}
