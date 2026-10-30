// Input: 生产安全回填计划、绝对输出目录与批次 ID
// Output: 脱敏 JSONL/CSV、守卫式回滚 SQL 与清单 SHA-256 回执
// Pos: casework 应用层——preview 审计工件的唯一生成器
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.casework.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class CaseProvenanceBackfillArtifactWriter {
    private static final int EVIDENCE_PREVIEW_LIMIT = 80;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record ArtifactReceipt(String batchId, int candidateCount, String manifestSha256,
                                  Path jsonlPath, Path csvPath, Path rollbackSqlPath) { }

    public ArtifactReceipt write(CaseProvenanceBackfillPlan plan, Path outputRoot, String batchId) {
        requireSafeInputs(outputRoot, batchId);
        Path batchDirectory = outputRoot.resolve(batchId);
        try {
            Files.createDirectories(outputRoot);
            Files.createDirectory(batchDirectory);
            Path jsonl = batchDirectory.resolve("audit.jsonl");
            Path csv = batchDirectory.resolve("audit.csv");
            Path rollback = batchDirectory.resolve("rollback.sql");
            Files.writeString(jsonl, jsonl(plan), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            Files.writeString(csv, csv(plan), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            Files.writeString(rollback, rollback(plan, batchId), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            int fillable = Math.toIntExact(plan.count(CaseProvenanceBackfillPlan.DecisionType.FILLABLE));
            return new ArtifactReceipt(batchId, fillable, manifestSha256(plan), jsonl, csv, rollback);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot create provenance backfill artifacts", exception);
        }
    }

    public String manifestSha256(CaseProvenanceBackfillPlan plan) {
        return sha256(canonicalManifest(plan));
    }

    private String jsonl(CaseProvenanceBackfillPlan plan) {
        List<String> lines = new ArrayList<>();
        for (CaseProvenanceBackfillPlan.Decision decision : plan.decisions()) {
            try {
                lines.add(objectMapper.writeValueAsString(auditRow(decision)));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("cannot serialize provenance audit row", exception);
            }
        }
        return String.join("\n", lines) + (lines.isEmpty() ? "" : "\n");
    }

    private static String csv(CaseProvenanceBackfillPlan plan) {
        StringBuilder result = new StringBuilder(
                "caseId,projectId,decision,reason,title,path,document,page,evidenceSha256,evidencePreview\n");
        for (CaseProvenanceBackfillPlan.Decision decision : plan.decisions()) {
            Map<String, Object> row = auditRow(decision);
            result.append(decision.caseId()).append(',')
                    .append(value(decision.candidate().sourceProjectId())).append(',')
                    .append(decision.type()).append(',')
                    .append(csvValue(decision.reason())).append(',')
                    .append(csvValue(row.get("title"))).append(',')
                    .append(csvValue(row.get("path"))).append(',')
                    .append(csvValue(row.get("documentName"))).append(',')
                    .append(value(row.get("pageNumber"))).append(',')
                    .append(decision.evidenceSha256()).append(',')
                    .append(csvValue(row.get("evidencePreview"))).append('\n');
        }
        return result.toString();
    }

    private static Map<String, Object> auditRow(CaseProvenanceBackfillPlan.Decision decision) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("caseId", decision.caseId());
        row.put("projectId", decision.candidate().sourceProjectId());
        row.put("decision", decision.type().name());
        row.put("reason", decision.reason());
        if (decision.proposed() != null) {
            row.put("title", decision.proposed().title());
            row.put("path", decision.proposed().path());
            row.put("documentName", decision.proposed().documentName());
            row.put("pageNumber", decision.proposed().pageNumber());
        }
        row.put("evidenceSha256", decision.evidenceSha256());
        row.put("evidencePreview", preview(decision.candidate().responseText()));
        return row;
    }

    private static String canonicalManifest(CaseProvenanceBackfillPlan plan) {
        StringBuilder value = new StringBuilder();
        for (CaseProvenanceBackfillPlan.Decision decision : plan.decisions()) {
            CaseProvenanceBackfillPlan.Proposed proposed = decision.proposed();
            value.append(decision.caseId()).append('|').append(decision.type()).append('|')
                    .append(decision.evidenceSha256()).append('|');
            if (proposed != null) {
                value.append(proposed.title()).append('|').append(proposed.path()).append('|')
                        .append(proposed.index()).append('|').append(proposed.documentName()).append('|')
                        .append(proposed.pageNumber());
            }
            value.append('\n');
        }
        return value.toString();
    }

    private static String rollback(CaseProvenanceBackfillPlan plan, String batchId) {
        StringBuilder sql = new StringBuilder("-- batch: ").append(batchId)
                .append("\nSTART TRANSACTION;\n");
        for (CaseProvenanceBackfillPlan.Decision decision : plan.decisions()) {
            if (decision.type() != CaseProvenanceBackfillPlan.DecisionType.FILLABLE) {
                continue;
            }
            CaseProvenanceBackfillPlan.Proposed p = decision.proposed();
            sql.append("UPDATE knowledge_case SET source_section_title = NULL, ")
                    .append("source_section_path = NULL, source_section_index = NULL, ")
                    .append("source_document_name = NULL, source_page_number = NULL ")
                    .append("WHERE id = ").append(decision.caseId()).append(" AND status = 'ACTIVE' ")
                    .append("AND source_section_title = '").append(sqlLiteral(p.title())).append("' ")
                    .append("AND source_section_path = '").append(sqlLiteral(p.path())).append("' ")
                    .append("AND source_section_index <=> ").append(sqlNumber(p.index())).append(' ')
                    .append("AND source_document_name <=> ").append(sqlString(p.documentName())).append(' ')
                    .append("AND source_page_number <=> ").append(sqlNumber(p.pageNumber())).append(";\n");
        }
        return sql.append("-- Verify affected row counts before replacing ROLLBACK with COMMIT.\nROLLBACK;\n")
                .toString();
    }

    private static void requireSafeInputs(Path outputRoot, String batchId) {
        if (outputRoot == null || !outputRoot.isAbsolute()) {
            throw new IllegalArgumentException("artifact output root must be absolute");
        }
        if (batchId == null || !batchId.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("invalid batch id");
        }
    }

    private static String preview(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.substring(0, Math.min(EVIDENCE_PREVIEW_LIMIT, normalized.length()));
    }

    private static String csvValue(Object value) {
        String text = value(value);
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String sqlLiteral(String value) {
        return value.replace("'", "''");
    }

    private static String sqlString(String value) {
        return value == null ? "NULL" : "'" + sqlLiteral(value) + "'";
    }

    private static String sqlNumber(Number value) {
        return value == null ? "NULL" : value.toString();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
