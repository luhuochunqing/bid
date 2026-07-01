package com.xiyu.bid.service;

import com.xiyu.bid.dto.AdminUserDTO;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import com.xiyu.bid.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 用户查询服务（分页查询、DTO 转换）。
 * <p>从 AdminUserService 拆分而来，满足行数门禁。
 * <p>副作用层：仅做查询和转换。
 */
@Service
@Transactional(readOnly = true)
public class AdminUserQueryService {

    private final UserRepository userRepository;
    private final OrganizationDepartmentRepository departmentRepository;

    public AdminUserQueryService(UserRepository userRepository,
                                 OrganizationDepartmentRepository departmentRepository) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    /**
     * 分页查询用户列表。
     * <p>用户表 department_code 实际存储的是组织架构 external_dept_id，
     * 因此按部门编码过滤时需要先反查组织部门表得到 external_dept_id。
     */
    public PaginatedResult<AdminUserDTO> listUsersPage(int page, int size, String keyword,
                                                        Boolean enabled, String departmentCode,
                                                        String sourceApp) {
        Stream<User> stream = userRepository.findAll().stream();

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim().toLowerCase(Locale.ROOT);
            stream = stream.filter(u ->
                u.getFullName() != null && u.getFullName().toLowerCase(Locale.ROOT).contains(kw)
                || u.getUsername() != null && u.getUsername().toLowerCase(Locale.ROOT).contains(kw)
                || u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).contains(kw)
                || u.getPhone() != null && u.getPhone().contains(kw)
                || u.getEmployeeNumber() != null && u.getEmployeeNumber().toLowerCase(Locale.ROOT).contains(kw)
            );
        }
        if (enabled != null) {
            stream = stream.filter(u -> Objects.equals(u.getEnabled(), enabled));
        }
        if (departmentCode != null && !departmentCode.isBlank()) {
            Set<String> externalDeptIds = resolveExternalDeptIds(departmentCode, sourceApp);
            if (externalDeptIds.isEmpty()) {
                return new PaginatedResult<>(Collections.emptyList(), 0, page, size);
            }
            stream = stream.filter(u -> externalDeptIds.contains(u.getDepartmentCode()));
        }

        List<AdminUserDTO> all = stream
                .sorted((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.getUsername(), right.getUsername()))
                .map(this::toDto)
                .toList();

        int total = all.size();
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        int startIndex = (page - 1) * size;
        int endIndex = Math.min(startIndex + size, total);
        List<AdminUserDTO> list = startIndex >= total ? Collections.emptyList() : all.subList(startIndex, endIndex);
        return new PaginatedResult<>(list, total, page, size);
    }

    private Set<String> resolveExternalDeptIds(String departmentCode, String sourceApp) {
        if (sourceApp != null && !sourceApp.isBlank()) {
            Optional<OrganizationDepartmentEntity> target = departmentRepository
                    .findBySourceAppAndDepartmentCode(sourceApp, departmentCode);
            if (target.isEmpty()) {
                return Collections.emptySet();
            }
            List<OrganizationDepartmentEntity> all = departmentRepository
                    .findBySourceAppAndEnabledTrueOrderByDepartmentCode(sourceApp);
            Map<String, List<OrganizationDepartmentEntity>> byParent = all.stream()
                    .filter(d -> d.getParentDepartmentCode() != null && !d.getParentDepartmentCode().isBlank())
                    .collect(Collectors.groupingBy(OrganizationDepartmentEntity::getParentDepartmentCode));
            Set<String> result = new HashSet<>();
            collectDescendants(target.get(), byParent, result, new HashSet<>(), 0);
            return result;
        }
        return departmentRepository.findAll().stream()
                .filter(d -> departmentCode.equals(d.getDepartmentCode()))
                .map(OrganizationDepartmentEntity::getExternalDeptId)
                .collect(Collectors.toSet());
    }

    private void collectDescendants(OrganizationDepartmentEntity current,
                                    Map<String, List<OrganizationDepartmentEntity>> byParent,
                                    Set<String> result,
                                    Set<String> visited,
                                    int depth) {
        if (depth > 20 || current == null || !visited.add(current.getDepartmentCode())) {
            return;
        }
        if (current.getExternalDeptId() != null) {
            result.add(current.getExternalDeptId());
        }
        List<OrganizationDepartmentEntity> children = byParent.getOrDefault(current.getDepartmentCode(), Collections.emptyList());
        for (OrganizationDepartmentEntity child : children) {
            collectDescendants(child, byParent, result, visited, depth + 1);
        }
    }

    public AdminUserDTO toDto(User user) {
        return AdminUserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .departmentCode(user.getDepartmentCode())
                .departmentName(user.getDepartmentName())
                .employeeNumber(user.getEmployeeNumber())
                .crmSalesNo(user.getCrmSalesNo())
                .roleId(user.getRoleProfile() == null ? null : user.getRoleProfile().getId())
                // SAFE: 管理员后台用户列表展示字段，列出所有用户的 DB 当前 roleCode 用于审计/管理；
                // 调用此 API 的已是 admin 管理员，鉴权在 controller 层完成。CO-373 治理范围外。
                .roleCode(user.getRoleCode())
                .roleName(user.getRoleName())
                .enabled(Boolean.TRUE.equals(user.getEnabled()))
                .externalOrgUserId(user.getExternalOrgUserId())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
