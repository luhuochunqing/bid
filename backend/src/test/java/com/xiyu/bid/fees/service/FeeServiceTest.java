package com.xiyu.bid.fees.service;

import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.fees.dto.FeeCreateRequest;
import com.xiyu.bid.fees.dto.FeeDTO;
import com.xiyu.bid.fees.dto.FeeUpdateRequest;
import com.xiyu.bid.fees.entity.Fee;
import com.xiyu.bid.fees.repository.FeeRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeeServiceTest {

    @Mock
    private FeeRepository feeRepository;

    @Mock
    private IAuditLogService auditLogService;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    private FeeService feeService;

    @BeforeEach
    void setUp() {
        feeService = new FeeService(feeRepository, auditLogService, projectAccessScopeService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createFee_ShouldCheckProjectAccessBeforeSaving() {
        FeeCreateRequest request = FeeCreateRequest.builder()
                .projectId(101L)
                .feeType(Fee.FeeType.BID_BOND)
                .amount(new BigDecimal("100.00"))
                .feeDate(LocalDateTime.now())
                .build();
        doThrow(new AccessDeniedException("denied"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(101L);

        assertThatThrownBy(() -> feeService.createFee(request))
                .isInstanceOf(AccessDeniedException.class);
        verify(feeRepository, never()).save(any(Fee.class));
    }

    @Test
    void getFeeById_ShouldCheckAccessUsingFeeProjectId() {
        when(feeRepository.findById(9L)).thenReturn(Optional.of(fee(9L, 202L, Fee.Status.PENDING)));
        doThrow(new AccessDeniedException("denied"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(202L);

        assertThatThrownBy(() -> feeService.getFeeById(9L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateFee_ShouldCheckAccessUsingExistingFeeProjectIdBeforeSaving() {
        when(feeRepository.findById(9L)).thenReturn(Optional.of(fee(9L, 202L, Fee.Status.PENDING)));
        doThrow(new AccessDeniedException("denied"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(202L);

        assertThatThrownBy(() -> feeService.updateFee(9L, FeeUpdateRequest.builder()
                        .amount(new BigDecimal("300.00"))
                        .build()))
                .isInstanceOf(AccessDeniedException.class);
        verify(feeRepository, never()).save(any(Fee.class));
    }

    @Test
    void getFeesByProjectId_ShouldCheckProjectAccessBeforeQuery() {
        doThrow(new AccessDeniedException("denied"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(303L);

        assertThatThrownBy(() -> feeService.getFeesByProjectId(303L))
                .isInstanceOf(AccessDeniedException.class);
        verify(feeRepository, never()).findByProjectId(303L);
    }

    @Test
    void getStatistics_ShouldCheckProjectAccessBeforeQuery() {
        doThrow(new AccessDeniedException("denied"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(303L);

        assertThatThrownBy(() -> feeService.getStatistics(303L))
                .isInstanceOf(AccessDeniedException.class);
        verify(feeRepository, never()).sumAmountByProjectIdAndStatus(any(), any());
    }

    @Test
    void getAllFees_ShouldFilterByAllowedProjectIdsForNonAdminUser() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(11L, 12L));
        when(feeRepository.findByProjectIdIn(List.of(11L, 12L), pageable))
                .thenReturn(Page.empty(pageable));

        Page<FeeDTO> fees = feeService.getAllFees(pageable);

        assertThat(fees).isEmpty();
        verify(feeRepository).findByProjectIdIn(List.of(11L, 12L), pageable);
        verify(feeRepository, never()).findAll(pageable);
    }

    @Test
    void getAllFees_ShouldUseFullQueryWhenAllowedProjectIdsIsEmptyForAdmin() {
        PageRequest pageable = PageRequest.of(0, 10);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin-user",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());
        when(feeRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        feeService.getAllFees(pageable);

        verify(feeRepository).findAll(pageable);
    }

    @Test
    void getAllFees_ShouldReturnEmptyPageWhenNonAdminHasNoAllowedProjects() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());

        Page<FeeDTO> fees = feeService.getAllFees(pageable);

        assertThat(fees).isEmpty();
        verify(feeRepository, never()).findAll(pageable);
        verify(feeRepository, never()).findByProjectIdIn(any(), any());
    }

    @Test
    void getFeesByStatus_ShouldFilterByAllowedProjectIdsForNonAdminUser() {
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(21L));
        when(feeRepository.findByStatusAndProjectIdIn(Fee.Status.PAID, List.of(21L)))
                .thenReturn(List.of(fee(1L, 21L, Fee.Status.PAID)));

        List<FeeDTO> fees = feeService.getFeesByStatus(FeeDTO.Status.PAID);

        assertThat(fees).extracting(FeeDTO::getProjectId).containsExactly(21L);
        verify(feeRepository).findByStatusAndProjectIdIn(Fee.Status.PAID, List.of(21L));
        verify(feeRepository, never()).findByStatus(Fee.Status.PAID);
    }

    private Fee fee(Long id, Long projectId, Fee.Status status) {
        return Fee.builder()
                .id(id)
                .projectId(projectId)
                .feeType(Fee.FeeType.BID_BOND)
                .amount(new BigDecimal("100.00"))
                .feeDate(LocalDateTime.now())
                .status(status)
                .build();
    }
}
