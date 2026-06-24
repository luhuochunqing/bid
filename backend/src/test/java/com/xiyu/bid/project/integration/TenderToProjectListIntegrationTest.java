package com.xiyu.bid.project.integration;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.tender.entity.TenderEvaluationBasic;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class TenderToProjectListIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectInitiationDetailsRepository initiationRepository;

    @Autowired
    private TenderRepository tenderRepository;

    @Autowired
    private TenderEvaluationRepository tenderEvaluationRepository;

    private User admin;

    @BeforeEach
    void setUp() {
        tenderEvaluationRepository.deleteAll();
        tenderRepository.deleteAll();
        userRepository.deleteAll();
        initiationRepository.deleteAll();

        admin = userRepository.save(User.builder()
                .username("admin-user")
                .password("XiyuDemo!2026")
                .email("admin@example.com")
                .fullName("管理员")
                .role(User.Role.ADMIN)
                .enabled(true)
                .departmentName("投标管理部")
                .build());
    }

    @Test
    @WithMockUser(username = "admin-user", roles = {"ADMIN"})
    void biddingTenderShouldAppearInProjectList() throws Exception {
        Tender tender = tenderRepository.save(Tender.builder()
                .title("CO-274 流程测试标讯")
                .sourcePlatform("人工录入")
                .status(Tender.Status.EVALUATED)
                .purchaserName("测试采购方")
                .budget(java.math.BigDecimal.valueOf(500000))
                .bidOpeningTime(LocalDateTime.of(2026, 7, 1, 10, 0))
                .build());

        Long tenderId = tender.getId();

        tenderEvaluationRepository.save(TenderEvaluation.builder()
                .tenderId(tenderId)
                .evaluatorId(admin.getId())
                .evaluationStatus(TenderEvaluation.EvaluationStatus.SUBMITTED)
                .build());

        // 1. 审核通过 -> 标讯变成投标中
        mockMvc.perform(post("/api/tenders/" + tenderId + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\": true, \"reviewComment\": \"通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 2. 投标立项 -> 创建项目
        mockMvc.perform(post("/api/tenders/" + tenderId + "/bid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectId").exists());

        // 3. 项目列表应包含该项目，且状态为待立项
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].name").value(org.hamcrest.Matchers.hasItem("CO-274 流程测试标讯")))
                .andExpect(jsonPath("$.data[?(@.name=='CO-274 流程测试标讯')].bidStatus").value(org.hamcrest.Matchers.hasItem("PENDING_INITIATION")));
    }

    /**
     * 标讯详情页「快速投标」流程：无评估表时，participate -> bid 也应创建项目并出现在列表。
     * 这是 CO-274 的回归场景。
     */
    @Test
    @WithMockUser(username = "admin-user", roles = {"ADMIN"})
    void quickBidWithoutEvaluationShouldAppearInProjectList() throws Exception {
        Tender tender = tenderRepository.save(Tender.builder()
                .title("CO-274 快速投标测试")
                .sourcePlatform("人工录入")
                .status(Tender.Status.TRACKING)
                .purchaserName("测试采购方")
                .budget(java.math.BigDecimal.valueOf(300000))
                .bidOpeningTime(LocalDateTime.of(2026, 8, 1, 10, 0))
                .build());

        Long tenderId = tender.getId();

        // 1. 参与投标 -> 标讯变成投标中
        mockMvc.perform(post("/api/tenders/" + tenderId + "/participate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accepted").value(true));

        // 2. 投标立项 -> 创建项目（此前因缺少评估表返回 404）
        mockMvc.perform(post("/api/tenders/" + tenderId + "/bid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectId").exists());

        // 3. 项目列表应包含该项目
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].name").value(org.hamcrest.Matchers.hasItem("CO-274 快速投标测试")))
                .andExpect(jsonPath("$.data[?(@.name=='CO-274 快速投标测试')].bidStatus").value(org.hamcrest.Matchers.hasItem("PENDING_INITIATION")));
    }
}
