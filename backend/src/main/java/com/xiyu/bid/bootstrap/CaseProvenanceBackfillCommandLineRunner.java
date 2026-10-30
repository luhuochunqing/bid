// Input: 显式 preview 模式、绝对审计目录与批次 ID
// Output: 只读预演的分类统计与可审计工件；旧破坏性入口明确拒绝
// Pos: Backend/bootstrap 包——生产安全案例来源回填 CLI 入口
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.bootstrap;

import com.xiyu.bid.casework.application.service.CaseProvenanceBackfillAppService;
import com.xiyu.bid.casework.application.service.CaseProvenanceBackfillArtifactWriter;
import com.xiyu.bid.casework.application.service.CaseProvenanceBackfillPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class CaseProvenanceBackfillCommandLineRunner implements CommandLineRunner {
    static final String LEGACY_FLAG = "--recompute-case-provenance=true";
    private static final String MODE = "--case-provenance-backfill=";
    private static final String ARTIFACT_DIR = "--case-provenance-artifact-dir=";
    private static final String BATCH_ID = "--case-provenance-batch-id=";

    private final CaseProvenanceBackfillAppService backfillAppService;
    private final CaseProvenanceBackfillArtifactWriter artifactWriter;

    @Override
    public void run(String... args) {
        if (Arrays.asList(args).contains(LEGACY_FLAG)) {
            throw new IllegalStateException("legacy destructive provenance recompute is disabled; use preview mode");
        }
        String mode = value(args, MODE);
        if (mode == null) {
            return;
        }
        if (!"preview".equals(mode)) {
            throw new IllegalStateException("unsupported case provenance backfill mode: " + mode);
        }
        String directory = value(args, ARTIFACT_DIR);
        String batchId = value(args, BATCH_ID);
        if (directory == null || batchId == null || !Path.of(directory).isAbsolute()) {
            throw new IllegalStateException("preview requires an absolute artifact directory and explicit batch id");
        }

        CaseProvenanceBackfillPlan plan = backfillAppService.preview();
        CaseProvenanceBackfillArtifactWriter.ArtifactReceipt receipt =
                artifactWriter.write(plan, Path.of(directory), batchId);
        log.info("Case provenance preview finished: batchId={}, scanned={}, fillable={}, manifestSha256={}",
                receipt.batchId(), plan.scannedCount(), receipt.candidateCount(), receipt.manifestSha256());
    }

    private static String value(String[] args, String prefix) {
        return Arrays.stream(args)
                .filter(arg -> arg.startsWith(prefix))
                .map(arg -> arg.substring(prefix.length()))
                .findFirst()
                .filter(value -> !value.isBlank())
                .orElse(null);
    }
}
