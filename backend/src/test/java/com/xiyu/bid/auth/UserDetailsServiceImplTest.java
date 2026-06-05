package com.xiyu.bid.auth;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void customRoleShouldRetainLegacyStaffAuthority() {
        User user = userWithRoleProfile("legal", User.Role.STAFF, "legal-reviewer");
        when(userRepository.findByUsername("legal")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("legal");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("ROLE_STAFF", "legal-reviewer", "ROLE_LEGAL-REVIEWER");
    }

    @Test
    void auditorRoleProfileShouldAddAuditorAuthorityWithoutChangingLegacyRole() {
        User user = userWithRoleProfile("auditor", User.Role.STAFF, RoleProfileCatalog.AUDITOR_CODE);
        when(userRepository.findByUsername("auditor")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("auditor");

        assertThat(details.getAuthorities())
                .extracting("authority")
                .contains("ROLE_STAFF", "ROLE_AUDITOR", "auditor", "ROLE_AUDITOR");
    }

    private User userWithRoleProfile(String username, User.Role role, String roleCode) {
        RoleProfile roleProfile = RoleProfile.builder()
                .code(roleCode)
                .name(roleCode)
                .build();
        return User.builder()
                .username(username)
                .password("{noop}password")
                .email(username + "@example.com")
                .fullName(username)
                .role(role)
                .roleProfile(roleProfile)
                .enabled(true)
                .build();
    }
}
