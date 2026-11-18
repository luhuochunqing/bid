// Input: adjacent OCR score tables with repeated page headers
// Output: conservative decision to link a numbered continuation
// Pos: scoreparse/infrastructure/baidu — physical table continuity
package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import java.util.Comparator;
import java.util.List;

final class BaiduRepeatedHeaderPolicy {
    private BaiduRepeatedHeaderPolicy() {}

    static boolean hasEvaluationCriteriaHeader(CanonicalTable table) {
        return com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping.isEvaluationCriteriaHeader(
                java.util.stream.IntStream.range(0, table.columns()).mapToObj(col -> text(table, 0, col)).toList());
    }

    static boolean continuesEvaluationCriteria(CanonicalTable previous, CanonicalTable current) {
        return hasEvaluationCriteriaHeader(previous) && current.rows() > 1
                && previous.sourceSection() != null && !previous.sourceSection().isBlank()
                && previous.sourceSection().equals(current.sourceSection())
                && !hasDeclaredTotal(previous)
                && java.util.stream.IntStream.range(0, 4).allMatch(col -> text(current, 1, col).isBlank())
                && !text(current, 1, 4).isBlank()
                && !com.xiyu.bid.scoreparse.domain.ScoreTableSummaryPolicy.isReviewerInstruction(text(current, 1, 4));
    }

    static boolean isContinuation(CanonicalTable previous, CanonicalTable current) {
        if (previous.columns() != current.columns() || current.rows() < 3 || previous.rows() < 2
                || previous.sourceSection() == null || previous.sourceSection().isBlank()
                || !previous.sourceSection().equals(current.sourceSection())) return false;
        if (current.cells().stream().anyMatch(c -> Integer.valueOf(0).equals(c.row())
                && c.rowspan() != null && c.rowspan() > 1)) return false;
        for (int col = 0; col < previous.columns(); col++) {
            if (!text(previous, 0, col).equals(text(current, 0, col))) return false;
        }
        if (hasDeclaredTotal(previous)) return false;
        for (int col = 0; col < current.columns() - 2; col++)
            if (!text(current, 1, col).isBlank()) return false;
        if (text(current, 1, current.columns() - 2).isBlank()) return false;
        List<Integer> left = numbers(previous, 1);
        List<Integer> right = numbers(current, 2);
        return !left.isEmpty() && !right.isEmpty() && right.getFirst() == left.getLast() + 1;
    }

    static java.util.Map<Integer, CanonicalTable> originalPageOwners(List<CanonicalTable> tables) {
        var owners = new java.util.HashMap<Integer, CanonicalTable>();
        var ambiguous = new java.util.HashSet<Integer>();
        for (var table : tables) {
            if (table == null || !Boolean.TRUE.equals(table.isScoreTable())
                    || table.sourceSection() == null || table.sourceSection().isBlank()) continue;
            var pages = new java.util.HashSet<Integer>();
            if (table.sourcePage() != null) pages.add(table.sourcePage());
            table.cells().stream().map(CanonicalCellDto::sourcePage).filter(java.util.Objects::nonNull).forEach(pages::add);
            for (var page : pages) {
                if (owners.containsKey(page) && owners.get(page) != table) ambiguous.add(page);
                else owners.put(page, table);
            }
        }
        ambiguous.forEach(owners::remove);
        return java.util.Map.copyOf(owners);
    }

    static boolean hasDeclaredTotal(CanonicalTable table) {
        return table.cells().stream().anyMatch(c -> c.normalizedText() != null
                && c.normalizedText().replaceAll("\\s+", "").matches("(?:合计|总计)(?:得分)?|总分|总得分|总评分"));
    }

    private static String text(CanonicalTable table, int row, int column) {
        return table.cells().stream().filter(c -> Integer.valueOf(row).equals(c.row())
                        && Integer.valueOf(column).equals(c.column())).findFirst()
                .map(CanonicalCellDto::rawText).orElse("").replaceAll("\\s+", "");
    }

    private static List<Integer> numbers(CanonicalTable table, int start) {
        return table.cells().stream().filter(c -> c.row() != null && c.row() >= start
                        && Integer.valueOf(0).equals(c.column()) && c.rawText() != null
                        && c.rawText().strip().matches("[0-9]{1,4}"))
                .sorted(Comparator.comparing(CanonicalCellDto::row))
                .map(c -> Integer.valueOf(c.rawText().strip())).toList();
    }
}
