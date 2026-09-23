package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Single pure projection of score facts into the values that contribute to a scheme total. */
public final class ScoreUnitPolicy {
    private ScoreUnitPolicy() {}

    public static Set<String> effectiveUnitIds(List<Node> facts) {
        List<Node> safeFacts = facts == null ? List.of() : List.copyOf(facts);
        Map<String, List<Node>> children = new HashMap<>();
        Map<String, Node> byId = new HashMap<>();
        for (Node fact : safeFacts) {
            byId.put(fact.id(), fact);
            if (fact.parentId() != null && !fact.parentId().isBlank()) {
                children.computeIfAbsent(fact.parentId(), ignored -> new ArrayList<>()).add(fact);
            }
        }
        Set<String> units = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        safeFacts.stream().filter(fact -> fact.parentId() == null || fact.parentId().isBlank()
                || !byId.containsKey(fact.parentId())).forEach(root -> visit(root, children, units, visiting));
        return Set.copyOf(units);
    }

    public static BigDecimal totalWeight(List<Node> facts) {
        Set<String> units = effectiveUnitIds(facts);
        if (facts == null) return BigDecimal.ZERO;
        return facts.stream().filter(fact -> units.contains(fact.id())).map(Node::weight)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static List<ScoreFact> effectiveFacts(List<ScoreFact> facts) {
        var nodes = facts.stream().map(fact -> new Node(fact.factId(), fact.parentFactId(), fact.weight(),
                fact.childAggregation().name())).toList();
        Set<String> effectiveIds = effectiveUnitIds(nodes);
        return facts.stream().filter(fact -> effectiveIds.contains(fact.factId())).toList();
    }

    public static boolean hasUnknownRelation(List<Node> facts) {
        if (facts == null) return false;
        Map<String, Long> childCounts = facts.stream().filter(f -> f.parentId() != null && !f.parentId().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(Node::parentId, java.util.stream.Collectors.counting()));
        return facts.stream().anyMatch(fact -> childCounts.containsKey(fact.id())
                && ChildAggregation.legacyDefault(fact.childAggregation()) == ChildAggregation.UNKNOWN);
    }

    public static boolean hasUnknownFactRelation(List<ScoreFact> facts) {
        return hasUnknownRelation(facts.stream().map(fact -> new Node(fact.factId(), fact.parentFactId(),
                fact.weight(), fact.childAggregation().name())).toList());
    }

    private static void visit(Node fact, Map<String, List<Node>> children, Set<String> units, Set<String> visiting) {
        if (!visiting.add(fact.id())) return;
        List<Node> directChildren = children.getOrDefault(fact.id(), List.of());
        ChildAggregation relation = ChildAggregation.legacyDefault(fact.childAggregation());
        if (directChildren.isEmpty()) {
            units.add(fact.id());
            return;
        }
        if (relation == ChildAggregation.UNKNOWN) return;
        if (relation != ChildAggregation.SUM || directChildren.isEmpty()) {
            units.add(fact.id());
            return;
        }
        directChildren.forEach(child -> visit(child, children, units, visiting));
    }

    public record Node(String id, String parentId, BigDecimal weight, String childAggregation) {
        public Node {
            id = id == null ? "" : id;
            parentId = parentId == null ? "" : parentId;
        }
    }
}
