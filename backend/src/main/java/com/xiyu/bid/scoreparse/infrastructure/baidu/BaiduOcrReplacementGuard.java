// Input: original PDF grid and OCR tables from the same physical pages
// Output: pages where OCR demonstrably collapsed two source score rows
// Pos: scoreparse/infrastructure/baidu — conservative source preservation
package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class BaiduOcrReplacementGuard {
    private static final Pattern TWO_CODES = Pattern.compile("^([0-9]{1,4})\\s+([0-9]{1,4})$");
    private BaiduOcrReplacementGuard() {}

    static Set<Integer> preservedPages(List<CanonicalTable> baseline, List<CanonicalTable> ocr) {
        Set<Integer> protectedPages = new LinkedHashSet<>();
        for (var original : baseline) {
            if (original == null || !Boolean.TRUE.equals(original.isScoreTable())
                    || original.sourceSection() == null || original.sourceSection().isBlank()) continue;
            Set<Integer> pages = pages(original);
            if (pages.size() < 2) continue;
            boolean broken = ocr.stream().filter(t -> t != null && Boolean.TRUE.equals(t.isScoreTable())
                            && original.sourceSection().equals(t.sourceSection()))
                    .flatMap(t -> t.cells().stream().filter(c -> Integer.valueOf(0).equals(c.column()))
                            .filter(c -> c.sourcePage() != null && pages.contains(c.sourcePage()))
                            .map(c -> collapsedRows(original, t, c)))
                    .anyMatch(Boolean::booleanValue);
            if (!broken) continue;
            boolean crossesOutside = ocr.stream().filter(t -> t != null && !java.util.Collections.disjoint(pages(t), pages))
                    .anyMatch(t -> !pages.containsAll(pages(t)));
            boolean multipleTablesOnPage = pages.stream().anyMatch(page -> ocr.stream()
                    .filter(t -> t != null && pages(t).contains(page)).count() > 1);
            if (!crossesOutside && !multipleTablesOnPage) protectedPages.addAll(pages);
        }
        return Set.copyOf(protectedPages);
    }

    static List<CanonicalTable> excludingPages(List<CanonicalTable> tables, Set<Integer> preservedPages) {
        return tables.stream().filter(table -> java.util.Collections.disjoint(pages(table), preservedPages)).toList();
    }

    private static boolean collapsedRows(CanonicalTable original, CanonicalTable ocr, CanonicalCellDto codeCell) {
        var match = TWO_CODES.matcher(codeCell.rawText() == null ? "" : codeCell.rawText().strip());
        if (!match.matches() || Integer.parseInt(match.group(2)) != Integer.parseInt(match.group(1)) + 1) return false;
        if (original.columns() < 3 || ocr.columns() < 3) return false;
        String combinedDimension = cellText(ocr, codeCell.row(), 1).replaceAll("\\s+", "");
        String combinedScore = cellText(ocr, codeCell.row(), ocr.columns() - 1);
        String[] scores = combinedScore.strip().split("\\s+");
        if (scores.length != 2 || !scores[0].matches("[0-9]+(?:\\.[0-9]+)?")
                || !scores[1].matches("[0-9]+(?:\\.[0-9]+)?")) return false;
        return rowMatches(original, codeCell.sourcePage(), match.group(1), scores[0], combinedDimension)
                && rowMatches(original, codeCell.sourcePage(), match.group(2), scores[1], combinedDimension);
    }

    private static boolean rowMatches(CanonicalTable original, int page, String code, String score, String combinedDimension) {
        var matches = original.cells().stream().filter(c -> Integer.valueOf(0).equals(c.column())
                && Integer.valueOf(page).equals(c.sourcePage()) && c.rawText() != null && code.equals(c.rawText().strip())).toList();
        if (matches.size() != 1) return false;
        Integer row = matches.getFirst().row();
        String name = cellText(original, row, 1).replaceAll("\\s+", "");
        return !name.isBlank() && combinedDimension.contains(name)
                && score.equals(cellText(original, row, original.columns() - 1).strip());
    }

    private static String cellText(CanonicalTable table, Integer row, int column) {
        return table.cells().stream().filter(c -> row != null && row.equals(c.row())
                && Integer.valueOf(column).equals(c.column())).findFirst()
                .map(c -> c.rawText() == null ? "" : c.rawText()).orElse("");
    }

    private static Set<Integer> pages(CanonicalTable table) {
        Set<Integer> pages = new LinkedHashSet<>();
        if (table.sourcePage() != null) pages.add(table.sourcePage());
        table.cells().stream().map(CanonicalCellDto::sourcePage).filter(java.util.Objects::nonNull).forEach(pages::add);
        return pages;
    }
}
