// Input: 启动参数 --recompute-case-provenance=true
// Output: 存量知识案例来源章节重算的执行日志与统计
// Pos: Backend/bootstrap 包——运维 CLI 入口（历史案例来源章节回填）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.bootstrap;

import com.xiyu.bid.casework.application.service.CaseProvenanceBackfillAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * CLI entry point for recomputing historical knowledge case provenance.
 *
 * <p>Activated only when the program argument {@code --recompute-case-provenance=true}
 * is present. Run it deliberately against one environment at a time; the recompute is
 * idempotent, so a repeated run makes no further writes.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseProvenanceBackfillCommandLineRunner implements CommandLineRunner {

    static final String RECOMPUTE_FLAG = "--recompute-case-provenance=true";

    private final CaseProvenanceBackfillAppService backfillAppService;

    @Override
    public void run(String... args) {
        if (!isRecomputeEnabled(args)) {
            return;
        }
        log.info("Starting case provenance backfill");
        CaseProvenanceBackfillAppService.BackfillResult result = backfillAppService.recomputeAll();
        log.info("Case provenance backfill finished: projectsScanned={}, casesScanned={}, casesUpdated={}, "
                        + "casesCleared={}, casesUnchanged={}, projectsFailed={}",
                result.projectsScanned(), result.casesScanned(), result.casesUpdated(),
                result.casesCleared(), result.casesUnchanged(), result.projectsFailed());
    }

    private static boolean isRecomputeEnabled(String[] args) {
        for (String arg : args) {
            if (RECOMPUTE_FLAG.equals(arg)) {
                return true;
            }
        }
        return false;
    }
}
