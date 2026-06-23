package com.xiyu.bid.project.integration;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
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

    private User salesUser;

    @BeforeEach
    void setUp() {
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
}
