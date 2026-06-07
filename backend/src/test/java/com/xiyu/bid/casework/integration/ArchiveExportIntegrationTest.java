package com.xiyu.bid.casework.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.casework.application.ProjectClosedEvent;
import com.xiyu.bid.casework.dto.ProjectArchiveQuery;
import com.xiyu.bid.casework.infrastructure.*;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ArchiveExportIntegrationTest {

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
    private ProjectScoreDraftRepository scoreDraftRepository;

    @Autowired
    private KnowledgeCaseRepository knowledgeCaseRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    private User adminUser;
    private Project project;
    private ProjectArchive archive;

    @BeforeEach
    void setUp() throws Exception {
        knowledgeCaseRepository.deleteAll();
        fileRepository.deleteAll();
        archiveRepository.deleteAll();
        scoreDraftRepository.deleteAll();
        projectRepository.deleteAll();
        tenderRepository.deleteAll();
        userRepository.deleteAll();

        // 1. 创建用户
        adminUser = new User();
        adminUser.setUsername("admin_user");
        adminUser.setEmail("admin@test.com");
        adminUser.setFullName("管理员");
        adminUser.setPassword("password");
        adminUser.setRole(User.Role.ADMIN);
        adminUser.setEnabled(true);
        userRepository.save(adminUser);

        // 2. 创建标讯与项目
        Tender tender = Tender.builder()
                .title("导出测试标讯")
                .projectType("综合")
                .status(Tender.Status.WON)
                .build();
        tender = tenderRepository.save(tender);

        project = Project.builder()
                .name("导出测试项目")
                .tenderId(tender.getId())
                .status(Project.Status.BIDDING)
                .managerId(adminUser.getId())
                .build();
        project = projectRepository.save(project);

        // 3. 创建归档台账
        archive = new ProjectArchive();
        archive.setProjectId(project.getId());
        archive.setProjectName(project.getName());
        archive.setArchiveStatus("ACTIVE");
        archive = archiveRepository.save(archive);

        // 创建临时物理文件，用于流式 ZIP 测试
        Path tempFile = Files.createTempFile("bid_document_mock_", ".txt");
        Files.writeString(tempFile, "这是测试标书的文本内容。项目经理王工具有高级职称，有8年研发经验。");

        // 4. 创建归档文件记录
        ArchiveFile file = new ArchiveFile();
        file.setArchiveId(archive.getId());
        file.setFileName("标书正文.txt");
        file.setDocumentCategory("BID");
        file.setFilePath(tempFile.toAbsolutePath().toString());
        file.setFileSize(Files.size(tempFile));
        file.setUploadUserId(adminUser.getId());
        file.setUploadUserName(adminUser.getUsername());
        fileRepository.save(file);

        // 5. 创建评分项草稿
        ProjectScoreDraft draft = new ProjectScoreDraft();
        draft.setProjectId(project.getId());
        draft.setSourceFileName("招标书.pdf");
        draft.setCategory("TECHNICAL");
        draft.setScoreItemTitle("项目经理资质要求");
        draft.setScoreRuleText("项目经理需具备高级职称，且有5年以上相关工作经验。");
        draft.setTaskAction("WRITE");
        draft.setGeneratedTaskTitle("编写项目经理资质说明");
        draft.setGeneratedTaskDescription("根据评分要求编写");
        draft.setSuggestedDeliverables("资质证明材料");
        draft.setStatus(ProjectScoreDraft.Status.DRAFT);
        draft.setSourceTableIndex(0);
        draft.setSourceRowIndex(0);
        scoreDraftRepository.save(draft);
    }

    @org.junit.jupiter.api.Disabled("Controller endpoint not yet implemented")
    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    void exportExcel_ShouldReturnStreamedFile() throws Exception {
        ProjectArchiveQuery query = new ProjectArchiveQuery();
        mockMvc.perform(post("/api/archive/export-excel")
                        .with(user("admin_user").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(query)))
                .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Disabled("Controller endpoint not yet implemented")
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    void exportZip_ShouldReturnStreamedZip() throws Exception {
        ProjectArchiveQuery query = new ProjectArchiveQuery();
        MvcResult mvcResult = mockMvc.perform(post("/api/archive/export-zip")
                        .with(user("admin_user").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(query)))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult finalResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn();

        byte[] content = finalResult.getResponse().getContentAsByteArray();
        assertThat(content).isNotEmpty();
    }

    @Test
    @Disabled("AI case slicing requires external AI service which is not available in test environment")
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    void projectClosedEvent_ShouldTriggerAiCaseSlicing() throws Exception {
        // 1. 验证目前没有切片案例
        assertThat(knowledgeCaseRepository.count()).isEqualTo(0);

        // 2. 发布结项事件
        eventPublisher.publishEvent(new ProjectClosedEvent(this, project.getId(), project.getName()));

        // 3. 轮询等待异步任务完成
        int attempts = 0;
        while (knowledgeCaseRepository.count() == 0 && attempts < 10) {
            Thread.sleep(200);
            attempts++;
        }

        // 4. 验证已成功通过监听器进行 AI 切片并落库
        List<KnowledgeCase> cases = knowledgeCaseRepository.findAll();
        assertThat(cases).isNotEmpty();
        KnowledgeCase kcase = cases.get(0);
        assertThat(kcase.getSourceProjectId()).isEqualTo(project.getId());
        assertThat(kcase.getScoringPointTitle()).isEqualTo("项目经理资质要求");
        assertThat(kcase.getResponseText()).contains("项目经理");
    }
}
