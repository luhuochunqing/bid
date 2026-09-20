// Input: 启动参数数组 + CaseProvenanceBackfillAppService
// Output: 断言旗标关闭时零调用、显式旗标时触发一次重算
// Pos: Backend/bootstrap 测试——存量案例来源章节回填 CLI 的旗标门控
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.bootstrap;

import com.xiyu.bid.casework.application.service.CaseProvenanceBackfillAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 存量案例来源章节回填 CLI 的旗标门控测试。
 *
 * <p>回填会重新下载标书并改写存量案例来源字段，必须确认它只在显式传入
 * {@code --recompute-case-provenance=true} 时执行，任何常规启动都不得触发。</p>
 */
class CaseProvenanceBackfillCommandLineRunnerTest {

    private final CaseProvenanceBackfillAppService backfillAppService =
            mock(CaseProvenanceBackfillAppService.class);
    private final CaseProvenanceBackfillCommandLineRunner runner =
            new CaseProvenanceBackfillCommandLineRunner(backfillAppService);

    @Test
    @DisplayName("常规启动（无旗标）不触发回填")
    void doesNothingWithoutFlag() {
        runner.run();
        runner.run("--server.port=18089", "--spring.profiles.active=dev", "--import-case-slices=true");

        verify(backfillAppService, never()).recomputeAll();
    }

    @Test
    @DisplayName("旗标值不是 true 时不触发（严格字面量匹配）")
    void ignoresOtherFlagValues() {
        runner.run("--recompute-case-provenance=false");
        runner.run("--recompute-case-provenance");

        verify(backfillAppService, never()).recomputeAll();
    }

    @Test
    @DisplayName("显式旗标触发一次重算")
    void runsOnceWithFlag() {
        when(backfillAppService.recomputeAll()).thenReturn(
                new CaseProvenanceBackfillAppService.BackfillResult(1, 2, 2, 0, 0, 0));

        runner.run("--recompute-case-provenance=true");

        verify(backfillAppService).recomputeAll();
    }
}
