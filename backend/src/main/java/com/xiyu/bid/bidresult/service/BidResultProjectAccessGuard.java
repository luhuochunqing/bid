package com.xiyu.bid.bidresult.service;

import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class BidResultProjectAccessGuard {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final ProjectAccessScopeService projectAccessScopeService;

    public void assertCanAccess(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
    }

    public <T> List<T> filterAccessible(List<T> items, Function<T, Long> projectIdResolver) {
        if (isCurrentUserAdmin()) {
            return items;
        }
        Set<Long> allowedProjectIds = new LinkedHashSet<>(projectAccessScopeService.getAllowedProjectIdsForCurrentUser());
        return items.stream()
                .filter(item -> allowedProjectIds.contains(projectIdResolver.apply(item)))
                .toList();
    }

    private boolean isCurrentUserAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .filter(Objects::nonNull)
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }
}
