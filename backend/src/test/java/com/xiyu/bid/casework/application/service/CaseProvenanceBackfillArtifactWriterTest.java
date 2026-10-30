package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort.Candidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CaseProvenanceBackfillArtifactWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesDeterministicRedactedAuditAndGuardedRollback() throws Exception {
        String sensitive = "敏感正文".repeat(100);
        Candidate candidate = new Candidate(9L, 100L, sensitive, "案例", null, null, null, null, null);
        var proposed = new CaseProvenanceBackfillPlan.Proposed(
                "1. 成功案例", "第一章 > 1. 成功案例", 2, "投标文件.pdf", 12);
        var decision = new CaseProvenanceBackfillPlan.Decision(candidate,
                CaseProvenanceBackfillPlan.DecisionType.FILLABLE, "unique source section verified",
                proposed, "abc123");
        var plan = new CaseProvenanceBackfillPlan(List.of(decision));

        var receipt = new CaseProvenanceBackfillArtifactWriter().write(plan, tempDir, "batch-20261030");

        String jsonl = Files.readString(receipt.jsonlPath());
        String csv = Files.readString(receipt.csvPath());
        String rollback = Files.readString(receipt.rollbackSqlPath());
        assertThat(jsonl).contains("\"caseId\":9", "FILLABLE", "abc123").doesNotContain(sensitive);
        assertThat(csv).contains("9,100,FILLABLE").doesNotContain(sensitive);
        assertThat(rollback)
                .contains("UPDATE knowledge_case")
                .contains("SET source_section_title = NULL")
                .contains("WHERE id = 9 AND status = 'ACTIVE'")
                .contains("source_section_title = '1. 成功案例'")
                .contains("source_section_path = '第一章 > 1. 成功案例'");
        assertThat(receipt.candidateCount()).isEqualTo(1);
        assertThat(receipt.manifestSha256()).hasSize(64);

        var second = new CaseProvenanceBackfillArtifactWriter()
                .write(plan, tempDir.resolve("second"), "batch-20261030");
        assertThat(second.manifestSha256()).isEqualTo(receipt.manifestSha256());
    }
}
