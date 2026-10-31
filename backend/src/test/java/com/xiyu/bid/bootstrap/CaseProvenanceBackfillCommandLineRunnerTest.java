// Input: 安全 preview 参数或已废弃的破坏性重算参数
// Output: 无参数零调用；旧入口拒绝；preview 仅生成审计工件
// Pos: Backend/bootstrap 测试——生产安全来源回填 CLI 门禁
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.bootstrap;

import com.xiyu.bid.casework.application.service.CaseProvenanceBackfillAppService;
import com.xiyu.bid.casework.application.service.CaseProvenanceBackfillArtifactWriter;
import com.xiyu.bid.casework.application.service.CaseProvenanceBackfillPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaseProvenanceBackfillCommandLineRunnerTest {
    @TempDir
    Path tempDir;

    private final CaseProvenanceBackfillAppService service = mock(CaseProvenanceBackfillAppService.class);
    private final CaseProvenanceBackfillArtifactWriter writer = mock(CaseProvenanceBackfillArtifactWriter.class);
    private final CaseProvenanceBackfillCommandLineRunner runner =
            new CaseProvenanceBackfillCommandLineRunner(service, writer);

    @Test
    void ordinaryStartupDoesNothing() {
        runner.run("--server.port=18089");
        verify(service, never()).preview();
        verify(service, never()).recomputeAll();
    }

    @Test
    void rejectsLegacyDestructiveEntryPoint() {
        assertThatThrownBy(() -> runner.run("--recompute-case-provenance=true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled")
                .hasMessageContaining("preview");
        verify(service, never()).recomputeAll();
    }

    @Test
    void previewCreatesArtifactsWithoutCallingAnyWritePath() {
        CaseProvenanceBackfillPlan plan = new CaseProvenanceBackfillPlan(List.of());
        when(service.preview()).thenReturn(plan);
        when(writer.write(plan, Path.of("/tmp/provenance-audit"), "prod-preview-20261030"))
                .thenReturn(new CaseProvenanceBackfillArtifactWriter.ArtifactReceipt(
                        "prod-preview-20261030", 0, "a".repeat(64),
                        Path.of("/tmp/a"), Path.of("/tmp/b"), Path.of("/tmp/c")));

        runner.run("--case-provenance-backfill=preview",
                "--case-provenance-artifact-dir=/tmp/provenance-audit",
                "--case-provenance-batch-id=prod-preview-20261030");

        verify(service).preview();
        verify(writer).write(plan, Path.of("/tmp/provenance-audit"), "prod-preview-20261030");
        verify(service, never()).recomputeAll();
    }

    @Test
    void previewRequiresAbsoluteArtifactDirectoryAndBatchId() {
        assertThatThrownBy(() -> runner.run("--case-provenance-backfill=preview",
                "--case-provenance-artifact-dir=relative"))
                .isInstanceOf(IllegalStateException.class);
        verify(service, never()).preview();
    }

    @Test
    void applyRecomputesPlanAndUsesCompletePreviewReceipt() throws Exception {
        String batchId = "batch-apply-1";
        Path batchDirectory = Files.createDirectory(tempDir.resolve(batchId));
        Files.writeString(batchDirectory.resolve("audit.jsonl"), "{}\n");
        Files.writeString(batchDirectory.resolve("audit.csv"), "header\n");
        Files.writeString(batchDirectory.resolve("rollback.sql"), "ROLLBACK;\n");
        CaseProvenanceBackfillPlan plan = new CaseProvenanceBackfillPlan(List.of());
        when(service.preview()).thenReturn(plan);
        when(service.apply(plan, new CaseProvenanceBackfillAppService.PreviewReceipt(
                batchId, 0, "a".repeat(64))))
                .thenReturn(new CaseProvenanceBackfillAppService.ApplyResult(0));

        runner.run("--case-provenance-backfill=apply",
                "--case-provenance-artifact-dir=" + tempDir,
                "--case-provenance-batch-id=" + batchId,
                "--case-provenance-candidate-count=0",
                "--case-provenance-manifest-sha256=" + "a".repeat(64));

        verify(service).preview();
        verify(service).apply(plan, new CaseProvenanceBackfillAppService.PreviewReceipt(
                batchId, 0, "a".repeat(64)));
        verify(writer, never()).write(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void applyRejectsIncompleteOrInvalidReceiptBeforeScanning() throws Exception {
        String batchId = "batch-apply-1";
        Path batchDirectory = Files.createDirectory(tempDir.resolve(batchId));
        Files.writeString(batchDirectory.resolve("audit.jsonl"), "{}\n");
        Files.writeString(batchDirectory.resolve("audit.csv"), "header\n");
        Files.writeString(batchDirectory.resolve("rollback.sql"), "ROLLBACK;\n");

        assertThatThrownBy(() -> runner.run("--case-provenance-backfill=apply",
                "--case-provenance-artifact-dir=" + tempDir,
                "--case-provenance-batch-id=" + batchId,
                "--case-provenance-candidate-count=not-a-number",
                "--case-provenance-manifest-sha256=" + "a".repeat(64)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> runner.run("--case-provenance-backfill=apply",
                "--case-provenance-artifact-dir=" + tempDir,
                "--case-provenance-batch-id=" + batchId,
                "--case-provenance-candidate-count=0",
                "--case-provenance-manifest-sha256=bad"))
                .isInstanceOf(IllegalStateException.class);
        verify(service, never()).preview();
        verify(service, never()).apply(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
