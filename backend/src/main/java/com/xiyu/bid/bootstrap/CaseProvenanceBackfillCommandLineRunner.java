// Input: 显式 preview/apply 模式、绝对审计目录、批次 ID 与完整预演回执
// Output: 可审计预演工件，或经重算校验后的只补不清回填；旧破坏性入口明确拒绝
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
import java.nio.file.Files;
import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class CaseProvenanceBackfillCommandLineRunner implements CommandLineRunner {
    static final String LEGACY_FLAG = "--recompute-case-provenance=true";
    private static final String MODE = "--case-provenance-backfill=";
    private static final String ARTIFACT_DIR = "--case-provenance-artifact-dir=";
    private static final String BATCH_ID = "--case-provenance-batch-id=";
    private static final String CANDIDATE_COUNT = "--case-provenance-candidate-count=";
    private static final String MANIFEST_SHA256 = "--case-provenance-manifest-sha256=";

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
        if (!"preview".equals(mode) && !"apply".equals(mode)) {
            throw new IllegalStateException("unsupported case provenance backfill mode: " + mode);
        }
        String directory = value(args, ARTIFACT_DIR);
        String batchId = value(args, BATCH_ID);
        if (directory == null || batchId == null || !Path.of(directory).isAbsolute()
                || !batchId.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalStateException(mode + " requires an absolute artifact directory and valid batch id");
        }
        if ("apply".equals(mode)) {
            apply(args, Path.of(directory), batchId);
            return;
        }

        CaseProvenanceBackfillPlan plan = backfillAppService.preview();
        CaseProvenanceBackfillArtifactWriter.ArtifactReceipt receipt =
                artifactWriter.write(plan, Path.of(directory), batchId);
        log.info("Case provenance preview finished: batchId={}, scanned={}, fillable={}, manifestSha256={}",
                receipt.batchId(), plan.scannedCount(), receipt.candidateCount(), receipt.manifestSha256());
    }

    private void apply(String[] args, Path artifactRoot, String batchId) {
        int candidateCount = candidateCount(value(args, CANDIDATE_COUNT));
        String digest = value(args, MANIFEST_SHA256);
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("apply requires a lowercase SHA-256 manifest digest");
        }
        Path batchDirectory = artifactRoot.resolve(batchId);
        if (!hasCompleteArtifactSet(batchDirectory)) {
            throw new IllegalStateException("apply requires the complete preview artifact set");
        }

        CaseProvenanceBackfillPlan plan = backfillAppService.preview();
        CaseProvenanceBackfillAppService.ApplyResult result = backfillAppService.apply(plan,
                new CaseProvenanceBackfillAppService.PreviewReceipt(batchId, candidateCount, digest));
        log.info("Case provenance apply finished: batchId={}, candidates={}, updated={}, manifestSha256={}",
                batchId, candidateCount, result.updated(), digest);
    }

    private static int candidateCount(String value) {
        try {
            int count = Integer.parseInt(value);
            if (count < 0) {
                throw new IllegalStateException("candidate count must be non-negative");
            }
            return count;
        } catch (NumberFormatException | NullPointerException exception) {
            throw new IllegalStateException("apply requires a valid candidate count", exception);
        }
    }

    private static boolean hasCompleteArtifactSet(Path batchDirectory) {
        return Files.isRegularFile(batchDirectory.resolve("audit.jsonl"))
                && Files.isRegularFile(batchDirectory.resolve("audit.csv"))
                && Files.isRegularFile(batchDirectory.resolve("rollback.sql"));
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
