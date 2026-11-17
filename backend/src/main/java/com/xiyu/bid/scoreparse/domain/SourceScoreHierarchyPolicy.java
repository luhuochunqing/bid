// Input: scoped scoring facts and explicit category cells in canonical PDF tables.
// Output: parent links restored from original column and group boundaries, independent of model codes.
// Pos: scoreparse/domain — pure hierarchy correction; no guessed parents or created facts.
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

public final class SourceScoreHierarchyPolicy {
    private SourceScoreHierarchyPolicy() {}
    private record Located(CanonicalTable table, CanonicalCellDto cell, int position, String scope) {}
    private static final String NAME_HEADER = "评审项目|评分项目|评分项|评分因素|评审因素";
    private static final java.util.regex.Pattern GROUP = java.util.regex.Pattern.compile("^(.+?)[(](\\d+(?:\\.\\d+)?)分?[)]$");

    public static List<MultimodalScoreFact> restore(List<MultimodalScoreFact> facts, DocumentArtifact artifact) {
        if (artifact == null) return facts;
        var located = new ArrayList<Located>();
        artifact.tables().stream().filter(t -> !SourceTableHeaderPolicy.headers(artifact, t).isEmpty() && t.sourcePage() != null
                        && "PDF".equalsIgnoreCase(t.sourceFileType().isBlank() ? artifact.documentFor(t.tableId()).fileType() : t.sourceFileType()))
                .sorted(Comparator.comparing(CanonicalTable::sourcePage)).forEach(t -> {
                    var meta = artifact.documentFor(t.tableId());
                    String scheme = OriginalScoringContext.schemeId(OriginalScoringContext.schemeTitleForTable(t));
                    String scope = meta.documentId() + "|" + meta.sha256() + "|" + scheme;
                    t.cells().stream().filter(c -> c.row() != null && c.column() != null)
                            .sorted(Comparator.comparing(CanonicalCellDto::row).thenComparing(CanonicalCellDto::column))
                            .forEach(c -> located.add(new Located(t, c, located.size(), scope)));
                });
        var labels = located.stream().filter(l -> nameColumn(artifact, l))
                .filter(l -> !Boolean.TRUE.equals(l.cell().isMergedContinue()) && !clean(l.cell().rawText()).isBlank()
                        && !clean(l.cell().rawText()).matches(NAME_HEADER)).toList();
        var groups = labels.stream().filter(l -> GROUP.matcher(clean(l.cell().rawText())).matches())
                .filter(l -> hasGroupStructure(artifact, l, located)).toList();
        return facts.stream().map(f -> restoreOne(f, facts, located, labels, groups)).toList();
    }

    private static boolean nameColumn(DocumentArtifact artifact, Located located) {
        return SourceTableHeaderPolicy.headers(artifact, located.table())
                .getOrDefault(located.cell().column(), "").matches(NAME_HEADER);
    }

    private static boolean hasGroupStructure(DocumentArtifact artifact, Located label, List<Located> located) {
        // A score suffix alone also occurs on ordinary leaves. Require a hierarchy layout
        // or a vertically continued name cell before treating it as a parent container.
        if (SourceTableHeaderPolicy.headers(artifact, label.table()).values().stream()
                .filter(h -> h.matches(NAME_HEADER)).count() > 1
                || label.cell().rowspan() != null && label.cell().rowspan() > 1) return true;
        return located.stream().filter(l -> l.position() > label.position() && l.scope().equals(label.scope())
                        && l.table().columns() == label.table().columns()
                        && l.cell().column().equals(label.cell().column()) && nameColumn(artifact, l)
                        && !clean(l.cell().rawText()).matches(NAME_HEADER))
                .findFirst().filter(l -> clean(l.cell().rawText()).isBlank()
                        || Boolean.TRUE.equals(l.cell().isMergedContinue())).isPresent();
    }

    private static MultimodalScoreFact restoreOne(MultimodalScoreFact fact, List<MultimodalScoreFact> facts,
                                                   List<Located> located, List<Located> labels, List<Located> groups) {
        if (!"IN_SCHEME".equals(fact.scopeStatus())) return fact;
        var ownGroups = groups.stream().filter(g -> owns(fact, g)).toList();
        var anchors = ownGroups.isEmpty() ? located.stream().filter(l -> fact.sourceCellIds().contains(l.cell().cellId())).toList() : ownGroups;
        if (anchors.isEmpty() || anchors.stream().map(Located::scope).distinct().count() != 1) return fact;
        var anchor = anchors.getFirst();
        var active = new TreeMap<Integer, Located>();
        labels.stream().filter(g -> g.scope().equals(anchor.scope()) && g.position() <= anchor.position()
                        && g.cell().column() < anchor.cell().column())
                .forEach(g -> {
                    // Every new name ends the old group at this level, even an ordinary leaf without a score suffix.
                    active.tailMap(g.cell().column(), true).clear();
                    if (groups.contains(g)) active.put(g.cell().column(), g);
                });
        // A group owns subsequent rows until the next label in its column or an outer column.
        for (var group : active.descendingMap().values()) {
            var parents = facts.stream().filter(p -> p != fact && p.scopeStatus().equals(fact.scopeStatus())
                    && p.schemeId().equals(fact.schemeId()) && !p.code().isBlank() && owns(p, group)).toList();
            if (parents.size() == 1) return fact.withParentCode(parents.getFirst().code());
            if (parents.size() > 1) return fact;
        }
        return fact;
    }

    private static boolean owns(MultimodalScoreFact fact, Located group) {
        var label = GROUP.matcher(clean(group.cell().rawText()));
        if (!label.matches() || fact.weight() == null || new BigDecimal(label.group(2)).compareTo(fact.weight()) != 0) return false;
        return fact.sourceCellIds().contains(group.cell().cellId())
                || (fact.tableId().isBlank() && fact.sourceCellIds().isEmpty()
                && clean(fact.quotedText()).equals(clean(group.cell().rawText())));
    }
    private static String clean(String raw) {
        return java.text.Normalizer.normalize(EvidenceText.normalize(raw).text(), java.text.Normalizer.Form.NFKC)
                .replaceAll("\\s+", "");
    }
}
