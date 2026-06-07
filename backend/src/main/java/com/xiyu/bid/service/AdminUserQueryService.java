package com.xiyu.bid.service;

import com.xiyu.bid.dto.AdminUserDTO;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 用户查询服务（分页查询、DTO 转换）。
 * <p>从 AdminUserService 拆分而来，满足行数门禁。
 * <p>副作用层：仅做查询和转换。
 */
@Service
@Transactional(readOnly = true)
public class AdminUserQueryService {

    private final UserRepository userRepository;

    public AdminUserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 分页查询用户列表。
     */
    public PaginatedResult<AdminUserDTO> listUsersPage(int page, int size, String keyword,
                                                        Boolean enabled, String departmentCode) {
        java.util.stream.Stream<User> stream = userRepository.findAll().stream();

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim().toLowerCase(Locale.ROOT);
            stream = stream.filter(u ->
                u.getFullName() != null && u.getFullName().toLowerCase(Locale.ROOT).contains(kw)
                || u.getUsername() != null && u.getUsername().toLowerCase(Locale.ROOT).contains(kw)
                || u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).contains(kw)
                || u.getPhone() != null && u.getPhone().contains(kw)
            );
        }
        if (enabled != null) {
            stream = stream.filter(u -> Objects.equals(u.getEnabled(), enabled));
        }
        if (departmentCode != null && !departmentCode.isBlank()) {
            stream = stream.filter(u -> departmentCode.equals(u.getDepartmentCode()));
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

    public AdminUserDTO toDto(User user) {
        return AdminUserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .departmentCode(user.getDepartmentCode())
                .departmentName(user.getDepartmentName())
                .roleId(user.getRoleProfile() == null ? null : user.getRoleProfile().getId())
                .roleCode(user.getRoleCode())
                .roleName(user.getRoleName())
                .enabled(Boolean.TRUE.equals(user.getEnabled()))
                .externalOrgUserId(user.getExternalOrgUserId())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
