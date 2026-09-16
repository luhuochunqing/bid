// Input: 历史补发部署配置、业务日期与补发服务
// Output: 启动强制 preview、09:00 execute 及 fail-closed 配置门禁验证
// Pos: backend/src/test/java/com/xiyu/bid/workbench/job/ - 一次性历史补发调度单测
package com.xiyu.bid.workbench.job;

import com.xiyu.bid.config.BizTimezoneProperties;
import com.xiyu.bid.workbench.service.OpportunityPredictionHistoryReplayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("商机预测历史补发调度")
class OpportunityPredictionHistoryReplayTaskTest {

    private static final LocalDate RUN_DATE = LocalDate.of(2026, 9, 16);

    @Mock private OpportunityPredictionHistoryReplayService replayService;
    @Mock private BizTimezoneProperties bizTimezone;

    private OpportunityPredictionHistoryReplayProperties properties;
    private OpportunityPredictionHistoryReplayTask task;

    @BeforeEach
    void setUp() {
        properties = new OpportunityPredictionHistoryReplayProperties();
        task = new OpportunityPredictionHistoryReplayTask(replayService, properties, bizTimezone);
        lenient().when(replayService.run(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(new OpportunityPredictionHistoryReplayService.ReplayOutcome(
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    @DisplayName("默认关闭时启动与调度都不调用补发服务")
    void disabledByDefaultDoesNothing() {
        task.previewOnStartup();
        task.runScheduled();

        verify(replayService, never()).run(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("即使配置 execute，应用启动也只能强制 preview")
    void startupAlwaysForcesPreview() {
        enableValidExecute();

        task.previewOnStartup();

        verify(replayService).run(RUN_DATE, RUN_DATE,
                OpportunityPredictionHistoryReplayService.ReplayMode.PREVIEW);
    }

    @Test
    @DisplayName("执行日 09:00 调度且模式为 execute 时才执行写入")
    void scheduledRunExecutesOnConfiguredDate() {
        enableValidExecute();
        when(bizTimezone.today()).thenReturn(RUN_DATE);

        task.runScheduled();

        verify(replayService).run(RUN_DATE, RUN_DATE,
                OpportunityPredictionHistoryReplayService.ReplayMode.EXECUTE);
    }

    @Test
    @DisplayName("非执行日调度不调用补发服务，避免错过后追补")
    void scheduledRunSkipsDifferentDate() {
        enableValidExecute();
        when(bizTimezone.today()).thenReturn(RUN_DATE.plusDays(1));

        task.runScheduled();

        verify(replayService, never()).run(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("截止日晚于执行日属于无效配置，启动和调度均失败关闭")
    void invalidWindowNeverRuns() {
        properties.setEnabled(true);
        properties.setMode(OpportunityPredictionHistoryReplayService.ReplayMode.EXECUTE);
        properties.setRunDate(RUN_DATE);
        properties.setCutoff(RUN_DATE.plusDays(1));
        task.previewOnStartup();
        task.runScheduled();

        verify(replayService, never()).run(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("执行日配置 preview 时 09:00 仍保持零写模式")
    void scheduledPreviewStaysPreview() {
        properties.setEnabled(true);
        properties.setMode(OpportunityPredictionHistoryReplayService.ReplayMode.PREVIEW);
        properties.setRunDate(RUN_DATE);
        properties.setCutoff(RUN_DATE);
        when(bizTimezone.today()).thenReturn(RUN_DATE);

        task.runScheduled();

        verify(replayService).run(RUN_DATE, RUN_DATE,
                OpportunityPredictionHistoryReplayService.ReplayMode.PREVIEW);
    }

    private void enableValidExecute() {
        properties.setEnabled(true);
        properties.setMode(OpportunityPredictionHistoryReplayService.ReplayMode.EXECUTE);
        properties.setRunDate(RUN_DATE);
        properties.setCutoff(RUN_DATE);
    }
}
