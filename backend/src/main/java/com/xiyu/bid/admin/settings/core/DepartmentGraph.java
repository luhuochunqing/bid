package com.xiyu.bid.admin.settings.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DepartmentGraph(Map<String, DepartmentNode> definitions, List<DepartmentOption> options, List<DepartmentNode> tree) {
    public DepartmentGraph {
        definitions = definitions == null ? Map.of() : Map.copyOf(definitions);
        options = options == null ? List.of() : List.copyOf(options);
        tree = tree == null ? List.of() : List.copyOf(tree);
    }

    public List<String> descendantsOf(String rootCode) {
        if (rootCode == null || !definitions.containsKey(rootCode)) {
            return List.of();
        }
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(rootCode);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            definitions.values().stream()
                    .filter(definition -> Objects.equals(definition.parentCode(), current))
                    .map(DepartmentNode::code)
                    .forEach(queue::addLast);
        }
        return List.copyOf(visited);
    }
}
