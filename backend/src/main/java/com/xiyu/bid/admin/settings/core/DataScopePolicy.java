package com.xiyu.bid.admin.settings.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class DataScopePolicy {
    private static final String DEFAULT_SCOPE = "self";
    private static final Set<String> ALLOWED_SCOPES = Set.of("all", "dept", "deptAndSub", "self");

    private DataScopePolicy() {
    }

    public static CoreAccessProfile resolveAccessProfile(
            UserAccessSubject subject,
            List<UserScopeRule> userRules,
            List<DepartmentScopeRule> departmentRules,
            RoleAccessRule roleRule,
            DepartmentGraph departmentGraph
    ) {
        if (subject == null) {
            return CoreAccessProfile.empty();
        }
        String ownDeptCode = DepartmentGraphPolicy.normalizeCode(subject.departmentCode());
        UserScopeRule userRule = firstUserRule(userRules, subject.userId());
        DepartmentScopeRule departmentRule = firstDepartmentRule(departmentRules, ownDeptCode);
        String scope = normalizeScope(userRule != null
                ? userRule.dataScope()
                : departmentRule != null
                ? departmentRule.dataScope()
                : roleRule == null ? null : roleRule.dataScope());
        List<Long> projectIds = normalizeProjectIds(userRule != null
                ? userRule.allowedProjectIds()
                : roleRule == null ? List.of() : roleRule.allowedProjectIds());
        List<String> explicitDeptCodes = normalizeDeptCodes(userRule != null
                ? userRule.allowedDeptCodes()
                : departmentRule != null
                ? departmentRule.allowedDeptCodes()
                : roleRule == null ? List.of() : roleRule.allowedDeptCodes());

        if ("all".equals(scope)) {
            return new CoreAccessProfile(scope, projectIds, departmentGraph.options().stream().map(DepartmentOption::code).toList());
        }

        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        if ("dept".equals(scope) && !DepartmentGraphPolicy.UNASSIGNED_DEPT_CODE.equals(ownDeptCode)) {
            allowed.add(ownDeptCode);
        }
        if ("deptAndSub".equals(scope) && !DepartmentGraphPolicy.UNASSIGNED_DEPT_CODE.equals(ownDeptCode)) {
            allowed.addAll(departmentGraph.descendantsOf(ownDeptCode));
        }
        if (!"self".equals(scope)) {
            allowed.addAll(explicitDeptCodes);
        }
        return new CoreAccessProfile(scope, projectIds, List.copyOf(allowed));
    }

    public static String normalizeScope(String scope) {
        String candidate = scope == null ? DEFAULT_SCOPE : scope.trim();
        return ALLOWED_SCOPES.contains(candidate) ? candidate : DEFAULT_SCOPE;
    }

    public static List<Long> normalizeProjectIds(List<Long> projectIds) {
        if (projectIds == null) {
            return List.of();
        }
        return projectIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }

    public static List<String> normalizeDeptCodes(List<String> deptCodes) {
        if (deptCodes == null) {
            return List.of();
        }
        return deptCodes.stream()
                .filter(Objects::nonNull)
                .map(DepartmentGraphPolicy::normalizeCode)
                .distinct()
                .toList();
    }

    private static UserScopeRule firstUserRule(List<UserScopeRule> rules, Long userId) {
        return rules == null ? null : rules.stream()
                .filter(rule -> Objects.equals(rule.userId(), userId))
                .findFirst()
                .orElse(null);
    }

    private static DepartmentScopeRule firstDepartmentRule(List<DepartmentScopeRule> rules, String deptCode) {
        return rules == null ? null : rules.stream()
                .filter(rule -> Objects.equals(DepartmentGraphPolicy.normalizeCode(rule.departmentCode()), deptCode))
                .findFirst()
                .orElse(null);
    }
}
