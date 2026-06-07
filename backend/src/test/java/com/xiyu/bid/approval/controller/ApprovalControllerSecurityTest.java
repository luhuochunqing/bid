package com.xiyu.bid.approval.controller;

import com.xiyu.bid.approval.service.ApprovalCurrentUserLookupService;
import com.xiyu.bid.approval.service.ApprovalWorkflowService;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ApprovalControllerSecurityTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void getUserIdFromDetails_ResolvesPersistedUserByUsername() throws Exception {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.withUsername("alice")
                .password("password")
                .authorities("ROLE_STAFF")
                .build();
        User user = User.builder()
                .id(42L)
                .username("alice")
                .role(User.Role.STAFF)
                .email("alice@example.com")
                .fullName("Alice")
                .password("secret")
                .enabled(true)
                .build();

        org.mockito.Mockito.when(userRepository.findByUsername("alice")).thenReturn(java.util.Optional.of(user));

        Method method = ApprovalController.class.getDeclaredMethod(
                "getUserIdFromDetails",
                UserDetails.class
        );
        method.setAccessible(true);

        ApprovalController controller = new ApprovalController((ApprovalWorkflowService) null, new ApprovalCurrentUserLookupService(userRepository));

        Long userId = (Long) method.invoke(controller, userDetails);

        assertThat(userId).isEqualTo(42L);
    }

    @Test
    void getUserIdFromDetails_RejectsUnknownAuthenticatedUser() throws Exception {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.withUsername("ghost")
                .password("password")
                .authorities("ROLE_STAFF")
                .build();

        org.mockito.Mockito.when(userRepository.findByUsername("ghost"))
                .thenReturn(java.util.Optional.empty());

        Method method = ApprovalController.class.getDeclaredMethod(
                "getUserIdFromDetails",
                UserDetails.class
        );
        method.setAccessible(true);

        ApprovalController controller = new ApprovalController((ApprovalWorkflowService) null, new ApprovalCurrentUserLookupService(userRepository));

        assertThatThrownBy(() -> method.invoke(controller, userDetails))
                .hasCauseExactlyInstanceOf(AuthenticationServiceException.class)
                .cause()
                .hasMessageContaining("Authenticated user not found");
    }
}
