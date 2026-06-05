package com.xiyu.bid.documentexport.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.documenteditor.dto.SectionCreateRequest;
import com.xiyu.bid.documenteditor.dto.StructureCreateRequest;
import com.xiyu.bid.documenteditor.entity.DocumentSection;
import com.xiyu.bid.documenteditor.entity.DocumentStructure;
import com.xiyu.bid.documenteditor.entity.SectionType;
import com.xiyu.bid.documenteditor.repository.DocumentSectionRepository;
import com.xiyu.bid.documenteditor.repository.DocumentStructureRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class DocumentExportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentStructureRepository structureRepository;

    @Autowired
    private DocumentSectionRepository sectionRepository;

    private Project project;
    private User adminUser;
    private User outsiderUser;

    @BeforeEach
    void setUp() {
        sectionRepository.deleteAll();
        structureRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = userRepository.save(User.builder()
                .username("export-admin")
                .password("XiyuDemo!2026")
                .email("export-admin@example.com")
                .fullName("导出管理员")
                .role(User.Role.ADMIN)
                .enabled(true)
                .build());

        outsiderUser = userRepository.save(User.builder()
                .username("export-outsider")
                .password("XiyuDemo!2026")
                .email("export-outsider@example.com")
                .fullName("无权用户")
                .role(User.Role.STAFF)
                .enabled(true)
                .build());

        project = projectRepository.save(Project.builder()
                .name("文档导出归档回归")
                .tenderId(7001L)
                .status(Project.Status.BIDDING)
                .managerId(adminUser.getId())
                .teamMembers(List.of(adminUser.getId()))
                .startDate(LocalDateTime.of(2026, 3, 12, 9, 0))
                .endDate(LocalDateTime.of(2026, 3, 20, 18, 0))
                .build());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void exportAndArchiveEndpoints_ShouldPersistMetadataAndArchiveProject() throws Exception {
        StructureCreateRequest structureRequest = new StructureCreateRequest();
        structureRequest.setProjectId(project.getId());
        structureRequest.setName("投标文件结构");

        String structureResponse = mockMvc.perform(post("/api/documents/{projectId}/editor/structure", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(structureRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long structureId = objectMapper.readTree(structureResponse).path("data").path("id").asLong();

        SectionCreateRequest sectionRequest = new SectionCreateRequest();
        sectionRequest.setStructureId(structureId);
        sectionRequest.setSectionType(SectionType.SECTION);
        sectionRequest.setTitle("技术方案");
        sectionRequest.setContent("这是技术方案正文");
        sectionRequest.setOrderIndex(1);

        mockMvc.perform(post("/api/documents/{projectId}/editor/sections", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sectionRequest)))
                .andExpect(status().isCreated());

        String exportResponse = mockMvc.perform(post("/api/documents/{projectId}/exports", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "format": "json",
                                  "exportedBy": %d,
                                  "exportedByName": "导出管理员"
                                }
                                """.formatted(adminUser.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileName").value("文档导出归档回归_document_export.json"))
                .andExpect(jsonPath("$.data.projectId").value(project.getId()))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("技术方案")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long exportId = objectMapper.readTree(exportResponse).path("data").path("id").asLong();
        assertThat(exportId).isPositive();

        mockMvc.perform(get("/api/documents/{projectId}/exports", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(exportId))
                .andExpect(jsonPath("$.data[0].exportedByName").value("导出管理员"));

        mockMvc.perform(post("/api/documents/{projectId}/archive", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "archivedBy": %d,
                                  "archivedByName": "导出管理员",
                                  "archiveReason": "投标结果已闭环"
                                }
                                """.formatted(adminUser.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.projectId").value(project.getId()))
                .andExpect(jsonPath("$.data.exportId").isNumber())
                .andExpect(jsonPath("$.data.archiveReason").value("投标结果已闭环"))
                .andExpect(jsonPath("$.data.caseSnapshot.projectId").value(project.getId()))
                .andExpect(jsonPath("$.data.caseSnapshot.archiveSummary").isNotEmpty());

        mockMvc.perform(get("/api/documents/{projectId}/archive-records", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].projectId").value(project.getId()))
                .andExpect(jsonPath("$.data[0].projectName").value("文档导出归档回归"))
                .andExpect(jsonPath("$.data[0].caseSnapshot.documentSnapshotText").value(org.hamcrest.Matchers.containsString("技术方案")));

        mockMvc.perform(get("/api/documents/{projectId}/case-snapshot", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(project.getId()))
                .andExpect(jsonPath("$.data.projectName").value("文档导出归档回归"))
                .andExpect(jsonPath("$.data.archiveSummary").value(org.hamcrest.Matchers.containsString("项目资料已完成归档")))
                .andExpect(jsonPath("$.data.documentSnapshotText").value(org.hamcrest.Matchers.containsString("技术方案")));

        assertThat(projectRepository.findById(project.getId())).isPresent();
        assertThat(projectRepository.findById(project.getId()).orElseThrow().getStage()).isEqualTo("CLOSED");
    }

    @Test
    @WithMockUser(username = "export-outsider", roles = {"STAFF"})
    void exportAndArchiveEndpoints_WhenProjectOutsideCurrentUserScope_ShouldReturnForbidden() throws Exception {
        DocumentStructure structure = structureRepository.save(DocumentStructure.builder()
                .projectId(project.getId())
                .name("投标文件结构")
                .build());
        sectionRepository.save(DocumentSection.builder()
                .structureId(structure.getId())
                .sectionType(SectionType.SECTION)
                .title("技术方案")
                .content("这是技术方案正文")
                .orderIndex(1)
                .build());

        mockMvc.perform(get("/api/documents/{projectId}/exports", project.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/documents/{projectId}/exports", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "format": "json",
                                  "exportedBy": %d,
                                  "exportedByName": "无权用户"
                                }
                                """.formatted(outsiderUser.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/documents/{projectId}/archive", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "archivedBy": %d,
                                  "archivedByName": "无权用户",
                                  "archiveReason": "越权归档"
                                }
                                """.formatted(outsiderUser.getId())))
                .andExpect(status().isForbidden());
    }
}
