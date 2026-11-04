package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.stream.Stream;

/** Coalesces proven source aliases while retaining a stable code and the candidate graph. */
public final class SourceFactDeduplication {
    private SourceFactDeduplication() {}

    public static List<MultimodalScoreFact> merge(List<MultimodalScoreFact> facts) {
        return merge(facts, null);
    }

    public static List<MultimodalScoreFact> merge(List<MultimodalScoreFact> facts, DocumentArtifact artifact) {
        var canonical = new ArrayList<MultimodalScoreFact>();
        var owners = new IdentityHashMap<MultimodalScoreFact, Integer>();
        var graph = MultimodalFactParentCodeNormalizer.normalize(facts);
        // A whole source unit shared by hierarchy levels cannot prove which level an
        // unparented alias denotes. Preserve that entire ambiguous unit in every input order.
        var hierarchyUnits = SourceFactIdentity.ambiguousHierarchyUnits(graph, artifact);
        for (var fact : facts) {
            int index = -1;
            for (int i = 0; i < canonical.size(); i++) {
                if (SourceFactIdentity.sameUnit(canonical.get(i), fact, artifact)
                        && !SourceFactIdentity.related(canonical.get(i), fact, graph)
                        && hierarchyUnits.stream().noneMatch(unit ->
                                SourceFactIdentity.sameUnit(unit, fact.withParentCode(""), artifact))
                        && SourceFactIdentity.sameWeight(canonical.get(i), fact)) { index = i; break; }
            }
            if (index < 0) {
                index = canonical.size();
                canonical.add(fact);
            } else {
                canonical.set(index, retain(canonical.get(index), fact));
            }
            owners.put(fact, index);
        }
        var result = new ArrayList<>(canonical);
        for (var fact : facts) {
            var parent = MultimodalFactParentCodeNormalizer.resolveParent(fact, facts);
            Integer parentIndex = parent == null ? null : owners.get(parent);
            int childIndex = owners.get(fact);
            if (parentIndex != null && parentIndex != childIndex) {
                var child = result.get(childIndex);
                result.set(childIndex, child.withParentCode(canonical.get(parentIndex).code()));
            }
        }
        return List.copyOf(result);
    }

    private static MultimodalScoreFact retain(MultimodalScoreFact first, MultimodalScoreFact alias) {
        var old = first.sourceMetadata();
        var other = alias.sourceMetadata();
        var notes = Stream.concat(Stream.concat(old.diagnostics().stream(), other.diagnostics().stream()),
                Stream.of("SOURCE_DUPLICATE_MERGED:" + alias.code())).distinct().toList();
        var metadata = new ScoreSourceMetadata(old.modelRuleText(), old.modelQuotedText(),
                Stream.concat(old.sourceSpans().stream(), other.sourceSpans().stream()).distinct().toList(), notes,
                old.modelCategory(), old.modelScoreType(), old.modelSourceClaim());
        return new MultimodalScoreFact(first.sectionTitle(), first.code(),
                first.parentCode().isBlank() ? alias.parentCode() : first.parentCode(), first.dimension(), first.weight(),
                first.ruleText().isBlank() ? alias.ruleText() : first.ruleText(), first.scoreType(), first.pageNumber(),
                first.tableId(), first.sourceCellIds(), first.quotedText(), Math.max(first.confidence(), alias.confidence()),
                first.childAggregation(), first.schemeId(), first.scopeStatus(), metadata);
    }
}
