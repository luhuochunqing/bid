package com.xiyu.bid.casework.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.casework.domain.port.CaseSnapshotPort;
import com.xiyu.bid.casework.dto.CaseDTO;
import com.xiyu.bid.casework.dto.CasePromoteFromProjectRequest;
import com.xiyu.bid.casework.dto.CaseReferenceRecordCreateRequest;
import com.xiyu.bid.casework.dto.CaseShareRecordCreateRequest;
import com.xiyu.bid.casework.repository.CaseReferenceRecordRepository;
import com.xiyu.bid.casework.repository.CaseShareRecordRepository;
import com.xiyu.bid.entity.Case;
import com.xiyu.bid.historyproject.dto.HistoricalProjectSnapshotDTO;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.repository.CaseRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
class CaseAdvancedIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CaseShareRecordRepository caseShareRecordRepository;

    @Autowired
    private CaseReferenceRecordRepository caseReferenceRecordRepository;

    private Case caseStudy;
    private Case relatedCase;
    private User ownerUser;

    @TestConfiguration
    static class TestBeans {
        @Bean(name = "passwordEncryptionUtil")
        @Primary
        PasswordEncryptionUtil passwordEncryptionUtil() {
            return new PasswordEncryptionUtil() {
                @Override
                public void initialize() {}

                @Override
                public String encrypt(String plainPassword) { return plainPassword; }

                @Override
                public String decrypt(String encryptedPassword) { return encryptedPassword; }

                @Override
                public boolean isKeyValid() { return true; }
            };
        }

        @Bean
        @Primary
        CaseSnapshotPort caseSnapshotPort() {
            return projectId -> HistoricalProjectSnapshotDTO.builder()
                    .projectId(projectId)
                    .archiveRecordId(10001L)
                    .exportId(20002L)
                    .projectName("归档项目")
                    .customerName("国资委")
                    .productLine("智慧园区")
                    .archiveSummary("项目已完成归档")
                    .documentSnapshotText("投标正文摘录")
                    .recommendedTags(List.of("数字化", "统一门户"))
                    .capturedAt(LocalDateTime.of(2025, 6, 1, 10, 0))
                    .build();
        }
    }

    @BeforeEach
    void setUp() {
        caseReferenceRecordRepository.deleteAll();
        caseShareRecordRepository.deleteAll();
        caseRepository.deleteAll();
        userRepository.deleteAll();

        ownerUser = userRepository.save(User.builder()
                .username("case-admin")
                .password("XiyuDemo!2026")
                .email("case-admin@example.com")
                .fullName("李总")
                .role(User.Role.ADMIN)
                .enabled(true)
                .build());

        caseStudy = caseRepository.save(Case.builder()
                .title("智慧园区平台案例")
                .industry(Case.Industry.INFRASTRUCTURE)
                .outcome(Case.Outcome.WON)
                .amount(new BigDecimal("780.00"))
                .projectDate(LocalDate.of(2025, 5, 20))
                .description("项目初始摘要")
                .customerName("杭州市人民政府")
                .locationName("杭州")
                .projectPeriod("2025-01-01 - 2025-12-31")
                .productLine("智慧园区")
                .sourceProjectId(1001L)
                .archiveSummary("项目初始摘要")
                .priceStrategy("以价值换价格")
                .successFactors(List.of("业主认可", "方案完整"))
                .lessonsLearned(List.of("提前梳理归档材料"))
                .documentSnapshotText("项目正文摘要")
                .attachmentNames(List.of("需求说明书.pdf"))
                .status("PUBLISHED")
                .publishedAt(LocalDateTime.of(2025, 5, 21, 9, 30))
                .visibility("INTERNAL")
                .tags(List.of("智慧园区", "IOC"))
                .highlights(List.of("统一门户", "跨部门联动"))
                .technologies(List.of("Vue", "Spring Boot"))
                .viewCount(12L)
                .useCount(3L)
                .build());

        relatedCase = caseRepository.save(Case.builder()
                .title("智慧园区平台案例-同线")
                .industry(Case.Industry.INFRASTRUCTURE)
                .outcome(Case.Outcome.WON)
                .amount(new BigDecimal("860.00"))
                .projectDate(LocalDate.of(2025, 5, 22))
                .description("同产品线案例")
                .customerName("杭州市人民政府")
                .locationName("杭州")
                .projectPeriod("2025-02-01 - 2025-12-31")
                .productLine("智慧园区")
                .sourceProjectId(1001L)
                .archiveSummary("同产品线项目摘要")
                .priceStrategy("价值优先")
                .successFactors(List.of("交付稳定"))
                .lessonsLearned(List.of("提前锁定核心接口"))
                .documentSnapshotText("同产品线正文摘要")
                .attachmentNames(List.of("验收报告.pdf"))
                .status("PUBLISHED")
                .publishedAt(LocalDateTime.of(2025, 5, 25, 9, 30))
                .visibility("INTERNAL")
                .tags(List.of("智慧园区", "政务"))
                .highlights(List.of("联动审批"))
                .technologies(List.of("Vue", "Spring Boot", "MySQL"))
                .viewCount(9L)
                .useCount(1L)
                .build());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void caseAdvancedEndpoints_ShouldPersistSearchAndRecommendationFlows() throws Exception {
        CaseDTO updateRequest = CaseDTO.builder()
                .title("智慧园区平台案例（更新）")
                .industry(CaseDTO.Industry.INFRASTRUCTURE)
                .outcome(CaseDTO.Outcome.WON)
                .amount(new BigDecimal("820.00"))
                .projectDate(LocalDate.of(2025, 5, 20))
                .description("更新后的项目摘要")
                .customerName("杭州市人民政府")
                .locationName("杭州")
                .projectPeriod("2025-01-01 - 2025-12-31")
                .productLine("智慧园区")
                .sourceProjectId(1001L)
                .archiveSummary("更新后的归档摘要")
                .priceStrategy("稳定性优先")
                .successFactors(List.of("业主认可", "跨部门配合"))
                .lessonsLearned(List.of("归档材料提前收口"))
                .documentSnapshotText("更新后的项目正文摘要")
                .attachmentNames(List.of("需求说明书.pdf", "验收报告.pdf"))
                .status("PUBLISHED")
                .publishedAt(LocalDateTime.of(2025, 5, 21, 9, 30))
                .visibility("INTERNAL")
                .tags(List.of("智慧园区", "政务"))
                .highlights(List.of("统一门户", "一网统管", "跨部门联动"))
                .technologies(List.of("Vue", "Spring Boot", "MySQL"))
                .viewCount(66L)
                .useCount(3L)
                .build();

        mockMvc.perform(put("/api/knowledge/cases/{id}", caseStudy.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("智慧园区平台案例（更新）"))
                .andExpect(jsonPath("$.data.productLine").value("智慧园区"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.visibility").value("INTERNAL"))
                .andExpect(jsonPath("$.data.attachmentNames", hasSize(2)));

        CaseShareRecordCreateRequest shareRequest = CaseShareRecordCreateRequest.builder()
                .createdBy(ownerUser.getId())
                .createdByName("李总")
                .baseUrl("http://127.0.0.1:14173")
                .build();

        mockMvc.perform(post("/api/knowledge/cases/{id}/share-records", caseStudy.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").value(containsString("/knowledge/case/detail?id=" + caseStudy.getId())))
                .andExpect(jsonPath("$.data.createdByName").value("李总"));

        mockMvc.perform(get("/api/knowledge/cases/{id}/share-records", caseStudy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        CaseReferenceRecordCreateRequest referenceRequest = CaseReferenceRecordCreateRequest.builder()
                .referencedBy(ownerUser.getId())
                .referencedByName("李总")
                .referenceTarget("杭州 IOC 投标项目")
                .referenceContext("用于项目方案章节中的成功案例引用")
                .build();

        mockMvc.perform(post("/api/knowledge/cases/{id}/references", caseStudy.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(referenceRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.referenceTarget").value("杭州 IOC 投标项目"))
                .andExpect(jsonPath("$.data.referencedByName").value("李总"));

        mockMvc.perform(get("/api/knowledge/cases/{id}/references", caseStudy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(get("/api/knowledge/cases")
                        .param("keyword", "智慧园区")
                        .param("industry", "INFRASTRUCTURE")
                        .param("outcome", "WON")
                        .param("tags", "智慧园区")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .param("sort", "popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].productLine").value("智慧园区"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.sort").value("popular"));

        mockMvc.perform(get("/api/knowledge/cases/search/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productLines", hasSize(1)))
                .andExpect(jsonPath("$.data.statuses").value(org.hamcrest.Matchers.hasItem("PUBLISHED")))
                .andExpect(jsonPath("$.data.tags", hasSize(greaterThanOrEqualTo(2))));

        mockMvc.perform(get("/api/knowledge/cases/{id}/related", caseStudy.getId())
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].caseData.id").value(relatedCase.getId()))
                .andExpect(jsonPath("$.data[0].reason").value(containsString("产品线一致")));

        mockMvc.perform(get("/api/knowledge/cases/{id}", caseStudy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.useCount").value(4))
                .andExpect(jsonPath("$.data.description").value("更新后的项目摘要"))
                .andExpect(jsonPath("$.data.successFactors", hasSize(2)))
                .andExpect(jsonPath("$.data.searchDocument").value(containsString("智慧园区")));

        assertThat(caseShareRecordRepository.findByCaseIdOrderByCreatedAtDesc(caseStudy.getId())).hasSize(1);
        assertThat(caseReferenceRecordRepository.findByCaseIdOrderByReferencedAtDesc(caseStudy.getId())).hasSize(1);
        assertThat(caseRepository.findById(caseStudy.getId()).orElseThrow().getUseCount()).isEqualTo(4L);
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void promoteFromProject_ShouldConsumeSnapshotAndCreatePublishableCase() throws Exception {
        String excerptOnlyKeyword = "星河联动中台-" + System.currentTimeMillis();
        CasePromoteFromProjectRequest request = CasePromoteFromProjectRequest.builder()
                .projectId(9023L)
                .title("项目归档案例")
                .status("PUBLISHED")
                .visibility("PUBLIC")
                .priceStrategy("价值优先")
                .documentSnapshotText(excerptOnlyKeyword)
                .build();

        mockMvc.perform(post("/api/knowledge/cases/promote-from-project")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sourceProjectId").value(9023))
                .andExpect(jsonPath("$.data.productLine").value("智慧园区"))
                .andExpect(jsonPath("$.data.archiveSummary").value(containsString("项目已完成归档")))
                .andExpect(jsonPath("$.data.documentSnapshotText").value(containsString(excerptOnlyKeyword)))
                .andExpect(jsonPath("$.data.successFactors", hasSize(2)))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.publishedAt").isNotEmpty());

        mockMvc.perform(get("/api/knowledge/cases")
                        .param("keyword", excerptOnlyKeyword)
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].title").value("项目归档案例"))
                .andExpect(jsonPath("$.data.items[0].productLine").value("智慧园区"));
    }
}
