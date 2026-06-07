package com.xiyu.bid.resources.service.expense;

import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseAccessGuardTest {

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    private ExpenseAccessGuard accessGuard;

    @BeforeEach
    void setUp() {
        accessGuard = new ExpenseAccessGuard(projectAccessScopeService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void visibleProjectIdsForCurrentUser_ShouldReturnNullForAdmin() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));

        assertThat(accessGuard.visibleProjectIdsForCurrentUser()).isNull();
    }

    @Test
    void visibleProjectIdsForCurrentUser_ShouldReturnScopeForNonAdmin() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "staff",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_STAFF"))
        ));
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(10L, 20L));

        assertThat(accessGuard.visibleProjectIdsForCurrentUser()).containsExactly(10L, 20L);
    }

    @Test
    void assertCanAccessProject_ShouldDelegateToSharedScopeService() {
        accessGuard.assertCanAccessProject(12L);

        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(12L);
    }
}
