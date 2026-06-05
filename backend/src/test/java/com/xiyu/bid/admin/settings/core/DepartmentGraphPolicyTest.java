package com.xiyu.bid.admin.settings.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class DepartmentGraphPolicyTest {

    @Test
    void buildGraph_ShouldNormalizeTreeAndExpandDescendants() {
        DepartmentGraph graph = DepartmentGraphPolicy.buildGraph(List.of(
                new DepartmentNode("SALES", "销售部", null, 1),
                new DepartmentNode("SALES_EAST", "华东销售", "SALES", 2)
        ));

        assertThat(graph.options()).extracting(DepartmentOption::code).containsExactly("SALES", "SALES_EAST");
        assertThat(graph.descendantsOf("SALES")).containsExactly("SALES", "SALES_EAST");
    }

    @Test
    void validateTree_ShouldRejectDuplicateCodeSelfParentAndCycles() {
        assertThat(DepartmentGraphPolicy.validateTree(List.of(
                new DepartmentNode("SALES", "销售部", null, 1),
                new DepartmentNode("SALES", "销售一部", null, 2)
        )).valid()).isFalse();

        assertThat(DepartmentGraphPolicy.validateTree(List.of(
                new DepartmentNode("SALES", "销售部", "SALES", 1)
        )).valid()).isFalse();

        assertThat(DepartmentGraphPolicy.validateTree(List.of(
                new DepartmentNode("A", "A部", "B", 1),
                new DepartmentNode("B", "B部", "A", 2)
        )).valid()).isFalse();
    }

    @Test
    void findRemovedBoundDepartments_ShouldReturnDepartmentsStillAssignedToUsers() {
        List<DepartmentNode> nextTree = List.of(new DepartmentNode("SALES", "销售部", null, 1));

        assertThat(DepartmentGraphPolicy.findRemovedBoundDepartments(nextTree, Set.of("SALES", "TECH")))
                .containsExactly("TECH");
    }

    @Test
    void departmentGraph_ShouldDefensivelyCopyMutableCollections() {
        LinkedHashMap<String, DepartmentNode> definitions = new LinkedHashMap<>();
        definitions.put("SALES", new DepartmentNode("SALES", "销售部", null, 1));
        ArrayList<DepartmentOption> options = new ArrayList<>(List.of(new DepartmentOption("SALES", "销售部")));
        ArrayList<DepartmentNode> tree = new ArrayList<>(List.of(new DepartmentNode("SALES", "销售部", null, 1)));

        DepartmentGraph graph = new DepartmentGraph(definitions, options, tree);

        definitions.put("TECH", new DepartmentNode("TECH", "技术部", "SALES", 2));
        options.add(new DepartmentOption("TECH", "技术部"));
        tree.add(new DepartmentNode("TECH", "技术部", "SALES", 2));

        assertThat(graph.definitions()).hasSize(1);
        assertThat(graph.options()).hasSize(1);
        assertThat(graph.tree()).hasSize(1);

        assertThatThrownBy(() -> graph.definitions().put("OPS", new DepartmentNode("OPS", "运维部", null, 3)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> graph.options().add(new DepartmentOption("OPS", "运维部")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> graph.tree().add(new DepartmentNode("OPS", "运维部", null, 3)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
