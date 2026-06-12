package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.CaseExportContext;
import com.xiyu.bid.casework.domain.model.CaseExportZipEntry;
import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class CaseExportPolicy {

    private static final int MAX_EXPORT_BATCH_SIZE = 500;
    private static final DateTimeFormatter ZIP_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public record ExportValidationResult(boolean valid, String errorMessage) {
        public static ExportValidationResult success() {
            return new ExportValidationResult(true, null);
        }

        public static ExportValidationResult failure(String message) {
            return new ExportValidationResult(false, message);
        }
    }

    public ExportValidationResult validateExportRequest(List<? extends KnowledgeCaseReadModel> cases) {
        if (cases == null || cases.isEmpty()) {
            return ExportValidationResult.failure("没有可导出的案例");
        }
        if (cases.size() > MAX_EXPORT_BATCH_SIZE) {
            return ExportValidationResult.failure(
                    String.format("导出案例数量超过限制（最多 %d 条，当前 %d 条）",
                            MAX_EXPORT_BATCH_SIZE, cases.size()));
        }
        return ExportValidationResult.success();
    }

    public CaseExportContext buildExportContext(List<? extends KnowledgeCaseReadModel> cases, String operatorName) {
        List<CaseExportZipEntry> entries = buildZipEntries(cases);
        String zipFileName = generateZipFileName();

        return new CaseExportContext(
                cases,
                entries,
                zipFileName,
                operatorName,
                LocalDateTime.now()
        );
    }

    public List<CaseExportZipEntry> buildZipEntries(List<? extends KnowledgeCaseReadModel> cases) {
        List<CaseExportZipEntry> entries = new ArrayList<>();

        for (KnowledgeCaseReadModel kc : cases) {
            String projectFolder = safeFileName(kc.getSourceProjectName());
            String scoringTitleFolder = safeFileName(kc.getScoringPointTitle());

            String txtEntryPath = String.format("%s/%s/应答全文.txt", projectFolder, scoringTitleFolder);
            String txtContent = buildResponseTextContent(kc);

            entries.add(new CaseExportZipEntry(txtEntryPath, txtContent.getBytes(), txtContent.length()));

            String indexEntryPath = buildIndexEntryPath(kc);
            String indexContent = buildIndexContent(kc);
            entries.add(new CaseExportZipEntry(indexEntryPath, indexContent.getBytes(), indexContent.length()));
        }

        return entries;
    }

    public String generateZipFileName() {
        String timestamp = LocalDateTime.now().format(ZIP_NAME_FORMATTER);
        return String.format("方案管理-案例库文件包-%s.zip", timestamp);
    }

    public String buildResponseTextContent(KnowledgeCaseReadModel kc) {
        StringBuilder sb = new StringBuilder();
        sb.append("【案例库案例应答全文】\n");
        sb.append("═".repeat(50)).append("\n\n");
        sb.append("来源项目：").append(nullSafe(kc.getSourceProjectName())).append("\n");
        sb.append("评分项标题：").append(nullSafe(kc.getScoringPointTitle())).append("\n");
        sb.append("评分类别：").append(nullSafe(kc.getScoringCategory())).append("\n");
        sb.append("客户类型：").append(nullSafe(kc.getCustomerType())).append("\n");
        sb.append("项目类型：").append(nullSafe(kc.getProjectType())).append("\n");
        sb.append("中标结果：").append(nullSafe(kc.getBidResult())).append("\n");
        sb.append("产品线：").append(nullSafe(kc.getProductLine())).append("\n");
        sb.append("创建时间：").append(formatDateTime(kc.getCreatedAt())).append("\n");
        sb.append("\n");
        sb.append("─".repeat(50)).append("\n");
        sb.append("【需求原文】\n");
        sb.append(nullSafe(kc.getRequirementRaw())).append("\n\n");
        sb.append("─".repeat(50)).append("\n");
        sb.append("【应答全文】\n");
        sb.append(nullSafe(kc.getResponseText())).append("\n");
        return sb.toString();
    }

    public String buildIndexEntryPath(KnowledgeCaseReadModel kc) {
        String projectFolder = safeFileName(kc.getSourceProjectName());
        String scoringTitleFolder = safeFileName(kc.getScoringPointTitle());
        return String.format("%s/%s/案例索引信息.txt", projectFolder, scoringTitleFolder);
    }

    public String buildIndexContent(KnowledgeCaseReadModel kc) {
        StringBuilder sb = new StringBuilder();
        sb.append("【案例索引信息】\n");
        sb.append("═".repeat(50)).append("\n\n");
        sb.append("案例ID：").append(kc.getId()).append("\n");
        sb.append("来源项目ID：").append(kc.getSourceProjectId()).append("\n");
        sb.append("来源项目名称：").append(nullSafe(kc.getSourceProjectName())).append("\n");
        sb.append("评分项标题：").append(nullSafe(kc.getScoringPointTitle())).append("\n");
        sb.append("评分类别：").append(nullSafe(kc.getScoringCategory())).append("\n");
        sb.append("客户类型：").append(nullSafe(kc.getCustomerType())).append("\n");
        sb.append("项目类型：").append(nullSafe(kc.getProjectType())).append("\n");
        sb.append("中标结果：").append(nullSafe(kc.getBidResult())).append("\n");
        sb.append("产品线：").append(nullSafe(kc.getProductLine())).append("\n");
        sb.append("复用次数：").append(kc.getReuseCount()).append("\n");
        sb.append("状态：").append(nullSafe(kc.getStatus())).append("\n");
        sb.append("是否置顶：").append(Boolean.TRUE.equals(kc.getIsPinned()) ? "是" : "否").append("\n");
        sb.append("创建时间：").append(formatDateTime(kc.getCreatedAt())).append("\n");
        return sb.toString();
    }

    public List<KnowledgeCaseReadModel> sortCasesForExport(List<? extends KnowledgeCaseReadModel> cases) {
        return cases.stream()
                .sorted(Comparator
                        .comparing(KnowledgeCaseReadModel::getIsPinned, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(KnowledgeCaseReadModel::getSourceProjectName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(KnowledgeCaseReadModel::getScoringPointTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(KnowledgeCaseReadModel::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public long calculateTotalExportSize(List<CaseExportZipEntry> entries) {
        return entries.stream()
                .mapToLong(CaseExportZipEntry::contentLength)
                .sum();
    }

    private String safeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "未知项目";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String nullSafe(String value) {
        return value != null ? value : "-";
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "-";
        }
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
