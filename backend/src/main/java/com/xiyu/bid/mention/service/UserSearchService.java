// Input: search query + optional limit from controller
// Output: List<UserSearchResult> projection (id, name, employeeNumber, role, departmentName, roleCode)
// Pos: Service/用户搜索服务（只读，DTO 投影）
package com.xiyu.bid.mention.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.mention.dto.UserSearchResult;
import com.xiyu.bid.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserSearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_QUERY_LENGTH = 100;
    /** CO-384: 批量按 ID 查询用户上限，防止滥用。 */
    private static final int MAX_BATCH_SIZE = 100;

    private final UserRepository userRepository;

    public UserSearchService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserSearchResult> search(String query, Integer limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String trimmed = query.trim();
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_QUERY_LENGTH);
        }
        String escaped = escapeLike(trimmed);
        int safeLimit = clampLimit(limit);
        return userRepository.searchActiveUsers(escaped, safeLimit).stream()
            .map(u -> new UserSearchResult(
                u.getId(),
                u.getFullName(),
                employeeNumberOrUsername(u),
                u.getRole() == null ? null : u.getRole().name(),
                u.getDepartmentName(),
                u.getRoleCode()))
            .toList();
    }

    /**
     * Historical organization-sync data may store the visible job number in username
     * while employee_number is blank. Keep this as a search-display compatibility
     * fallback; the long-term source of truth should be employee_number backfill.
     */
    private static String employeeNumberOrUsername(User user) {
        if (user.getEmployeeNumber() != null && !user.getEmployeeNumber().isBlank()) {
            return user.getEmployeeNumber();
        }
        return user.getUsername();
    }

    /**
     * CO-384: 批量按 ID 查询用户，返回与 search 相同的 UserSearchResult 投影。
     * 用于前端补查已分配人员姓名（如立项审批后的投标辅助人员回显）。
     */
    public List<UserSearchResult> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> safeIds = ids.stream().filter(java.util.Objects::nonNull).limit(MAX_BATCH_SIZE).toList();
        if (safeIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findByIdIn(safeIds).stream()
            .map(u -> new UserSearchResult(
                u.getId(),
                u.getFullName(),
                employeeNumberOrUsername(u),
                u.getRole() == null ? null : u.getRole().name(),
                u.getDepartmentName(),
                u.getRoleCode()))
            .toList();
    }

    private static String escapeLike(String raw) {
        return raw
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }

    private static int clampLimit(Integer limit) {
        int requested = limit == null ? DEFAULT_LIMIT : Math.max(1, limit);
        return Math.min(requested, MAX_LIMIT);
    }
}
