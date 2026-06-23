package com.xiyu.bid.tender.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.dto.TenderRequest;
import com.xiyu.bid.tender.service.TenderAuditService;
import com.xiyu.bid.tender.service.TenderCommandService;
import com.xiyu.bid.tender.service.TenderImportService;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.tender.service.TenderQueryService;
import com.xiyu.bid.tender.service.TenderSubmissionService;
import com.xiyu.bid.tender.service.TenderAiAnalysisService;
import com.xiyu.bid.tender.service.TenderImportService;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.ai.service.AiDeepCapabilityService;
import com.xiyu.bid.demo.service.DemoDataProvider;
import com.xiyu.bid.demo.service.DemoFusionService;
import com.xiyu.bid.demo.service.DemoModeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenderControllerUpdateTest {

    private TenderQueryService tenderQueryService;
    private TenderCommandService tenderCommandService;
    private TenderMapper tenderMapper;
    private DemoModeService demoModeService;
    private DemoDataProvider demoDataProvider;
    private DemoFusionService demoFusionService;
    private TenderAuditService tenderAuditService;
    private TenderImportService tenderImportService;
    private AiDeepCapabilityService aiDeepCapabilityService;
    private AuthService authService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setup() {
        tenderQueryService = mock(TenderQueryService.class);
        tenderCommandService = mock(TenderCommandService.class);
        tenderMapper = mock(TenderMapper.class);
        demoModeService = mock(DemoModeService.class);
        demoDataProvider = mock(DemoDataProvider.class);
        demoFusionService = mock(DemoFusionService.class);
        tenderAuditService = mock(TenderAuditService.class);
        tenderImportService = mock(TenderImportService.class);
        aiDeepCapabilityService = mock(AiDeepCapabilityService.class);
        authService = mock(AuthService.class);

        TenderController controller = new TenderController(
                tenderQueryService,
                tenderCommandService,
                null, // tenderSubmissionService
                tenderMapper,
                tenderImportService,
                demoModeService,
                demoDataProvider,
                demoFusionService,
                tenderAuditService,
                authService
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new com.xiyu.bid.exception.GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username, String role) {
        UserDetails principal = org.springframework.security.core.userdetails.User.withUsername(username)
                .password("password")
                .roles(role)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("updateTender: Admin user -> Success")
    void updateTender_admin_success() throws Exception {
        authenticateAs("admin-user", "ADMIN");

        User user = User.builder().id(1L).username("admin-user").role(User.Role.ADMIN).build();
        when(authService.resolveUserByUsername("admin-user")).thenReturn(user);

        TenderDTO tenderDTO = TenderDTO.builder().id(100L).creatorId(2L).status(Tender.Status.PENDING_ASSIGNMENT).build();
        when(tenderQueryService.getTenderById(100L)).thenReturn(tenderDTO);

        TenderRequest request = new TenderRequest();
        request.setTitle("Updated Title");
        request.setDeadline(java.time.LocalDateTime.now().plusDays(10));

        TenderDTO dto = new TenderDTO();
        when(tenderMapper.toDTO(any(TenderRequest.class))).thenReturn(dto);
        when(tenderCommandService.updateTender(eq(100L), any(TenderDTO.class))).thenReturn(dto);

        mockMvc.perform(put("/api/tenders/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("updateTender: Specialist, unassigned and self-created -> Success (creator can edit PENDING_ASSIGNMENT)")
    void updateTender_specialist_unassignedSelfCreated_success() throws Exception {
        authenticateAs("specialist-user", "MANAGER");

        User user = User.builder().id(1L).username("specialist-user").role(User.Role.MANAGER).build();
        User spyUser = spy(user);
        lenient().when(spyUser.getRoleCode()).thenReturn("bid-Team");
        when(authService.resolveUserByUsername("specialist-user")).thenReturn(spyUser);

        TenderDTO tenderDTO = TenderDTO.builder().id(100L).creatorId(1L).status(Tender.Status.PENDING_ASSIGNMENT).title("Original").build();
        when(tenderQueryService.getTenderById(100L)).thenReturn(tenderDTO);

        TenderDTO updatedDTO = TenderDTO.builder().id(100L).creatorId(1L).status(Tender.Status.PENDING_ASSIGNMENT).title("Updated Title").build();
        when(tenderCommandService.updateTender(eq(100L), any(), eq(1L))).thenReturn(updatedDTO);

        TenderRequest request = new TenderRequest();
        request.setTitle("Updated Title");
        request.setDeadline(java.time.LocalDateTime.now().plusDays(10));

        mockMvc.perform(put("/api/tenders/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("updateTender: Specialist, assigned and self-created -> Success")
    void updateTender_specialist_assignedSelfCreated_success() throws Exception {
        authenticateAs("specialist-user", "MANAGER");

        User user = User.builder().id(1L).username("specialist-user").role(User.Role.MANAGER).build();
        User spyUser = spy(user);
        lenient().when(spyUser.getRoleCode()).thenReturn("bid-Team");
        when(authService.resolveUserByUsername("specialist-user")).thenReturn(spyUser);

        TenderDTO tenderDTO = TenderDTO.builder().id(100L).creatorId(1L).status(Tender.Status.TRACKING).build();
        when(tenderQueryService.getTenderById(100L)).thenReturn(tenderDTO);

        TenderRequest request = new TenderRequest();
        request.setTitle("Updated Title");
        request.setDeadline(java.time.LocalDateTime.now().plusDays(10));

        TenderDTO dto = new TenderDTO();
        when(tenderMapper.toDTO(any(TenderRequest.class))).thenReturn(dto);
        when(tenderCommandService.updateTender(eq(100L), any(TenderDTO.class))).thenReturn(dto);

        mockMvc.perform(put("/api/tenders/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
