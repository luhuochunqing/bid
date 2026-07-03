package com.xiyu.bid.project.integration;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class ProjectListInitiationStateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenderRepository tenderRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectInitiationDetailsRepository initiationDetailsRepository;

    @Autowired
    private ProjectLeadAssignmentRepository projectLeadAssignmentRepository;

    private User salesUser;
    private User primaryLeadUser;
    private User secondaryLeadUser;

    @BeforeEach
    void setUp() {
        projectLeadAssignmentRepository.deleteAll();
        initiationDetailsRepository.deleteAll();
        projectRepository.deleteAll();
        tenderRepository.deleteAll();

        salesUser = userRepository.save(User.builder()
                .username("sales")
                .password("XiyuDemo!2026")
                .email("sales@example.com")
                .fullName("张销售")
                .role(User.Role.MANAGER)
                .enabled(true)
                .departmentName("销售部")
                .build());

        primaryLeadUser = userRepository.save(User.builder()
                .username("primaryLead")
                .password("XiyuDemo!2026")
                .email("primary-lead@example.com")
                .fullName("王主投")
                .role(User.Role.MANAGER)
                .enabled(true)
                .departmentName("投标部")
                .build());

        secondaryLeadUser = userRepository.save(User.builder()
                .username("secondaryLead")
                .password("XiyuDemo!2026")
                .email("secondary-lead@example.com")
                .fullName("李副投")
                .role(User.Role.MANAGER)
                .enabled(true)
                .departmentName("投标部")
                .build());

        Tender tender = tenderRepository.save(Tender.builder()
                .title("测试待立项标讯")
                .sourcePlatform("人工录入")
                .sourceType(Tender.SourceType.MANUAL_SINGLE)
                .projectManagerName("张销售")
                .department("销售部")
                .projectType("公开招标")
                .customerType("政府")
                .bidOpeningTime(LocalDateTime.of(2026, 6, 13, 13, 38, 45))
                .status(Tender.Status.BIDDING)
                .build());

        Project project = projectRepository.save(Project.builder()
                .name("测试待立项项目")
                .tenderId(tender.getId())
                .status(Project.Status.INITIATED)
                .stage("INITIATED")
                .managerId(salesUser.getId())
                .teamMembers(List.of(salesUser.getId()))
                .build());

        projectLeadAssignmentRepository.save(ProjectLeadAssignment.builder()
                .projectId(project.getId())
                .primaryLeadUserId(primaryLeadUser.getId())
                .secondaryLeadUserId(secondaryLeadUser.getId())
                .assignedBy(salesUser.getId())
                .build());

        initiationDetailsRepository.save(ProjectInitiationDetails.builder()
                .projectId(project.getId())
                .ownerUserId(salesUser.getId())
                .reviewStatus("DRAFT")
                .projectLeaderName("张销售")
                .leaderDepartment("销售部")
                .build());
    }

    @Test
    @WithMockUser(username = "sales", roles = {"BID_PROJECTLEADER", "MANAGER"})
    void getProjects_shouldReturnPendingInitiationRowForDraftInitiation() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("测试待立项项目"))
                .andExpect(jsonPath("$.data[0].projectLeaderId").value(salesUser.getId()))
                .andExpect(jsonPath("$.data[0].biddingLeaderId").value(primaryLeadUser.getId()))
                .andExpect(jsonPath("$.data[0].secondaryBiddingLeaderId").value(secondaryLeadUser.getId()))
                .andExpect(jsonPath("$.data[0].bidStatus").value("PENDING_INITIATION"));
    }

    @Test
    @WithMockUser(username = "sales", roles = {"BID_PROJECTLEADER", "MANAGER"})
    void getProjects_shouldFallbackToInitiatedWhenStageValueIsInvalid() throws Exception {
        Project project = projectRepository.findAll().getFirst();
        project.setStage("PENDING_INITIATION");
        projectRepository.save(project);

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("测试待立项项目"))
                .andExpect(jsonPath("$.data[0].stage").value("PENDING_INITIATION"))
                .andExpect(jsonPath("$.data[0].bidStatus").value("PENDING_INITIATION"));
    }

    @Test
    @WithMockUser(username = "sales", roles = {"BID_PROJECTLEADER", "MANAGER"})
    void getProjects_shouldReturnPendingInitiationAfterRejection() throws Exception {
        ProjectInitiationDetails details = initiationDetailsRepository.findAll().getFirst();
        details.setReviewStatus("REJECTED");
        initiationDetailsRepository.save(details);

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("测试待立项项目"))
                .andExpect(jsonPath("$.data[0].bidStatus").value("PENDING_INITIATION"));
    }

    @Test
    @WithMockUser(username = "sales", roles = {"BID_PROJECTLEADER", "MANAGER"})
    void getProjects_shouldReturnInitiatedRowAfterSubmission() throws Exception {
        ProjectInitiationDetails details = initiationDetailsRepository.findAll().getFirst();
        details.setReviewStatus("PENDING_REVIEW");
        initiationDetailsRepository.save(details);

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("测试待立项项目"))
                .andExpect(jsonPath("$.data[0].bidStatus").value("INITIATED"));
    }

    @Test
    @WithMockUser(username = "sales", roles = {"BID_PROJECTLEADER", "MANAGER"})
    void getProjects_shouldFilterByProjectLeaderId() throws Exception {
        mockMvc.perform(get("/api/projects")
                        .param("projectLeaderId", salesUser.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("测试待立项项目"));

        mockMvc.perform(get("/api/projects")
                        .param("projectLeaderId", primaryLeadUser.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @WithMockUser(username = "sales", roles = {"BID_PROJECTLEADER", "MANAGER"})
    void getProjects_shouldFilterByPrimaryBiddingLeaderId_only() throws Exception {
        // CO-xxx fix: 对齐 ProjectController.biddingLeaderId 过滤契约（与前端 useProjectFilter 一致）：
        // 投标负责人筛选只匹配主负责人（primaryLeadUserId），不匹配副负责人（secondaryLeadUserId）。
        // 设计原因：避免筛 A 时主=B/副=A 的项目混入，而列表只显示主负责人姓名。
        mockMvc.perform(get("/api/projects")
                        .param("biddingLeaderId", primaryLeadUser.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("测试待立项项目"));

        // 副负责人不参与筛选，期望 0 条结果
        mockMvc.perform(get("/api/projects")
                        .param("biddingLeaderId", secondaryLeadUser.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0));

        // 销售负责人也非投标负责人，期望 0 条结果
        mockMvc.perform(get("/api/projects")
                        .param("biddingLeaderId", salesUser.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
