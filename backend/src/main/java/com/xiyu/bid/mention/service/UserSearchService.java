// Input: search query + optional limit from controller
// Output: List<UserSearchResult> projection (id, name, employeeNumber, role, departmentName, roleCode)
// Pos: Service/用户搜索服务（只读，DTO 投影）
package com.xiyu.bid.mention.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.mention.dto.UserSearchResult;
import com.xiyu.bid.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserSearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_QUERY_LENGTH = 100;

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
                u.getDisplayEmployeeNumber(),
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
