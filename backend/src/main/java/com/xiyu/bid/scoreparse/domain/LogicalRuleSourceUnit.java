package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Pure structural proof of a complete logical rule, independent of the length of a model quote. */
public final class LogicalRuleSourceUnit {
    private static final Pattern ID = Pattern.compile("序号|编号|条款号|条目");
    private static final Pattern NAME = Pattern.compile("评分因素|评分项目|评分项|评审因素|评审项目|评审内容|评分内容|评审要素|指标名称");
    private static final Pattern CHAPTER = Pattern.compile("^(第[一二三四五六七八九十百0-9]+章)\\s*.*$");
    private static final Pattern SCORE = Pattern.compile("分值|分数|得分|权重|满分");
    private LogicalRuleSourceUnit() {}
    public record Resolution(boolean applicable, boolean closed, List<SourceSpan> spans) {
        public Resolution { spans = List.copyOf(spans); }
    }
    private record Cell(CanonicalTable table, CanonicalCellDto cell) {}

    /** A unique exact citation in the source row's name column can anchor its rule column. */
    public static boolean isExactNamedRowAnchor(DocumentArtifact artifact, MultimodalScoreFact fact) {
        if (artifact == null || fact.sourceCellIds().isEmpty() || fact.quotedText().isBlank()) return false;
        var cited = cells(artifact).stream().filter(c -> fact.sourceCellIds().contains(c.cell().cellId())).toList();
        if (cited.size() != fact.sourceCellIds().size()) return false;
        var names = cited.stream().filter(cell -> {
            var headers = titles(cell.table().cells().stream().filter(c -> Integer.valueOf(0).equals(c.row())).toList());
            return isName(headers.getOrDefault(cell.cell().column(), ""))
                    && EvidenceText.normalize(text(cell.cell().rawText())).text()
                    .equals(EvidenceText.normalize(fact.quotedText()).text());
        }).toList();
        return names.size() == 1 && logicalRow(artifact, names.getFirst()).containsAll(cited);
    }

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
        var headers = rowHeaders(artifact, row);
        int minCitedCol = cited.stream().map(c -> c.cell().column()).filter(Objects::nonNull).mapToInt(Integer::intValue).min().orElse(0);
        // Remove structural identity/value columns, retaining rule AND material/notes columns in document order.
        var rules = row.stream().filter(c -> !text(c.cell().rawText()).isBlank() && !Boolean.TRUE.equals(c.cell().isMergedContinue()))
                .filter(c -> {
                    String role = headers.getOrDefault(c.cell().column(), "");
                    if (isId(role) || isName(role) || isScore(role)) return false;
                    if (c.cell().column() != null && c.cell().column() < minCitedCol) return false;
                    if (!headers.isEmpty()) return true;
                    return !normalized(c.cell().rawText()).equals(normalized(fact.dimension()))
                            && !normalized(c.cell().rawText()).equals(normalized(fact.code()))
                            && !text(c.cell().rawText()).matches("[（(]?\\d+(?:\\.\\d+)?\\s*分?[）)]?");
                }).toList();
        if (rules.isEmpty()) return new Resolution(true, false, List.of());
        // Unnumbered rows need their original name/value anchor alongside the full rule.
        var source = unnumbered(headers) ? row : rules;
        return new Resolution(true, true, SourceSpanResolver.resolve(artifact, source.stream().map(c -> c.cell().cellId()).toList()));
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
        var headers = rowHeaders(artifact, row);
        if (lastRow < lastTable.rows() - 1) return independent(lastTable, lastRow + 1, headers, type);
        var next = artifact.tables().subList(artifact.tables().indexOf(lastTable) + 1, artifact.tables().size()).stream()
                .filter(t -> Objects.equals(document, artifact.documentFor(t.tableId()))).findFirst();
        if (next.isEmpty()) return true;
        String chapter = chapter(first.table()), nextChapter = chapter(next.get());
        if (unnumbered(headers) && !chapter.isBlank() && !nextChapter.isBlank() && !chapter.equals(nextChapter)) return true;
        return independent(next.get(), 0, lastTable.columns() == next.get().columns() ? headers : Map.of(), type);
    }

    private static List<Cell> logicalRow(DocumentArtifact artifact, Cell first) {
        if (first.cell().logicalRowId().isBlank()) return List.of();
        var document = artifact.documentFor(first.cell().cellId());
        List<Cell> baseRow = cells(artifact).stream().filter(c -> first.cell().logicalRowId().equals(c.cell().logicalRowId())
                && first.table().sourceSection().equals(c.table().sourceSection())
                && Objects.equals(document, artifact.documentFor(c.cell().cellId()))).toList();
        if (baseRow.isEmpty()) return baseRow;
        var table = first.table();
        String type = table.sourceFileType().isBlank() ? (document == null ? "" : document.fileType()) : table.sourceFileType();
        if (!"PDF".equalsIgnoreCase(type) || table.rows() <= 1) {
            return baseRow;
        }
        int minRow = baseRow.stream().map(c -> c.cell().row()).filter(Objects::nonNull).mapToInt(Integer::intValue).min().orElse(-1);
        int maxRow = baseRow.stream().map(c -> c.cell().row()).filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(-1);
        if (minRow < 0) return baseRow;
        while (minRow > 0 && isContinuationRow(table, minRow)) {
            minRow--;
        }
        while (maxRow + 1 < table.rows() && isContinuationRow(table, maxRow + 1)) {
            maxRow++;
        }
        final int fromRow = minRow;
        final int toRow = maxRow;
        return table.cells().stream()
                .filter(c -> c.row() != null && c.row() >= fromRow && c.row() <= toRow)
                .map(c -> new Cell(table, c)).toList();
    }

    private static boolean isContinuationRow(CanonicalTable table, int rowIdx) {
        List<CanonicalCellDto> rowCells = table.cells().stream().filter(c -> Integer.valueOf(rowIdx).equals(c.row())).toList();
        if (rowCells.isEmpty() || ScoreTableSummaryPolicy.isSourceSummaryRow(rowCells, false)) return false;
        if (rowCells.stream().anyMatch(c -> ScoreTableSummaryPolicy.isReviewerInstruction(c.rawText()))) return false;
        boolean hasAnyText = rowCells.stream().anyMatch(c -> !text(c.rawText()).isBlank());
        if (!hasAnyText) return false;

        int maxCol = rowCells.stream().map(CanonicalCellDto::column).filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0);
        if (maxCol > 0) {
            boolean prefixBlank = rowCells.stream()
                    .filter(c -> c.column() != null && c.column() < maxCol)
                    .allMatch(c -> text(c.rawText()).isBlank());
            if (prefixBlank && rowCells.stream().filter(c -> Integer.valueOf(maxCol).equals(c.column())).anyMatch(c -> !text(c.rawText()).isBlank())) {
                return true;
            }
        }

        Map<Integer, String> headers = titles(table.cells().stream()
                .filter(c -> Integer.valueOf(0).equals(c.row())).toList());
        boolean hasStructure = headers.values().stream().anyMatch(h -> isId(h) || isScore(h));
        if (hasStructure) {
            boolean idBlank = rowCells.stream()
                    .filter(c -> isId(headers.getOrDefault(c.column(), "")))
                    .allMatch(c -> text(c.rawText()).isBlank());
            boolean scoreBlank = rowCells.stream()
                    .filter(c -> isScore(headers.getOrDefault(c.column(), "")))
                    .allMatch(c -> text(c.rawText()).isBlank());
            boolean nameBlank = rowCells.stream()
                    .filter(c -> isName(headers.getOrDefault(c.column(), "")))
                    .allMatch(c -> text(c.rawText()).isBlank());
            if (idBlank && scoreBlank && nameBlank) {
                return true;
            }
        }

        if (rowCells.size() >= 3 && maxCol >= 2) {
            boolean firstColBlank = rowCells.stream()
                    .filter(c -> Integer.valueOf(0).equals(c.column()))
                    .allMatch(c -> text(c.rawText()).isBlank());
            boolean lastColBlank = rowCells.stream()
                    .filter(c -> Integer.valueOf(maxCol).equals(c.column()))
                    .allMatch(c -> text(c.rawText()).isBlank());
            if (firstColBlank && lastColBlank) {
                return true;
            }
        }

        return false;
    }
    private static List<Cell> cells(DocumentArtifact artifact) {
        return artifact.tables().stream().flatMap(t -> t.cells().stream().map(c -> new Cell(t, c))).toList();
    }
    private static Map<Integer, String> rowHeaders(DocumentArtifact artifact, List<Cell> row) {
        return row.stream().map(Cell::table).distinct().map(t -> {
                    var inherited = SourceTableHeaderPolicy.headers(artifact, t);
                    return inherited.isEmpty() ? titles(t.cells().stream().filter(c -> Integer.valueOf(0).equals(c.row())).toList()) : inherited;
                })
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
            boolean noId = unnumbered(headers);
            boolean id = false, idMerged = false, name = false, score = false;
            for (var cell : cells) {
                String role = headers.getOrDefault(cell.column(), "");
                if (isId(role) && Boolean.TRUE.equals(cell.isMergedContinue())) {
                    idMerged = true;
                }
                if (text(cell.rawText()).isBlank()) continue;
                id |= isId(role);
                name |= isName(role) && (!noId || !Boolean.TRUE.equals(cell.isMergedContinue()));
                score |= isScore(role) && !Boolean.TRUE.equals(cell.isMergedContinue())
                        && ScoreSubtotalColumnPolicy.NUMBER.matcher(text(cell.rawText())).matches();
            }
            return (id || idMerged) && name || noId && name && score;
        }
        return false;
    }
    private static boolean inScoringSection(CanonicalTable table) {
        if (table == null || table.sourceSection() == null || table.sourceSection().isBlank()) return false;
        String root = table.sourceSection().split("[/＞>]", 2)[0].trim();
        return OriginalScoringSchemePolicy.isValidSchemeRoot(root);
    }
    private static boolean independentColumns(Map<Integer, String> titles) {
        var ids = titles.entrySet().stream().filter(e -> isId(e.getValue())).map(Map.Entry::getKey).toList();
        var names = titles.entrySet().stream().filter(e -> isName(e.getValue())).map(Map.Entry::getKey).toList();
        return ids.size() == 1 && names.size() == 1 && !ids.getFirst().equals(names.getFirst()) || unnumbered(titles);
    }
    private static boolean unnumbered(Map<Integer, String> titles) {
        if (titles.values().stream().anyMatch(LogicalRuleSourceUnit::isId)) return false;
        var names = titles.entrySet().stream().filter(e -> isName(e.getValue())).map(Map.Entry::getKey).toList();
        var scores = titles.entrySet().stream().filter(e -> isScore(e.getValue())).map(Map.Entry::getKey).toList();
        return !names.isEmpty() && scores.size() == 1 && !names.contains(scores.getFirst());
    }
    private static String cleanRole(String role) {
        return role == null ? "" : role.replaceAll("\\s+", "");
    }
    private static boolean isId(String role) {
        String clean = cleanRole(role);
        return ID.matcher(clean).find();
    }
    private static boolean isName(String role) {
        String clean = cleanRole(role);
        return NAME.matcher(clean).find();
    }
    private static boolean isScore(String role) {
        String clean = cleanRole(role);
        return SCORE.matcher(clean).find();
    }
    private static String chapter(CanonicalTable table) {
        var match = CHAPTER.matcher(table.sourceSection().split("[/＞>]", 2)[0].trim());
        return match.matches() ? match.group(1) : "";
    }
    private static String text(String value) { return value == null ? "" : value.strip(); }
    private static String normalized(String value) { return EvidenceText.normalize(value).text(); }
}
