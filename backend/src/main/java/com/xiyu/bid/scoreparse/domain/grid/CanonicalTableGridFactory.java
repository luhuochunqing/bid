// Input: DocumentArtifact (Sidecar v2 JSON 反序列化结果)
// Output: CanonicalTableGrid（含 StructureValidationResult）
// Pos: scoreparse/domain/grid — 纯核心（spec 059 Phase 1 FR-04 / FR-05）
package com.xiyu.bid.scoreparse.domain.grid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 从 Sidecar DocumentArtifact 创建 CanonicalTableGrid，并对结构做硬性校验（spec 059 Phase 1）。
 * <p>校验失败时返回的网格仍可被读取，但 {@link CanonicalTableGrid#isStructurallyValid()}
 * 为 false，{@link CanonicalTableGrid#validation()} 包含详细违规列表。
 * 调用方必须：
 * <ul>
 *   <li>校验失败时不得调 LLM、不得调归一化、不得覆盖 {@code score_item}</li>
 *   <li>将 {@link StructureValidationResult.Status#STRUCTURE_INCOMPLETE} 写入
 *       {@code score_parse_task.decision_status}</li>
 * </ul>
 * <p>校验不变量：
 * <ul>
 *   <li>{@link DocumentArtifact#SUPPORTED_SCHEMA_VERSION} 必须在调用前由调用方校验</li>
 *   <li>每个 logical (row, col) 至多对应一个 cell</li>
 *   <li>cellId 全表唯一；每个 cell 必须有非空 cellId</li>
 *   <li>每个 MergedRegion.startCellId 必须指向同表内的真实 origin cell</li>
 *   <li>rowspan/colspan 不可越过表格边界</li>
 *   <li>合并覆盖区域内的 cell 必须以 {@code isMergedContinue=true} 标记</li>
 * </ul>
 */
public final class CanonicalTableGridFactory {

    private CanonicalTableGridFactory() {
    }

    /**
     * 主入口：从单表 DTO 创建 CanonicalTableGrid。
     * <p>校验失败时返回的网格 {@code validation.valid()=false}；调用方必须按
     * {@link StructureValidationResult.Status#STRUCTURE_INCOMPLETE} 处理。
     * <p>本方法严格不抛业务异常：所有校验失败都收集到
     * {@link StructureValidationResult#violations()}。
     */
    public static CanonicalTableGrid fromSidecarTable(DocumentArtifact.CanonicalTable dto) {
        if (dto == null) {
            return invalid(dto, List.of(new StructureValidationResult.Violation(
                    "table", "CanonicalTable payload is null")));
        }
        List<StructureValidationResult.Violation> violations = new ArrayList<>();

        // ---- PASS 1: collect cells + verify uniqueness + per-cell invariants ----
        Set<String> seenIds = new HashSet<>();
        Set<String> duplicateIds = new HashSet<>();
        Map<Integer, Map<Integer, CanonicalCell>> grid = new HashMap<>();
        List<CanonicalCell> canonicalCells = new ArrayList<>();
        for (DocumentArtifact.CanonicalTable.CanonicalCellDto cdto : dto.cells()) {
            if (cdto == null) {
                violations.add(new StructureValidationResult.Violation(
                        "cells[]", "null cell entry"));
                continue;
            }
            String cellId = cdto.cellId();
            if (cellId == null || cellId.isBlank()) {
                violations.add(new StructureValidationResult.Violation(
                        "cells[].cellId", "empty cellId"));
                continue;
            }
            if (!seenIds.add(cellId)) {
                duplicateIds.add(cellId);
                continue;
            }
            int row = cdto.row() == null ? -1 : cdto.row();
            int col = cdto.column() == null ? -1 : cdto.column();
            int rowspan = cdto.rowspan() == null ? 1 : cdto.rowspan();
            int colspan = cdto.colspan() == null ? 1 : cdto.colspan();
            boolean isCont = cdto.isMergedContinue() != null && cdto.isMergedContinue();
            if (row < 0 || col < 0) {
                violations.add(new StructureValidationResult.Violation(
                        cellId, "row/column must be non-negative"));
                continue;
            }
            if (rowspan < 1 || colspan < 1) {
                violations.add(new StructureValidationResult.Violation(
                        cellId, "rowspan/colspan must be >= 1"));
                continue;
            }
            CanonicalCell cell = new CanonicalCell(
                    cellId,
                    row,
                    col,
                    cdto.rawText() == null ? "" : cdto.rawText(),
                    cdto.normalizedText() == null ? "" : cdto.normalizedText(),
                    rowspan,
                    colspan,
                    isCont,
                    cdto.sourceXmlPath() == null ? "" : cdto.sourceXmlPath());
            Optional<String> cellIssue = cell.validate();
            if (cellIssue.isPresent()) {
                violations.add(new StructureValidationResult.Violation(
                        cellId, cellIssue.get()));
                continue;
            }
            grid.computeIfAbsent(row, k -> new HashMap<>()).put(col, cell);
            canonicalCells.add(cell);
        }
        for (String dup : duplicateIds) {
            violations.add(new StructureValidationResult.Violation(
                    dup, "duplicate cellId within table"));
        }

        // ---- PASS 2: verify grid coverage ----
        if (dto.rows() > 0 && dto.columns() > 0) {
            for (int r = 0; r < dto.rows(); r++) {
                for (int c = 0; c < dto.columns(); c++) {
                    Map<Integer, CanonicalCell> rowMap = grid.get(r);
                    if (rowMap == null || !rowMap.containsKey(c)) {
                        violations.add(new StructureValidationResult.Violation(
                                "grid[" + r + "][" + c + "]", "missing cell at logical position"));
                    }
                }
            }
        } else {
            violations.add(new StructureValidationResult.Violation(
                    "rows/columns", "rows and columns must be > 0"));
        }

        // ---- PASS 3: verify merged regions ----
        List<MergedRegion> regions = new ArrayList<>();
        Set<String> regionStarts = new HashSet<>();
        if (dto.mergedRegions() != null) {
            for (DocumentArtifact.CanonicalTable.MergedRegionDto rdto : dto.mergedRegions()) {
                if (rdto == null || rdto.startCellId() == null
                        || rdto.rowspan() == null || rdto.colspan() == null) {
                    violations.add(new StructureValidationResult.Violation(
                            "mergedRegions[]", "missing field"));
                    continue;
                }
                MergedRegion region = new MergedRegion(
                        rdto.startCellId(), rdto.rowspan(), rdto.colspan());
                Optional<String> regionIssue = region.validate();
                if (regionIssue.isPresent()) {
                    violations.add(new StructureValidationResult.Violation(
                            rdto.startCellId(), regionIssue.get()));
                    continue;
                }
                // Verify startCellId points to a real cell in this table.
                boolean found = canonicalCells.stream()
                        .anyMatch(c -> c.cellId().equals(region.startCellId()));
                if (!found) {
                    violations.add(new StructureValidationResult.Violation(
                            region.startCellId(),
                            "MergedRegion.startCellId does not reference a cell in this table"));
                    continue;
                }
                CanonicalCell origin = canonicalCells.stream()
                        .filter(c -> c.cellId().equals(region.startCellId()))
                        .findFirst().orElse(null);
                if (origin != null) {
                    if (origin.rowspan() != region.rowspan()
                            || origin.colspan() != region.colspan()) {
                        violations.add(new StructureValidationResult.Violation(
                                origin.cellId(),
                                "MergedRegion span mismatch: cell "
                                        + origin.rowspan() + "x" + origin.colspan()
                                        + " vs region "
                                        + region.rowspan() + "x" + region.colspan()));
                        continue;
                    }
                    if (origin.row() + region.rowspan() > dto.rows()
                            || origin.column() + region.colspan() > dto.columns()) {
                        violations.add(new StructureValidationResult.Violation(
                                origin.cellId(),
                                "MergedRegion exceeds table bounds"));
                        continue;
                    }
                }
                if (!regionStarts.add(region.startCellId())) {
                    violations.add(new StructureValidationResult.Violation(
                            region.startCellId(),
                            "duplicate MergedRegion.startCellId"));
                    continue;
                }
                regions.add(region);
            }
        }

        // ---- PASS 4: verify isMergedContinue coverage ----
        Map<String, CanonicalCell> updatedContinuations = new HashMap<>();
        for (CanonicalCell origin : canonicalCells) {
            if (origin.isMergedContinue()) {
                continue;
            }
            for (int dr = 0; dr < origin.rowspan(); dr++) {
                for (int dc = 0; dc < origin.colspan(); dc++) {
                    int rr = origin.row() + dr;
                    int cc = origin.column() + dc;
                    if (rr >= dto.rows() || cc >= dto.columns()) {
                        continue;
                    }
                    if (dr == 0 && dc == 0) {
                        continue;
                    }
                    Map<Integer, CanonicalCell> rowMap = grid.get(rr);
                    if (rowMap == null) {
                        violations.add(new StructureValidationResult.Violation(
                                origin.cellId(),
                                "missing coverage cell at (" + rr + "," + cc + ")"));
                        continue;
                    }
                    CanonicalCell covered = rowMap.get(cc);
                    if (covered == null) {
                        violations.add(new StructureValidationResult.Violation(
                                origin.cellId(),
                                "missing coverage cell at (" + rr + "," + cc + ")"));
                    } else if (!covered.isMergedContinue()) {
                        violations.add(new StructureValidationResult.Violation(
                                origin.cellId(),
                                "expected isMergedContinue at (" + rr + "," + cc + ") "
                                        + "but found origin cellId " + covered.cellId()));
                    } else if (covered.rawText().isBlank() && !origin.rawText().isBlank()) {
                        CanonicalCell inherited = new CanonicalCell(
                                covered.cellId(), covered.row(), covered.column(),
                                origin.rawText(), origin.normalizedText(),
                                covered.rowspan(), covered.colspan(),
                                covered.isMergedContinue(), covered.sourceXmlPath()
                        );
                        rowMap.put(cc, inherited);
                        updatedContinuations.put(covered.cellId(), inherited);
                    }
                }
            }
        }
        if (!updatedContinuations.isEmpty()) {
            for (int i = 0; i < canonicalCells.size(); i++) {
                CanonicalCell cell = canonicalCells.get(i);
                CanonicalCell updated = updatedContinuations.get(cell.cellId());
                if (updated != null) {
                    canonicalCells.set(i, updated);
                }
            }
        }

        StructureValidationResult result = violations.isEmpty()
                ? StructureValidationResult.ok()
                : StructureValidationResult.incomplete(violations);
        return new CanonicalTableGrid(
                dto.tableId(),
                dto.rows(),
                dto.columns(),
                canonicalCells,
                regions,
                result);
    }

    private static CanonicalTableGrid invalid(
            DocumentArtifact.CanonicalTable dto,
            List<StructureValidationResult.Violation> violations) {
        return new CanonicalTableGrid(
                dto == null ? "" : dto.tableId(),
                0, 0,
                List.of(),
                List.of(),
                StructureValidationResult.incomplete(violations));
    }
}
