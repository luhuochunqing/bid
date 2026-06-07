package com.xiyu.bid.historyproject.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.historyproject.entity.HistoricalProjectSnapshotRecord;
import com.xiyu.bid.historyproject.repository.HistoricalProjectSnapshotRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalProjectSnapshotAppServiceTest {

    @Mock
    private HistoricalProjectSnapshotRecordRepository snapshotRepository;

    private HistoricalProjectSnapshotAppService snapshotAppService;

    @BeforeEach
    void setUp() {
        snapshotAppService = new HistoricalProjectSnapshotAppService(snapshotRepository, new ObjectMapper());
    }

    @Test
    void capture_ShouldPersistSummarySnapshotAndInferredTags() {
        when(snapshotRepository.save(any(HistoricalProjectSnapshotRecord.class)))
                .thenAnswer(invocation -> {
                    HistoricalProjectSnapshotRecord record = invocation.getArgument(0);
                    record.setId(88L);
                    record.setCapturedAt(LocalDateTime.of(2026, 4, 19, 10, 30));
                    return record;
                });

        HistoricalProjectSnapshotCaptureCommand command = new HistoricalProjectSnapshotCaptureCommand(
                12L,
                34L,
                56L,
                "智慧园区综合治理项目",
                "西域数智",
                "智慧园区",
                "覆盖园区安防与智慧运营",
                """
                        {
                          "sections": [
                            {
                              "title": "技术方案",
                              "content": "建设智慧园区平台，覆盖交通、能源和城市治理联动。"
                            },
                            {
                              "title": "实施成果",
                              "content": "形成统一运营中台。"
                            }
                          ]
                        }
                        """
        );

        var snapshot = snapshotAppService.capture(command);

        ArgumentCaptor<HistoricalProjectSnapshotRecord> recordCaptor =
                ArgumentCaptor.forClass(HistoricalProjectSnapshotRecord.class);
        verify(snapshotRepository).save(recordCaptor.capture());

        HistoricalProjectSnapshotRecord savedRecord = recordCaptor.getValue();
        assertThat(savedRecord.getProjectId()).isEqualTo(12L);
        assertThat(savedRecord.getArchiveRecordId()).isEqualTo(34L);
        assertThat(savedRecord.getExportId()).isEqualTo(56L);
        assertThat(savedRecord.getDocumentSnapshotText()).contains("技术方案", "实施成果");
        assertThat(savedRecord.getArchiveSummary())
                .contains("项目资料已完成归档", "西域数智", "已提取正文快照")
                .doesNotContain("智慧园区综合治理项目");
        assertThat(savedRecord.getRecommendedTags()).contains("智慧化", "智慧园区", "交通", "能源");

        assertThat(snapshot.getProjectId()).isEqualTo(12L);
        assertThat(snapshot.getArchiveSummary()).contains("已提取正文快照");
        assertThat(snapshot.getRecommendedTags()).contains("智慧化", "智慧园区", "交通", "能源");
        assertThat(snapshot.getCapturedAt()).isEqualTo(LocalDateTime.of(2026, 4, 19, 10, 30));
    }

    @Test
    void capture_ShouldFallbackToRawContentWhenExportPayloadIsNotJson() {
        when(snapshotRepository.save(any(HistoricalProjectSnapshotRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HistoricalProjectSnapshotCaptureCommand command = new HistoricalProjectSnapshotCaptureCommand(
                99L,
                100L,
                101L,
                "普通项目",
                null,
                null,
                null,
                "原始导出内容 技术方案 A"
        );

        var snapshot = snapshotAppService.capture(command);

        assertThat(snapshot.getDocumentSnapshotText()).contains("原始导出内容 技术方案 A");
        assertThat(snapshot.getRecommendedTags()).isEmpty();
    }

    @Test
    void getLatestSnapshot_ShouldThrowWhenProjectHasNoSnapshot() {
        when(snapshotRepository.findTopByProjectIdOrderByCapturedAtDesc(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> snapshotAppService.getLatestSnapshot(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("HistoricalProjectSnapshot");
    }
}
