package com.xiyu.bid.casework.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.casework.application.ProjectArchiveWorkflowService;
import com.xiyu.bid.casework.dto.ProjectArchiveQuery;
import com.xiyu.bid.casework.infrastructure.*;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProjectArchiveIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TenderRepository tenderRepository;

    @Autowired
    private ProjectArchiveRepository archiveRepository;

    @Autowired
    private ArchiveFileRepository fileRepository;

    @Autowired
    private ArchiveLogRepository logRepository;

    @Autowired
    private KnowledgeCaseRepository caseRepository;

    @Autowired
    private com.xiyu.bid.businessqualification.infrastructure.persistence.repository.QualificationLoanRecordJpaRepository loanRepository;

    @Autowired
    private ProjectArchiveWorkflowService workflowService;

    private User adminUser;
    private User staffUser;
    private Project project;
    private ProjectArchive archive;

    @BeforeEach
    void setUp() {
        fileRepository.deleteAll();
        logRepository.deleteAll();
        archiveRepository.deleteAll();
        caseRepository.deleteAll();
        projectRepository.deleteAll();
        tenderRepository.deleteAll();
        userRepository.deleteAll();
        loanRepository.deleteAll();

        // 1. 创建用户
        adminUser = new User();
        adminUser.setUsername("admin_user");
        adminUser.setEmail("admin@test.com");
        adminUser.setFullName("管理员");
        adminUser.setPassword("password");
        adminUser.setRole(User.Role.ADMIN);
        adminUser.setEnabled(true);
        userRepository.save(adminUser);

        staffUser = new User();
        staffUser.setUsername("staff_user");
        staffUser.setEmail("staff@test.com");
        staffUser.setFullName("员工");
        staffUser.setPassword("password");
        staffUser.setRole(User.Role.STAFF);
        staffUser.setEnabled(true);
        userRepository.save(staffUser);

        // 2. 创建标讯和项目
        Tender tender = Tender.builder()
                .title("测试标讯")
                .projectType("综合")
                .projectManagerName("张项目经理")
                .biddingPersonName("李投标经理")
                .status(Tender.Status.WON)
                .build();
        tender = tenderRepository.save(tender);

        project = Project.builder()
                .name("测试项目一期")
                .tenderId(tender.getId())
                .status(Project.Status.WON)
                .managerId(adminUser.getId())
                .teamMembers(List.of(staffUser.getId())) // 包含 staff 成员使其有权限
                .build();
        project = projectRepository.save(project);

        // 3. 创建归档台账
        archive = new ProjectArchive();
        archive.setProjectId(project.getId());
        archive.setProjectName(project.getName());
        archive.setArchiveStatus("CLOSED");
        archive = archiveRepository.save(archive);

        // 4. 创建归档文件
        ArchiveFile file1 = new ArchiveFile();
        file1.setArchiveId(archive.getId());
        file1.setFileName("招标说明书.pdf");
        file1.setDocumentCategory("TENDER");
        file1.setFilePath("/files/tender1.pdf");
        file1.setFileSize(1024L);
        file1.setUploadUserId(adminUser.getId());
        file1.setUploadUserName(adminUser.getUsername());
        fileRepository.save(file1);

        ArchiveFile file2 = new ArchiveFile();
        file2.setArchiveId(archive.getId());
        file2.setFileName("投标回复函.docx");
        file2.setDocumentCategory("BID");
        file2.setFilePath("/files/bid1.docx");
        file2.setFileSize(2048L);
        file2.setUploadUserId(adminUser.getId());
        file2.setUploadUserName(adminUser.getUsername());
        fileRepository.save(file2);

        // 5. 创建日志
        ArchiveLog log = new ArchiveLog();
        log.setArchiveId(archive.getId());
        log.setOperatorId(adminUser.getId());
        log.setOperatorName(adminUser.getUsername());
        log.setActionType("DOWNLOAD");
        log.setActionContent("下载了“招标说明书.pdf”");
        logRepository.save(log);
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    void getArchives_AsAdmin_ShouldReturnAll() throws Exception {
        mockMvc.perform(get("/api/archive")
                        .param("projectName", "测试")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].projectName").value("测试项目一期"))
                .andExpect(jsonPath("$.content[0].projectType").value("综合"))
                .andExpect(jsonPath("$.content[0].bidResult").value("WON"))
                .andExpect(jsonPath("$.content[0].fileCategoryDetails.TENDER").value(1))
                .andExpect(jsonPath("$.content[0].fileCategoryDetails.BID").value(1))
                .andExpect(jsonPath("$.content[0].projectManager").value("张项目经理"))
                .andExpect(jsonPath("$.content[0].bidManager").value("李投标经理"));
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    void getArchiveDetail_AsAdmin_ShouldReturnDetail() throws Exception {
        mockMvc.perform(get("/api/archive/" + archive.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectName").value("测试项目一期"))
                .andExpect(jsonPath("$.files[*].fileName").value(org.hamcrest.Matchers.hasItems("招标说明书.pdf", "投标回复函.docx")))
                .andExpect(jsonPath("$.logs[0].actionType").value("DOWNLOAD"));
    }

    @Test
    @WithMockUser(username = "staff_user", roles = {"STAFF"})
    void checkBorrow_AsStaffWithAccess_ShouldAllow() throws Exception {
        com.xiyu.bid.businessqualification.infrastructure.persistence.entity.QualificationLoanRecordEntity loan =
            new com.xiyu.bid.businessqualification.infrastructure.persistence.entity.QualificationLoanRecordEntity();
        loan.setQualificationId(1L);
        loan.setProjectId(String.valueOf(project.getId()));
        loan.setBorrower("staff_user");
        loan.setStatus(com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus.BORROWED);
        loan.setBorrowedAt(java.time.LocalDateTime.now());
        loanRepository.save(loan);

        mockMvc.perform(get("/api/qualification/1/check-borrow")
                        .param("projectId", project.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.borrowRecordId").value(loan.getId().intValue()));
    }

    @Test
    @WithMockUser(username = "staff_user", roles = {"STAFF"})
    void checkBorrow_AsStaffWithoutAccess_ShouldDeny() throws Exception {
        // 创建另一个无权访问的项目
        Project otherProject = projectRepository.save(Project.builder()
                .name("隔离的秘密项目")
                .tenderId(99999L)
                .status(Project.Status.INITIATED)
                .managerId(adminUser.getId())
                .teamMembers(List.of()) // 无此成员
                .build());

        mockMvc.perform(get("/api/qualification/1/check-borrow")
                        .param("projectId", otherProject.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("未绑定已审批通过的借阅流程，请先提交借阅审批"));
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    void caseWorkflow_ShouldQueryAndReuseAndOffShelf() throws Exception {
        // 1. 准备案例
        KnowledgeCase kcase = new KnowledgeCase();
        kcase.setSourceProjectId(project.getId());
        kcase.setSourceProjectName(project.getName());
        kcase.setScoringPointTitle("技术偏离表响应");
        kcase.setRequirementRaw("要求完全响应偏离表");
        kcase.setResponseText("我司在此承诺完全响应...");
        kcase.setStatus("ACTIVE");
        kcase.setProjectType("综合");
        kcase.setCustomerType("国有企业");
        kcase = caseRepository.save(kcase);

        // 2. 查询卡片
        mockMvc.perform(get("/api/cases")
                        .param("keyword", "偏离表")
                        .param("customerType", "国有企业")
                        .param("sortBy", "created"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].scoringTitle").value("技术偏离表响应"))
                .andExpect(jsonPath("$.content[0].reuseCount").value(0));

        // 3. 一键复用
        mockMvc.perform(post("/api/cases/" + kcase.getId() + "/reuse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newReuseCount").value(1));

        // 4. 下架
        mockMvc.perform(post("/api/cases/" + kcase.getId() + "/off-shelf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFF_SHELF"));

        // 5. 下架后再次查询，应该查不到该案例了
        mockMvc.perform(get("/api/cases")
                        .param("keyword", "偏离表"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }
}
