package com.xiyu.bid.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xiyu.bid.project.dto.InitiationDto;
import com.xiyu.bid.project.dto.InitiationViewDto;
import com.xiyu.bid.project.service.ProjectCurrentUserLookupService;
import com.xiyu.bid.project.service.ProjectInitiationService;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectInitiationControllerTest {

    private ProjectInitiationService service;
    private com.xiyu.bid.project.service.ProjectInitiationApprovalService approvalService;
    private UserRepository userRepository;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setup() {
        service = mock(ProjectInitiationService.class);
        approvalService = mock(com.xiyu.bid.project.service.ProjectInitiationApprovalService.class);
        userRepository = mock(UserRepository.class);
        when(userRepository.findByUsername("sales")).thenReturn(Optional.of(
                com.xiyu.bid.entity.User.builder()
                        .id(42L)
                        .username("sales")
                        .fullName("Sales User")
                        .email("sales@example.com")
                        .password("secret")
                        .enabled(true)
                        .role(com.xiyu.bid.entity.User.Role.MANAGER)
                        .build()
        ));
        ProjectInitiationController controller = new ProjectInitiationController(
                service,
                approvalService,
                new ProjectCurrentUserLookupService(userRepository)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        reset(service, approvalService, userRepository);
    }

    @Test
    void submit_UsesResolvedCurrentUserId() throws Exception {
        InitiationDto req = new InitiationDto();
        req.setOwnerUnit("西域事业部");

        InitiationViewDto dto = new InitiationViewDto();
        dto.setProjectId(100L);
        dto.setOwnerUnit("西域事业部");
        when(service.submit(eq(100L), any(InitiationDto.class), eq(42L))).thenReturn(dto);

        UserDetails principal = User.withUsername("sales").password("x").roles("STAFF").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));

        mockMvc.perform(post("/api/projects/100/initiation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.projectId").value(100L))
                .andExpect(jsonPath("$.data.ownerUnit").value("西域事业部"));
    }
}
