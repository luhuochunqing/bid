package com.xiyu.bid.integration.organization.domain.policy;

import com.xiyu.bid.integration.organization.dto.OssMenuTreeNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OssMenuPermissionMapper - OSS 菜单编码到内部权限码映射")
class OssMenuPermissionMapperTest {

    @Test
    @DisplayName("按配置映射菜单编码到内部权限码")
    void map_withConfiguredMappings_returnsMappedPermissions() {
        OssMenuPermissionMapper mapper = new OssMenuPermissionMapper(
                Map.of("projectmanager", "project.manager", "bidding", "bidding"),
                "IGNORE"
        );
        List<OssMenuTreeNode> tree = List.of(node("projectmanager", List.of(node("bidding", List.of()))));

        Set<String> result = mapper.map(tree);

        assertThat(result).containsExactlyInAnyOrder("project.manager", "bidding");
    }

    @Test
    @DisplayName("映射大小写不敏感")
    void map_caseInsensitiveMapping_works() {
        OssMenuPermissionMapper mapper = new OssMenuPermissionMapper(
                Map.of("ProjectManager", "project.manager"),
                "IGNORE"
        );
        List<OssMenuTreeNode> tree = List.of(node("PROJECTMANAGER", List.of()));

        Set<String> result = mapper.map(tree);

        assertThat(result).containsExactly("project.manager");
    }

    @Test
    @DisplayName("未映射编码默认被忽略")
    void map_unmappedCodeWithIgnoreBehavior_ignored() {
        OssMenuPermissionMapper mapper = new OssMenuPermissionMapper(Map.of(), "IGNORE");
        List<OssMenuTreeNode> tree = List.of(node("unknown", List.of()));

        Set<String> result = mapper.map(tree);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("未映射编码可配置为使用规范化编码")
    void map_unmappedCodeWithUseNormalizedCode_returnsNormalizedCode() {
        OssMenuPermissionMapper mapper = new OssMenuPermissionMapper(Map.of(), "USE_NORMALIZED_CODE");
        List<OssMenuTreeNode> tree = List.of(node("Unknown_Code ", List.of()));

        Set<String> result = mapper.map(tree);

        assertThat(result).containsExactly("unknown_code");
    }

    @Test
    @DisplayName("空菜单树返回空集合")
    void map_emptyTree_returnsEmptySet() {
        OssMenuPermissionMapper mapper = new OssMenuPermissionMapper(Map.of(), "IGNORE");

        Set<String> result = mapper.map(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("递归遍历子节点")
    void map_nestedChildren_traversesAll() {
        OssMenuPermissionMapper mapper = new OssMenuPermissionMapper(
                Map.of("child", "child.permission"),
                "IGNORE"
        );
        List<OssMenuTreeNode> tree = List.of(
                node("root", List.of(node("child", List.of(node("grandchild", List.of())))))
        );

        Set<String> result = mapper.map(tree);

        assertThat(result).containsExactly("child.permission");
    }

    @Test
    @DisplayName("按真实 OSS 数字二级菜单编码映射")
    void map_numericSecondLevelMenuCode_returnsInternalPermissionKeys() {
        OssMenuPermissionMapper mapper = new OssMenuPermissionMapper(
                Map.of("1002", "bidding", "100201", "bidding-list"),
                "IGNORE"
        );
        List<OssMenuTreeNode> tree = List.of(node("1002", List.of(node("100201", List.of()))));

        Set<String> result = mapper.map(tree);

        assertThat(result).containsExactlyInAnyOrder("bidding", "bidding-list");
    }

    @Test
    @DisplayName("多值映射：一个 OSS 菜单码映射到多个内部权限键（逗号分隔）")
    void map_multiValueMapping_returnsAllPermissions() {
        OssMenuPermissionMapper mapper = new OssMenuPermissionMapper(
                Map.of("100402", "knowledge-qualification,qualification.manage,qualification.view"),
                "IGNORE"
        );
        List<OssMenuTreeNode> tree = List.of(node("100402", List.of()));

        Set<String> result = mapper.map(tree);

        assertThat(result).containsExactlyInAnyOrder(
                "knowledge-qualification", "qualification.manage", "qualification.view");
    }

    @Test
    @DisplayName("多值映射：mapCodes 也支持逗号分隔多值")
    void mapCodes_multiValueMapping_returnsAllPermissions() {
        OssMenuPermissionMapper mapper = new OssMenuPermissionMapper(
                Map.of("100408", "knowledge-warehouse,warehouse.manage"),
                "IGNORE"
        );

        Set<String> result = mapper.mapCodes(List.of("100408"));

        assertThat(result).containsExactlyInAnyOrder("knowledge-warehouse", "warehouse.manage");
    }

    @Test
    @DisplayName("多值映射：空值和空白值被过滤")
    void map_multiValueMappingWithBlankParts_filtersBlanks() {
        OssMenuPermissionMapper mapper = new OssMenuPermissionMapper(
                Map.of("100402", "knowledge-qualification,, ,qualification.manage"),
                "IGNORE"
        );
        List<OssMenuTreeNode> tree = List.of(node("100402", List.of()));

        Set<String> result = mapper.map(tree);

        assertThat(result).containsExactlyInAnyOrder("knowledge-qualification", "qualification.manage");
    }

    private OssMenuTreeNode node(String menuCode, List<OssMenuTreeNode> children) {
        return new OssMenuTreeNode(
                null, menuCode, null, null, null, null,
                null, null, null, null, null, children,
                null, null, null, null, null, null,
                null, null, null, null
        );
    }
}
