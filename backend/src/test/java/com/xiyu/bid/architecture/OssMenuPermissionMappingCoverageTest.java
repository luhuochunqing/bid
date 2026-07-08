package com.xiyu.bid.architecture;

import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.systems.external.ExternalMenuService;
import com.xiyu.bid.systems.external.ExternalMenuTreeNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 架构测试：校验 OSS 菜单定义与权限映射表的覆盖一致性。
 *
 * <p>ExternalMenuService 是 OSS 可配置菜单的唯一源，本测试确保每个 menuCode
 * 在 application.yml 的映射表中都有定义，避免新增菜单后 OSS 用户拿到菜单但后端 403。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("OSS 菜单权限映射覆盖性架构测试")
class OssMenuPermissionMappingCoverageTest {

    @Autowired
    private ExternalMenuService externalMenuService;

    @Autowired
    private OrganizationIntegrationProperties organizationIntegrationProperties;

    @Test
    @DisplayName("ExternalMenuService 中所有 OSS 菜单码都必须在映射表中有定义")
    void allExternalMenuCodes_haveMappingEntry() {
        Set<String> menuCodes = collectMenuCodes(externalMenuService.getMenuList());
        Map<String, List<String>> mappings = organizationIntegrationProperties.getDirectory()
                .getMenuCodeToPermissionKeyMappings();

        Set<String> mappedCodes = mappings.keySet().stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        Set<String> unmappedCodes = menuCodes.stream()
                .filter(code -> !mappedCodes.contains(code))
                .collect(Collectors.toSet());

        assertThat(unmappedCodes)
                .as("ExternalMenuService 中有 %d 个菜单码未在 application.yml 映射表中定义: %s",
                        unmappedCodes.size(), unmappedCodes)
                .isEmpty();
    }

    @Test
    @DisplayName("映射表中的每个 OSS 菜单码至少映射到一个内部权限键")
    void allMappedCodes_haveNonEmptyPermissions() {
        Map<String, List<String>> mappings = organizationIntegrationProperties.getDirectory()
                .getMenuCodeToPermissionKeyMappings();

        List<String> emptyMappings = mappings.entrySet().stream()
                .filter(e -> e.getValue() == null || e.getValue().stream().allMatch(String::isBlank))
                .map(Map.Entry::getKey)
                .toList();

        assertThat(emptyMappings)
                .as("以下 OSS 菜单码映射到了空的权限列表: %s", emptyMappings)
                .isEmpty();
    }

    private Set<String> collectMenuCodes(List<ExternalMenuTreeNode> nodes) {
        Set<String> codes = new HashSet<>();
        if (nodes == null) {
            return codes;
        }
        List<ExternalMenuTreeNode> stack = new ArrayList<>(nodes);
        while (!stack.isEmpty()) {
            ExternalMenuTreeNode node = stack.removeLast();
            if (node == null) {
                continue;
            }
            if (node.getMenuCode() != null && !node.getMenuCode().isBlank()) {
                codes.add(node.getMenuCode().trim().toLowerCase());
            }
            if (node.getChildren() != null) {
                stack.addAll(node.getChildren());
            }
        }
        return codes;
    }
}
