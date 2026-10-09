package com.xiyu.bid.competitionintel.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.competitionintel.dto.AnalysisCreateRequest;
import com.xiyu.bid.competitionintel.dto.CompetitorUpsertRequest;
import com.xiyu.bid.competitionintel.entity.CompetitionAnalysis;
import com.xiyu.bid.competitionintel.entity.Competitor;
import com.xiyu.bid.competitionintel.repository.CompetitionAnalysisRepository;
import com.xiyu.bid.competitionintel.repository.CompetitorRepository;
import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 竞争情报控制器集成测试
 * 测试HTTP端点的完整请求响应流程
 */
// The real repository uses MySQL ON DUPLICATE KEY UPDATE; scope compatibility to this test.
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:h2:mem:competition-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class CompetitionIntelControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CompetitorRepository competitorRepository;

    @Autowired
    private CompetitionAnalysisRepository analysisRepository;

    @MockBean
    private IAuditLogService auditLogService;

    private Competitor testCompetitor;
    private CompetitionAnalysis testAnalysis;

    @BeforeEach
    void setUp() {
        analysisRepository.deleteAll();
        competitorRepository.deleteAll();

        testCompetitor = competitorRepository.save(Competitor.builder()
                .name("测试竞企")
                .industry("建筑业")
                .strengths("技术实力强")
                .weaknesses("报价偏高")
                .marketShare(new BigDecimal("20.5"))
                .typicalBidRangeMin(new BigDecimal("1000000"))
                .typicalBidRangeMax(new BigDecimal("1500000"))
                .build());

        testAnalysis = analysisRepository.save(CompetitionAnalysis.builder()
                .projectId(100L)
                .competitorId(testCompetitor.getId())
                .analysisDate(LocalDateTime.now())
                .winProbability(new BigDecimal("65.0"))
                .competitiveAdvantage("资质齐全")
                .recommendedStrategy("突出技术优势")
                .riskFactors("价格竞争")
                .build());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void getAllCompetitors_ShouldReturnListOfCompetitors() throws Exception {
        mockMvc.perform(get("/api/ai/competition/competitors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].name").value("测试竞企"))
                .andExpect(jsonPath("$.data[0].industry").value("建筑业"))
                .andExpect(jsonPath("$.msg").value("Successfully retrieved competitors"));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", RoleProfileCatalog.COMPETITOR_CREATE_PERMISSION})
    void upsertCompetitor_WithValidName_ShouldReturnCompetitor() throws Exception {
        long before = competitorRepository.count();
        CompetitorUpsertRequest request = CompetitorUpsertRequest.builder()
                .name("新竞企")
                .build();

        mockMvc.perform(post("/api/ai/competition/competitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("新竞企"))
                .andExpect(jsonPath("$.msg").value("Competitor upserted successfully"));

        assertThat(competitorRepository.count()).isEqualTo(before + 1);
        assertThat(competitorRepository.findByName("新竞企"))
                .hasValueSatisfying(saved -> assertThat(saved.getName()).isEqualTo("新竞企"));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", RoleProfileCatalog.COMPETITOR_CREATE_PERMISSION})
    void upsertCompetitor_WithBlankName_ShouldReturnBadRequest() throws Exception {
        CompetitorUpsertRequest request = CompetitorUpsertRequest.builder()
                .name(" ")
                .build();

        mockMvc.perform(post("/api/ai/competition/competitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void getAnalysisByProject_ShouldReturnAnalysisList() throws Exception {
        mockMvc.perform(get("/api/ai/competition/project/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].projectId").value(100))
                .andExpect(jsonPath("$.msg").value("Successfully retrieved competition analysis"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void analyzeCompetition_ShouldReturnNewAnalysis() throws Exception {
        mockMvc.perform(post("/api/ai/competition/project/200/analyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectId").value(200))
                .andExpect(jsonPath("$.data.winProbability").value(60.0))
                .andExpect(jsonPath("$.data.analysisDate").isNotEmpty())
                .andExpect(jsonPath("$.msg").value("Competition analysis completed successfully"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void createAnalysis_WithValidData_ShouldReturnCreatedAnalysis() throws Exception {
        AnalysisCreateRequest request = AnalysisCreateRequest.builder()
                .projectId(300L)
                .competitorId(testCompetitor.getId())
                .winProbability(new BigDecimal("75.0"))
                .competitiveAdvantage("技术领先")
                .recommendedStrategy("强调创新")
                .riskFactors("价格竞争")
                .build();

        mockMvc.perform(post("/api/ai/competition/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectId").value(300))
                .andExpect(jsonPath("$.data.winProbability").value(75.0))
                .andExpect(jsonPath("$.msg").value("Analysis created successfully"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void createAnalysis_WithNullProjectId_ShouldReturnBadRequest() throws Exception {
        AnalysisCreateRequest request = AnalysisCreateRequest.builder()
                .projectId(null)
                .competitorId(testCompetitor.getId())
                .build();

        mockMvc.perform(post("/api/ai/competition/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void createAnalysis_WithInvalidWinProbability_ShouldReturnBadRequest() throws Exception {
        AnalysisCreateRequest request = AnalysisCreateRequest.builder()
                .projectId(300L)
                .winProbability(new BigDecimal("150.0"))
                .build();

        mockMvc.perform(post("/api/ai/competition/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void getHistoricalPerformance_ShouldReturnAnalysisList() throws Exception {
        mockMvc.perform(get("/api/ai/competition/competitor/{id}/history", testCompetitor.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].competitorId").value(testCompetitor.getId()))
                .andExpect(jsonPath("$.msg").value("Successfully retrieved historical performance"));
    }

    @Test
    @WithMockUser(roles = {"MANAGER"})
    void getHistoricalPerformance_WithNonExistentCompetitor_ShouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/ai/competition/competitor/99999/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @WithMockUser(roles = {"MANAGER"})
    void getAllCompetitors_WhenEmpty_ShouldReturnEmptyList() throws Exception {
        // Clear database
        analysisRepository.deleteAll();
        competitorRepository.deleteAll();

        mockMvc.perform(get("/api/ai/competition/competitors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ADMIN"})
    void upsertCompetitor_WithInsufficientPermissions_ShouldReturnForbidden(String role) throws Exception {
        long before = competitorRepository.count();
        CompetitorUpsertRequest request = CompetitorUpsertRequest.builder()
                .name("新竞企")
                .build();

        mockMvc.perform(post("/api/ai/competition/competitors")
                        .with(user("user").roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        assertThat(competitorRepository.count()).isEqualTo(before);
    }
}
