package com.xiyu.bid.architecture;

import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 架构测试守卫：校验后端所有 {@code @PreAuthorize("hasAuthority('xxx')")} 使用的权限键
 * 必须在 OSS 菜单映射表（application.yml: xiyu.integrations.organization.directory.menu-code-to-permission-key-mappings）
 * 中有来源。
 *
 * <p>背景：spec 032 修复 OSS 权限扩散后，OSS 用户的 authorities 严格等于 OSS 菜单码映射出的内部权限键。
 * 如果后端 {@code @PreAuthorize(hasAuthority('xxx'))} 使用的权限键在 OSS 映射表中没有来源，
 * OSS 用户会一律被 403 拒绝（即使本地 catalog 中有该权限键）。
 *
 * <p>本测试是 CO-560 的防复发守卫：CO-560 修复了 {@code retrospective.submit} 缺失映射导致
 * OSS 用户提交复盘全部 403 的问题。本测试确保未来新增 {@code @PreAuthorize(hasAuthority('xxx'))}
 * 时，权限键 xxx 必须同步在 OSS 映射表中配置来源，否则架构测试失败。
 *
 * <p>排除项：大写的 SCOPE_/CLAIM_/ASSIGN_/DELETE_/UPDATE_/PAY_ 类型权限键不走 OSS 映射
 * （这些是内部 SCOPE 权限，由 OAuth2/客户端凭证授予，不经 OSS 菜单）。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("PreAuthorize hasAuthority 权限键 OSS 映射覆盖守卫")
class PreAuthorizeAuthorityOssMappingCoverageTest {

    /**
     * 排除的权限键前缀：这些是内部 SCOPE/批量操作权限键，不走 OSS 菜单映射。
     * 大写命名约定区分 OSS 菜单映射权限键（小写、点号分隔）与内部 SCOPE 权限键（大写、下划线分隔）。
     */
    private static final Set<String> EXCLUDED_AUTHORITY_PREFIXES = Set.of(
            "SCOPE_", "CLAIM_", "ASSIGN_", "DELETE_", "UPDATE_", "PAY_"
    );

    /**
     * 匹配 @PreAuthorize 注解中的 hasAuthority('xxx') 或 hasAuthority("xxx") 调用。
     * 捕获组 1 是权限键 xxx。
     */
    private static final Pattern HAS_AUTHORITY_PATTERN = Pattern.compile(
            "hasAuthority\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");

    @Autowired
    private OrganizationIntegrationProperties organizationIntegrationProperties;

    @Test
    @DisplayName("所有 @PreAuthorize(hasAuthority('xxx')) 的权限键必须在 OSS 菜单映射表中有来源")
    void allHasAuthorityKeys_mustHaveOssMappingSource() {
        // 1. 扫描 com.xiyu.bid 包下所有 Controller 的 @PreAuthorize 注解
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages("com.xiyu.bid");

        List<AuthorityUsage> usages = new ArrayList<>();
        classes.stream()
                .flatMap(clazz -> clazz.getMethods().stream())
                .forEach(method -> collectHasAuthorityUsages(method, usages));

        // 2. 收集 OSS 映射表中所有已映射的内部权限键
        Set<String> mappedKeys = new HashSet<>();
        organizationIntegrationProperties.getDirectory()
                .getMenuCodeToPermissionKeyMappings()
                .values()
                .forEach(mappedKeys::addAll);

        // 3. catalog fallback 权限键（本地系统账号专用，OSS 用户不走此路径，但本地 admin 通过扩散持有）
        // 排除 "all"（已被 RoleProfileAdminPermissionFilter 过滤，OSS 用户不持有）
        // 排除 ROLE_ 前缀（这是 Spring Security role authority，不是 permission key）

        // 4. 找出缺失映射的权限键
        Set<String> missingKeys = usages.stream()
                .map(AuthorityUsage::authorityKey)
                .filter(key -> !isExcludedInternalScope(key))
                .filter(key -> !mappedKeys.contains(key))
                .collect(Collectors.toCollection(HashSet::new));

        assertThat(missingKeys)
                .as("以下 @PreAuthorize(hasAuthority('xxx')) 权限键在 OSS 菜单映射表中没有来源，" +
                        "OSS 用户会被 403 拒绝。请在 application.yml " +
                        "xiyu.integrations.organization.directory.menu-code-to-permission-key-mappings " +
                        "中为对应 OSS 菜单码追加此权限键映射: %s", missingKeys)
                .isEmpty();
    }

    private void collectHasAuthorityUsages(JavaMethod method, List<AuthorityUsage> usages) {
        for (JavaAnnotation<?> annotation : method.getAnnotations()) {
            String annotationTypeName = annotation.getRawType().getName();
            if (!annotationTypeName.endsWith("PreAuthorize")) {
                continue;
            }
            Object value = annotation.get("value").orElse(null);
            if (value == null) {
                continue;
            }
            String expression = value.toString();
            Matcher matcher = HAS_AUTHORITY_PATTERN.matcher(expression);
            while (matcher.find()) {
                usages.add(new AuthorityUsage(
                        method.getOwner().getName(),
                        method.getName(),
                        matcher.group(1)
                ));
            }
        }
    }

    private boolean isExcludedInternalScope(String key) {
        return EXCLUDED_AUTHORITY_PREFIXES.stream().anyMatch(key::startsWith);
    }

    private record AuthorityUsage(String className, String methodName, String authorityKey) {
    }
}
