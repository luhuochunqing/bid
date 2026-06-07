package com.xiyu.bid.resources.service.expense;

import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseAccessGuard {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final ProjectAccessScopeService projectAccessScopeService;

    public void assertCanAccessProject(Long projectId) {
        if (projectId != null) {
            projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        }
    }

    public List<Long> visibleProjectIdsForCurrentUser() {
        return isCurrentUserAdmin() ? null : projectAccessScopeService.getAllowedProjectIdsForCurrentUser();
    }

    private boolean isCurrentUserAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    }
}
