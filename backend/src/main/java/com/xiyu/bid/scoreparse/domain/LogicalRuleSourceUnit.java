package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Pattern;

/** Pure structural proof of a complete logical rule, independent of the length of a model quote. */
public final class LogicalRuleSourceUnit {
    private static final Pattern ID = Pattern.compile("序号|编号");
    private static final Pattern NAME = Pattern.compile("评分因素|评分项目|评分项|评审因素|评审项目");
    private static final Pattern SCORE = Pattern.compile("分值|分数|得分");
    private LogicalRuleSourceUnit() {}
    public record Resolution(boolean applicable, boolean closed, List<SourceSpan> spans) {
        public Resolution { spans = List.copyOf(spans); }
    }
    private record Cell(CanonicalTable table, CanonicalCellDto cell) {}

    /** Physical citations can be disjoint while restoration reads the same complete logical source row. */
    public static boolean sharesLogicalRow(DocumentArtifact artifact, MultimodalScoreFact first, MultimodalScoreFact second) {
        if (artifact == null) return false;
        return cells(artifact).stream().filter(c -> first.sourceCellIds().contains(c.cell().cellId()))
                .flatMap(c -> logicalRow(artifact, c).stream())
                .anyMatch(c -> second.sourceCellIds().contains(c.cell().cellId()));
    }

    public static Resolution resolve(DocumentArtifact artifact, MultimodalScoreFact fact) {
        if (artifact == null) return new Resolution(false, false, List.of());
        var cited = cells(artifact).stream().filter(c -> fact.sourceCellIds().contains(c.cell().cellId())).toList();
        if (cited.isEmpty()) return new Resolution(false, false, List.of());
        var first = cited.getFirst();
        var document = artifact.documentFor(first.cell().cellId());
        String type = first.table().sourceFileType().isBlank() ? document == null ? "" : document.fileType() : first.table().sourceFileType();
        if (!"DOCX".equalsIgnoreCase(type) && !"PDF".equalsIgnoreCase(type)) return new Resolution(false, false, List.of());
        if (cited.stream().anyMatch(c -> c.cell().logicalRowId().isBlank())) return new Resolution(true, false, List.of());
        var row = logicalRow(artifact, first);
        if (row.isEmpty() || cited.stream().anyMatch(c -> !row.contains(c))) return new Resolution(true, false, List.of());
        var ids = row.stream().map(c -> c.cell().cellId()).toList();
        // PDF logical rows already carry physical page continuity. The DOCX following-row guard additionally
        // rejects unproven breaks between XML tables; a PDF table's role labels need not be repeated per page.
        boolean proven = "DOCX".equalsIgnoreCase(type) ? closed(artifact, ids)
                : row.stream().allMatch(c -> c.cell().sourcePage() != null || c.table().sourcePage() != null)
                && OriginalScoreQualificationPolicy.resolveReferencedCells(artifact, row.getFirst().table().tableId(), ids).size() == row.size();
        if (!proven) return new Resolution(true, false, List.of());
        var headers = rowHeaders(row);
        // Remove structural identity/value columns, retaining rule AND material/notes columns in document order.
        var rules = row.stream().filter(c -> !text(c.cell().rawText()).isBlank() && !Boolean.TRUE.equals(c.cell().isMergedContinue()))
                .filter(c -> {
                    String role = headers.getOrDefault(c.cell().column(), "");
                    if (ID.matcher(role).matches() || NAME.matcher(role).matches() || SCORE.matcher(role).matches()) return false;
                    if (!headers.isEmpty()) return true;
                    return !normalized(c.cell().rawText()).equals(normalized(fact.dimension()))
                            && !normalized(c.cell().rawText()).equals(normalized(fact.code()))
                            && !text(c.cell().rawText()).matches("[（(]?\\d+(?:\\.\\d+)?\\s*分?[）)]?");
                }).toList();
        if (rules.isEmpty()) return new Resolution(true, false, List.of());
        return new Resolution(true, true, SourceSpanResolver.resolve(artifact, rules.stream().map(c -> c.cell().cellId()).toList()));
    }

    /** Also used before name/score reconstruction; a short exact quote supplies no closure proof. */
    public static boolean closed(DocumentArtifact artifact, List<String> ids) {
        if (artifact == null || ids.isEmpty()) return false;
        var cited = cells(artifact).stream().filter(c -> ids.contains(c.cell().cellId())).toList();
        if (cited.isEmpty()) return false;
        var row = logicalRow(artifact, cited.getFirst());
        if (row.isEmpty() || cited.stream().anyMatch(c -> !row.contains(c))) return false;
        var first = row.getFirst();
        var document = artifact.documentFor(first.cell().cellId());
        String type = first.table().sourceFileType().isBlank() ? document == null ? "" : document.fileType() : first.table().sourceFileType();
        if ("DOCX".equalsIgnoreCase(type) && row.stream().anyMatch(c -> text(c.cell().sourceXmlPath()).isBlank())) return false;
        if ("PDF".equalsIgnoreCase(type) && row.stream().anyMatch(c -> c.cell().sourcePage() == null && c.table().sourcePage() == null)) return false;
        var rowIds = row.stream().map(c -> c.cell().cellId()).toList();
        if (OriginalScoreQualificationPolicy.resolveReferencedCells(artifact, first.table().tableId(), rowIds).size() != row.size()) return false;
        var lastTable = row.getLast().table();
        int lastRow = row.stream().filter(c -> c.table().equals(lastTable)).map(Cell::cell).map(CanonicalCellDto::row)
                .filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(-1);
        if (lastRow < 0 || lastTable.rows() <= 0) return false;
        var headers = rowHeaders(row);
        if (lastRow < lastTable.rows() - 1) return independent(lastTable, lastRow + 1, headers, type);
        var next = artifact.tables().subList(artifact.tables().indexOf(lastTable) + 1, artifact.tables().size()).stream()
                .filter(t -> Objects.equals(document, artifact.documentFor(t.tableId()))).findFirst();
        if (next.isEmpty()) return true;
        return independent(next.get(), 0, lastTable.columns() == next.get().columns() ? headers : Map.of(), type);
    }

    private static List<Cell> logicalRow(DocumentArtifact artifact, Cell first) {
        if (first.cell().logicalRowId().isBlank()) return List.of();
        var document = artifact.documentFor(first.cell().cellId());
        return cells(artifact).stream().filter(c -> first.cell().logicalRowId().equals(c.cell().logicalRowId())
                && first.table().sourceSection().equals(c.table().sourceSection())
                && Objects.equals(document, artifact.documentFor(c.cell().cellId()))).toList();
    }
    private static List<Cell> cells(DocumentArtifact artifact) {
        return artifact.tables().stream().flatMap(t -> t.cells().stream().map(c -> new Cell(t, c))).toList();
    }
    private static Map<Integer, String> rowHeaders(List<Cell> row) {
        return row.stream().map(Cell::table).distinct().map(t -> titles(t.cells().stream()
                        .filter(c -> Integer.valueOf(0).equals(c.row())).toList()))
                .filter(LogicalRuleSourceUnit::independentColumns).findFirst().orElse(Map.of());
    }
    private static Map<Integer, String> titles(List<CanonicalCellDto> cells) {
        Map<Integer, String> titles = new LinkedHashMap<>();
        cells.stream().filter(c -> c.column() != null).forEach(c -> titles.put(c.column(), text(c.rawText())));
        return titles;
    }
    private static boolean independent(CanonicalTable table, int from, Map<Integer, String> inherited, String type) {
        if ("PDF".equalsIgnoreCase(type) && inScoringSection(table)) return true;
        var headers = inherited;
        for (int number : table.cells().stream().map(CanonicalCellDto::row).filter(Objects::nonNull)
                .filter(n -> n >= from).distinct().sorted().toList()) {
            var cells = table.cells().stream().filter(c -> Objects.equals(c.row(), number)).toList();
            var titles = titles(cells);
            if (independentColumns(titles)) { headers = titles; continue; }
            boolean id = false, name = false;
            for (var cell : cells) {
                if (text(cell.rawText()).isBlank()) continue;
                String role = headers.getOrDefault(cell.column(), "");
                id |= ID.matcher(role).matches();
                name |= NAME.matcher(role).matches();
            }
            return id && name;
        }
        return false;
    }
    private static boolean inScoringSection(CanonicalTable table) {
        if (table == null || table.sourceSection() == null || table.sourceSection().isBlank()) return false;
        String root = table.sourceSection().split("[/＞>]", 2)[0].trim();
        return OriginalScoringSchemePolicy.isValidSchemeRoot(root);
    }
    private static boolean independentColumns(Map<Integer, String> titles) {
        var ids = titles.entrySet().stream().filter(e -> ID.matcher(e.getValue()).matches()).map(Map.Entry::getKey).toList();
        var names = titles.entrySet().stream().filter(e -> NAME.matcher(e.getValue()).matches()).map(Map.Entry::getKey).toList();
        return ids.size() == 1 && names.size() == 1 && !ids.getFirst().equals(names.getFirst());
    }
    private static String text(String value) { return value == null ? "" : value.strip(); }
    private static String normalized(String value) { return EvidenceText.normalize(value).text(); }
}
