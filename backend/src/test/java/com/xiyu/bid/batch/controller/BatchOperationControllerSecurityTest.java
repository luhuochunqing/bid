package com.xiyu.bid.batch.controller;

import com.xiyu.bid.batch.dto.BatchClaimRequest;
import com.xiyu.bid.batch.dto.BatchOperationResponse;
import com.xiyu.bid.batch.service.BatchOperationService;
import com.xiyu.bid.batch.service.BatchTenderAssignmentService;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchOperationControllerSecurityTest {

    @Mock
    private BatchOperationService batchOperationService;

    @Mock
    private BatchTenderAssignmentService batchTenderAssignmentService;

    @Mock
    private AuthService authService;

    @Test
    void batchClaimTenders_IgnoresRequestUserIdAndUsesAuthenticatedUser() {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.withUsername("manager")
                .password("password")
                .authorities("ROLE_MANAGER")
                .build();
        User currentUser = User.builder()
                .id(42L)
                .username("manager")
                .role(User.Role.MANAGER)
                .email("manager@example.com")
                .fullName("Manager")
                .password("secret")
                .enabled(true)
                .build();
        BatchClaimRequest request = new BatchClaimRequest(List.of(1L, 2L), 999L, "tender");

        when(authService.resolveUserByUsername("manager")).thenReturn(currentUser);
        when(batchOperationService.batchClaimTenders(eq(List.of(1L, 2L)), eq(42L)))
                .thenReturn(BatchOperationResponse.builder()
                        .success(true)
                        .successCount(2)
                        .failureCount(0)
                        .totalCount(2)
                        .operationType("CLAIM")
                        .build());

        BatchOperationController controller = new BatchOperationController(
                batchOperationService,
                batchTenderAssignmentService,
                authService
        );

        var response = controller.batchClaimTenders(request, userDetails);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(batchOperationService).batchClaimTenders(eq(List.of(1L, 2L)), userIdCaptor.capture());
        assertThat(userIdCaptor.getValue()).isEqualTo(42L);
    }

    @Test
    void batchClaimTenders_RejectsUnknownAuthenticatedUser() {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.withUsername("ghost")
                .password("password")
                .authorities("ROLE_MANAGER")
                .build();
        BatchClaimRequest request = new BatchClaimRequest(List.of(1L), 999L, "tender");

        when(authService.resolveUserByUsername("ghost"))
                .thenThrow(new org.springframework.security.core.userdetails.UsernameNotFoundException("ghost"));

        BatchOperationController controller = new BatchOperationController(
                batchOperationService,
                batchTenderAssignmentService,
                authService
        );

        assertThatThrownBy(() -> controller.batchClaimTenders(request, userDetails))
                .isInstanceOf(AuthenticationServiceException.class)
                .hasMessageContaining("Authenticated user not found");
    }

    @Test
    void batchDeleteItems_hasPreAuthorizeAnnotation() throws NoSuchMethodException {
        org.springframework.security.access.prepost.PreAuthorize annotation = BatchOperationController.class
                .getMethod("batchDeleteItems", String.class, com.xiyu.bid.batch.dto.BatchDeleteRequest.class, UserDetails.class)
                .getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);

        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN') or (hasRole('MANAGER') and !#type.equalsIgnoreCase('tender'))");
    }
}
