package com.xiyu.bid.resources.task;

import com.xiyu.bid.resources.application.service.ScanDepositReturnTrackingAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepositReturnScanTaskTest {

    @Mock
    private ScanDepositReturnTrackingAppService scanDepositReturnTrackingAppService;

    @InjectMocks
    private DepositReturnScanTask task;

    @Test
    @DisplayName("正常执行时应调用 scan 并记录提醒数")
    void shouldCallScanOnNormalExecution() {
        when(scanDepositReturnTrackingAppService.scan()).thenReturn(3);

        task.scanDepositReturn();

        verify(scanDepositReturnTrackingAppService).scan();
    }

    @Test
    @DisplayName("scan 抛出异常时不应向外传播，防止定时任务崩溃")
    void shouldSwallowExceptionToProtectScheduler() {
        when(scanDepositReturnTrackingAppService.scan())
                .thenThrow(new RuntimeException("DB connection lost"));

        // 不应抛出异常——定时任务必须吞掉异常，否则会阻塞后续调度
        task.scanDepositReturn();

        verify(scanDepositReturnTrackingAppService).scan();
    }
}
