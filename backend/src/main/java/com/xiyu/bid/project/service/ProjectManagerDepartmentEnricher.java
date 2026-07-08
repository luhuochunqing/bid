package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 构建项目 managerId → departmentName 映射。
 *
 * <p>生产环境 users.department_name 多为空字符串，但 users.department_code 存的是 OSS external_dept_id
 * （见 OrganizationDirectoryJsonMapper.user() 读取 "deptCode"/"deptId"）。
 * 因此通过 organization_departments.external_dept_id 批量反查部门名。</p>
 */
@Component
@RequiredArgsConstructor
public class ProjectManagerDepartmentEnricher {

    /** OSS 同步用户的 department_code 实际存的是 OSS external_dept_id。 */
    private static final String OSS_SOURCE_APP = "oss";

    private final OrganizationDepartmentRepository organizationDepartmentRepository;

    /**
     * 构建 managerId → departmentName 映射。
     *
     * @param managerIds 项目 managerId 集合
     * @param userMap    userId → User 映射（由调用方批量查询后传入）
     * @return managerId → departmentName 映射；查不到的不放入（调用方用 getOrDefault 处理）
     */
    public Map<Long, String> buildManagerDepartmentMap(
            Set<Long> managerIds, Map<Long, User> userMap) {
        if (managerIds.isEmpty()) {
            return new HashMap<>();
        }
        // userId → user.departmentCode（实际是 OSS external_dept_id）
        Map<Long, String> userIdToDeptCode = new HashMap<>(managerIds.size());
        managerIds.forEach(id -> {
            User user = userMap.get(id);
            if (user != null && StringUtils.isNotBlank(user.getDepartmentCode())) {
                userIdToDeptCode.put(id, user.getDepartmentCode());
            }
        });
        if (userIdToDeptCode.isEmpty()) {
            return new HashMap<>();
        }
        // 批量查 organization_departments
        Set<String> externalDeptIds = new java.util.HashSet<>(userIdToDeptCode.values());
        Map<String, String> externalDeptIdToName = organizationDepartmentRepository
                .findBySourceAppAndExternalDeptIdIn(OSS_SOURCE_APP, externalDeptIds).stream()
                .collect(Collectors.toMap(
                        OrganizationDepartmentEntity::getExternalDeptId,
                        OrganizationDepartmentEntity::getDepartmentName,
                        (a, b) -> a));
        // 构建 userId → departmentName 映射
        Map<Long, String> result = new HashMap<>(managerIds.size());
        userIdToDeptCode.forEach((userId, deptCode) ->
                result.put(userId, externalDeptIdToName.get(deptCode)));
        return result;
    }
}
