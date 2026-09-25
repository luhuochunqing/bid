// Input: Sidecar DocumentArtifact JSON / DOM
// Output: 不可变 DocumentArtifact 值对象
// Pos: scoreparse/domain/grid — 纯核心（spec 059 Phase 1）
package com.xiyu.bid.scoreparse.domain.grid;

import java.util.List;
import java.util.Optional;

/**
 * Sidecar DocumentArtifact v2 响应（spec 059 Phase 1）。
 * <p>本模型作为 CanonicalTableGridFactory 的输入。整体契约：
 * <ul>
 *   <li>{@code schemaVersion} 必须为受支持的当前版本或兼容的历史版本</li>
 *   <li>{@code artifact.sha256} 必须与落库任务输入文件的 SHA-256 一致（Phase 4 校验）</li>
 *   <li>{@code tables[*]} 数组中的每个表都必须能通过 {@link CanonicalTableGridFactory}</li>
 * </ul>
 * <p>本类只承载数据；JSON 反序列化交给 Jackson，由工厂层调用。
 *
 * <h3>不变量的处理</h3>
 * <p>FP-Java 原则：domain 层不抛业务异常。本 record 的硬不变量通过 {@link ArtifactMeta#validate()}
 * 和 {@link CanonicalTable#validate()} 返回 {@code Optional<String>} 描述违规原因；
 * 调用方收集到 {@code StructureValidationResult.violations}。
 */
public record DocumentArtifact(
        ArtifactMeta artifact,
        List<CanonicalTable> tables,
        List<String> warnings,
        List<TextBlockEvidence> textBlocks,
        String schemaVersion
) {

    public static final String SUPPORTED_SCHEMA_VERSION = "2.2";
    public static final String COMPATIBLE_SCHEMA_VERSION = "2.1";
    public static final String LEGACY_SCHEMA_VERSION = "2.0";

    public DocumentArtifact(ArtifactMeta artifact, List<CanonicalTable> tables, List<String> warnings) {
        this(artifact, tables, warnings, List.of(), SUPPORTED_SCHEMA_VERSION);
    }

    public DocumentArtifact(ArtifactMeta artifact, List<CanonicalTable> tables, List<String> warnings,
                            String schemaVersion) {
        this(artifact, tables, warnings, List.of(), schemaVersion);
    }

    public DocumentArtifact {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        tables = tables == null ? List.of() : List.copyOf(tables);
        textBlocks = textBlocks == null ? List.of() : List.copyOf(textBlocks);
        schemaVersion = schemaVersion == null ? "" : schemaVersion;
    }

    public boolean hasSupportedSchemaVersion() {
        return SUPPORTED_SCHEMA_VERSION.equals(schemaVersion)
                || COMPATIBLE_SCHEMA_VERSION.equals(schemaVersion)
                || LEGACY_SCHEMA_VERSION.equals(schemaVersion);
    }

    /** Exact OCR text with physical PDF page and pixel-region provenance. */
    public record TextBlockEvidence(String blockId, Integer sourcePage, String sourceSection,
                                    String text, Double confidence, BoundingBox bounds) {
        public TextBlockEvidence {
            blockId = blockId == null ? "" : blockId;
            sourceSection = sourceSection == null ? "" : sourceSection;
            text = text == null ? "" : text;
        }

        public boolean isVerifiable() {
            return sourcePage != null && !blockId.isBlank() && !sourceSection.isBlank() && !text.isBlank()
                    && confidence != null && confidence >= 70.0 && bounds != null
                    && bounds.left() >= 0 && bounds.top() >= 0 && bounds.width() > 0 && bounds.height() > 0
                    && bounds.imageWidth() > 0 && bounds.imageHeight() > 0
                    && bounds.left() + bounds.width() <= bounds.imageWidth()
                    && bounds.top() + bounds.height() <= bounds.imageHeight();
        }

        public String locator() {
            if (!isVerifiable()) return "";
            return "pdf:page=" + sourcePage + ";ocr=" + blockId + ";bbox=" + bounds.left() + ","
                    + bounds.top() + "," + bounds.width() + "," + bounds.height();
        }
    }

    /** Coordinates are pixels in the rendered page; page dimensions make them reproducible. */
    public record BoundingBox(int left, int top, int width, int height, int imageWidth, int imageHeight) {
    }

    /**
     * 文件级元信息（必须包含 sha256 与 fileType）。
     * <p>本 record 不在 compact constructor 抛异常；不变量通过 {@link #validate()}
     * 暴露。sha256/fileType 为空时 validate 返回 {@code Optional.of(reason)}。
     */
    public record ArtifactMeta(
            String documentId,
            String sha256,
            String fileType,
            String extractionVersion
    ) {
        public ArtifactMeta {
            if (documentId == null) {
                documentId = "";
            }
            if (sha256 == null) {
                sha256 = "";
            }
            if (fileType == null) {
                fileType = "";
            }
            if (extractionVersion == null) {
                extractionVersion = "";
            }
        }

        public Optional<String> validate() {
            if (sha256.isBlank()) {
                return Optional.of("DocumentArtifact.sha256 is required");
            }
            if (fileType.isBlank()) {
                return Optional.of("DocumentArtifact.fileType is required");
            }
            return Optional.empty();
        }
    }

    /**
     * 单张表（v2 形态：cells 是扁平数组，包含 origin 与 isMergedContinue 引用）。
     * <p>本 record 不在 compact constructor 抛异常；不变量通过 {@link #validate()} 暴露。
     */
    public record CanonicalTable(
            String tableId,
            Integer sourcePage,
            String sourceSection,
            int rows,
            int columns,
            List<MergedRegionDto> mergedRegions,
            List<CanonicalCellDto> cells,
            Boolean isScoreTable
    ) {
        public CanonicalTable {
            if (tableId == null) {
                tableId = "";
            }
            if (sourceSection == null) {
                sourceSection = "";
            }
            cells = cells == null ? List.of() : List.copyOf(cells);
            mergedRegions = mergedRegions == null ? List.of() : List.copyOf(mergedRegions);
        }

        public Optional<String> validate() {
            if (tableId.isBlank()) {
                return Optional.of("CanonicalTable.tableId is required");
            }
            if (rows < 0) {
                return Optional.of("CanonicalTable.rows must be >= 0");
            }
            if (columns < 0) {
                return Optional.of("CanonicalTable.columns must be >= 0");
            }
            return Optional.empty();
        }

        public record MergedRegionDto(String startCellId, Integer rowspan, Integer colspan) {}

        public record CanonicalCellDto(
                String cellId,
                Integer row,
                Integer column,
                String rawText,
                String normalizedText,
                Integer rowspan,
                Integer colspan,
                Boolean isMergedContinue,
                String sourceXmlPath,
                Integer sourcePage,
                String logicalRowId
        ) {
            public CanonicalCellDto(String cellId, Integer row, Integer column, String rawText,
                                    String normalizedText, Integer rowspan, Integer colspan,
                                    Boolean isMergedContinue, String sourceXmlPath, Integer sourcePage) {
                this(cellId, row, column, rawText, normalizedText, rowspan, colspan,
                        isMergedContinue, sourceXmlPath, sourcePage, "");
            }

            public CanonicalCellDto(String cellId, Integer row, Integer column, String rawText,
                                    String normalizedText, Integer rowspan, Integer colspan,
                                    Boolean isMergedContinue, String sourceXmlPath) {
                this(cellId, row, column, rawText, normalizedText, rowspan, colspan,
                        isMergedContinue, sourceXmlPath, null, "");
            }

            public CanonicalCellDto {
                if (logicalRowId == null) logicalRowId = "";
            }
        }
    }
}
