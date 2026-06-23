package com.xiyu.bid.ai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.ai.client.RoutingAiProvider;
import com.xiyu.bid.ai.dto.AiAnalysisResponse;
import com.xiyu.bid.ai.dto.DimensionScore;
import com.xiyu.bid.ai.dto.ProjectScorePreviewRequestDTO;
import com.xiyu.bid.ai.repository.AiAnalysisResultRepository;
import com.xiyu.bid.ai.repository.ProjectScorePreviewRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class AiDeepCapabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiProvider aiProvider;

    @MockBean
    private RoutingAiProvider routingAiProvider;

    @Autowired
    private TenderRepository tenderRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleProfileRepository roleProfileRepository;

    @Autowired
    private AiAnalysisResultRepository aiAnalysisResultRepository;

    @Autowired
    private ProjectScorePreviewRepository projectScorePreviewRepository;

    private Tender tender;
    private Project project;

    @BeforeEach
    void setUp() {
        projectScorePreviewRepository.deleteAll();
        aiAnalysisResultRepository.deleteAll();
        projectRepository.deleteAll();
        tenderRepository.deleteAll();
        userRepository.deleteAll();
        roleProfileRepository.deleteAll();

        // Setup common mock behavior
        when(aiProvider.analyzeTender(anyString(), anyMap())).thenReturn(AiAnalysisResponse.builder()
                .score(85)
                .riskLevel(Tender.RiskLevel.LOW)
                .strengths(List.of("Experience", "Team"))
                .weaknesses(List.of("Budget limit"))
                .recommendations(List.of("Highlight tech depth"))
                .dimensionScores(List.of(DimensionScore.builder().dimension("Tech").score(90).details("Ok").build()))
                .build());

        when(aiProvider.analyzeProject(any(Long.class), anyMap())).thenReturn(AiAnalysisResponse.builder()
                .score(78)
                .riskLevel(Tender.RiskLevel.LOW)
                .strengths(List.of("Resources"))
                .weaknesses(List.of("Timeline"))
                .recommendations(List.of("Add developers"))
                .dimensionScores(List.of(DimensionScore.builder().dimension("Risk").score(80).details("Ok").build()))
                .build());

        when(routingAiProvider.analyzeTender(anyString(), anyMap())).thenReturn(AiAnalysisResponse.builder()
                .score(85)
                .riskLevel(Tender.RiskLevel.LOW)
                .strengths(List.of("Experience", "Team"))
                .weaknesses(List.of("Budget limit"))
                .recommendations(List.of("Highlight tech depth"))
                .dimensionScores(List.of(DimensionScore.builder().dimension("Tech").score(90).details("Ok").build()))
                .build());

        when(routingAiProvider.analyzeProject(any(Long.class), anyMap())).thenReturn(AiAnalysisResponse.builder()
                .score(78)
                .riskLevel(Tender.RiskLevel.LOW)
                .strengths(List.of("Resources"))
                .weaknesses(List.of("Timeline"))
                .recommendations(List.of("Add developers"))
                .dimensionScores(List.of(DimensionScore.builder().dimension("Risk").score(80).details("Ok").build()))
                .build());

        tender = tenderRepository.save(Tender.builder()
                .title("智慧城市 IOC 项目")
                .source("政府采购网")
                .budget(new BigDecimal("680.00"))
                .deadline(LocalDateTime.now().plusDays(10))
                .status(Tender.Status.TRACKING)
                .build());

        project = projectRepository.save(Project.builder()
                .name("智慧城市 IOC 项目")
                .tenderId(tender.getId())
                .managerId(900001L)
                .status(Project.Status.BIDDING)
                .build());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void tenderAnalysisPreviewAndCards_ShouldUseRealContracts() throws Exception {
        String createAnalysisResponse = mockMvc.perform(post("/api/tenders/{id}/ai-analysis", tender.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenderId").value(tender.getId()))
                .andExpect(jsonPath("$.data.winScore").isNumber())
                .andExpect(jsonPath("$.data.dimensionScores", hasSize(greaterThanOrEqualTo(1))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createdAnalysis = objectMapper.readTree(createAnalysisResponse).path("data");
        assertThat(createdAnalysis.path("suggestion").asText()).isNotBlank();

        mockMvc.perform(get("/api/tenders/{id}/ai-analysis", tender.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenderId").value(tender.getId()));

        ProjectScorePreviewRequestDTO previewRequest = ProjectScorePreviewRequestDTO.builder()
                .projectId(project.getId())
                .tenderId(tender.getId())
                .projectName(project.getName())
                .industry("政府")
                .budget(new BigDecimal("680.00"))
                .tags(List.of("信创", "智慧城市"))
                .build();

        mockMvc.perform(post("/api/projects/score-preview")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectId").value(project.getId()))
                .andExpect(jsonPath("$.data.aiSummary.winScore").isNumber())
                .andExpect(jsonPath("$.data.scoreAnalysis.scoreCategories", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.generatedTasks", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/api/projects/{projectId}/ai-cards", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.competition").isArray())
                .andExpect(jsonPath("$.data.compliance").isArray());

        assertThat(aiAnalysisResultRepository.count()).isEqualTo(1);
        assertThat(projectScorePreviewRepository.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "ai-staff", roles = {"MANAGER"})
    void aiEndpoints_ForStaff_ShouldDenyHiddenAndUnlinkedTargets() throws Exception {
        User staffUser = userRepository.save(User.builder()
                .username("ai-staff")
                .password("XiyuDemo!2026")
                .email("ai-staff@example.com")
                .fullName("AI 普通用户")
                .role(User.Role.MANAGER)
                .roleProfile(roleProfileRepository.save(RoleProfile.builder()
                        .code("bid-Team")
                        .name("普通员工")
                        .dataScope("self")
                        .build()))
                .enabled(true)
                .build());
        Tender visibleTender = tenderRepository.save(Tender.builder()
                .title("普通用户可见 AI 标讯")
                .source("员工平台")
                .budget(new BigDecimal("120.00"))
                .deadline(LocalDateTime.now().plusDays(7))
                .status(Tender.Status.TRACKING)
                .build());
        Project visibleProject = projectRepository.save(Project.builder()
                .name("普通用户可见 AI 项目")
                .tenderId(visibleTender.getId())
                .managerId(staffUser.getId())
                .teamMembers(List.of(staffUser.getId()))
                .status(Project.Status.BIDDING)
                .build());
        Tender unlinkedTender = tenderRepository.save(Tender.builder()
                .title("未关联项目 AI 标讯")
                .source("公共平台")
                .budget(new BigDecimal("90.00"))
                .deadline(LocalDateTime.now().plusDays(5))
                .status(Tender.Status.TRACKING)
                .build());

        mockMvc.perform(get("/api/projects/{projectId}/ai-cards", project.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/tenders/{id}/ai-analysis", unlinkedTender.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/projects/{projectId}/ai-cards", visibleProject.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
