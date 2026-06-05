// Input: document archive, historical snapshot, case promotion, and case search flows
// Output: end-to-end backend coverage for archive -> snapshot -> promote -> search
// Pos: Test/集成测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.casework.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.documenteditor.dto.SectionCreateRequest;
import com.xiyu.bid.documenteditor.dto.StructureCreateRequest;
import com.xiyu.bid.documenteditor.entity.SectionType;
import com.xiyu.bid.documenteditor.repository.DocumentSectionRepository;
import com.xiyu.bid.documenteditor.repository.DocumentStructureRepository;
import com.xiyu.bid.documentexport.repository.DocumentArchiveRecordRepository;
import com.xiyu.bid.documentexport.repository.DocumentExportFileRepository;
import com.xiyu.bid.documentexport.repository.DocumentExportRepository;
import com.xiyu.bid.entity.Case;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.historyproject.repository.HistoricalProjectSnapshotRecordRepository;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.repository.CaseRepository;
import com.xiyu.bid.repository.ProjectRepository;
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
class CaseArchivePromotionSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private DocumentStructureRepository structureRepository;

    @Autowired
    private DocumentSectionRepository sectionRepository;

    @Autowired
    private DocumentArchiveRecordRepository archiveRecordRepository;

    @Autowired
    private DocumentExportRepository exportRepository;

    @Autowired
    private DocumentExportFileRepository exportFileRepository;

    @Autowired
    private HistoricalProjectSnapshotRecordRepository snapshotRepository;

    private Project sourceProject;
    private Long structureId;
    private Long sectionId;

    @TestConfiguration
    static class TestBeans {
        @Bean(name = "passwordEncryptionUtil")
        @Primary
        PasswordEncryptionUtil passwordEncryptionUtil() {
            return new PasswordEncryptionUtil() {
                @Override
                public void initialize() {
                }

                @Override
                public String encrypt(String plainPassword) {
                    return plainPassword;
                }

                @Override
                public String decrypt(String encryptedPassword) {
                    return encryptedPassword;
                }

                @Override
                public boolean isKeyValid() {
                    return true;
                }
            };
        }
    }

    @BeforeEach
    void setUp() {
        exportFileRepository.deleteAll();
        exportRepository.deleteAll();
        archiveRecordRepository.deleteAll();
        snapshotRepository.deleteAll();
        sectionRepository.deleteAll();
        structureRepository.deleteAll();
        caseRepository.deleteAll();
        projectRepository.deleteAll();

        sourceProject = projectRepository.save(Project.builder()
                .name("归档闭环源项目")
                .tenderId(93001L)
                .status(Project.Status.BIDDING)
                .managerId(9301L)
                .teamMembers(List.of(9301L))
                .sourceModule("智慧园区")
                .sourceCustomer("国资客户")
                .sourceReasoningSummary("归档测试摘要")
                .build());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void archivePromoteAndSearch_ShouldPreserveBodyOnlyKeywordAndSearchIt() throws Exception {
        String bodyKeyword = "闭环关键字-" + System.currentTimeMillis();

        StructureCreateRequest structureRequest = new StructureCreateRequest();
        structureRequest.setProjectId(sourceProject.getId());
        structureRequest.setName("归档闭环结构");

        String structureResponse = mockMvc.perform(post("/api/documents/{projectId}/editor/structure", sourceProject.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(structureRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        structureId = objectMapper.readTree(structureResponse).path("data").path("id").asLong();

        String prefix = "正文摘要".repeat(90);
        SectionCreateRequest sectionRequest = new SectionCreateRequest();
        sectionRequest.setStructureId(structureId);
        sectionRequest.setSectionType(SectionType.SECTION);
        sectionRequest.setTitle("技术方案");
        sectionRequest.setContent(prefix + " " + bodyKeyword + " 结尾说明");
        sectionRequest.setOrderIndex(1);

        String sectionResponse = mockMvc.perform(post("/api/documents/{projectId}/editor/sections", sourceProject.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sectionRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        sectionId = objectMapper.readTree(sectionResponse).path("data").path("id").asLong();

        mockMvc.perform(post("/api/documents/{projectId}/archive", sourceProject.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "archivedBy": %d,
                                  "archivedByName": "归档管理员",
                                  "archiveReason": "归档闭环测试"
                                }
                                """.formatted(9301L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.projectId").value(sourceProject.getId()))
                .andExpect(jsonPath("$.data.caseSnapshot.documentSnapshotText").value(org.hamcrest.Matchers.containsString(bodyKeyword)))
                .andExpect(jsonPath("$.data.caseSnapshot.archiveSummary").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(bodyKeyword))));

        mockMvc.perform(get("/api/documents/{projectId}/case-snapshot", sourceProject.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentSnapshotText").value(org.hamcrest.Matchers.containsString(bodyKeyword)))
                .andExpect(jsonPath("$.data.archiveSummary").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(bodyKeyword))));

        String promoteResponse = mockMvc.perform(post("/api/knowledge/cases/promote-from-project")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d
                                }
                                """.formatted(sourceProject.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sourceProjectId").value(sourceProject.getId()))
                .andExpect(jsonPath("$.data.productLine").value("智慧园区"))
                .andExpect(jsonPath("$.data.archiveSummary").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(bodyKeyword))))
                .andExpect(jsonPath("$.data.documentSnapshotText").value(org.hamcrest.Matchers.containsString(bodyKeyword)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode promoteData = objectMapper.readTree(promoteResponse).path("data");
        long promotedCaseId = promoteData.path("id").asLong();

        mockMvc.perform(get("/api/knowledge/cases")
                        .param("keyword", bodyKeyword)
                        .param("page", "1")
                        .param("pageSize", "10")
                        .param("sort", "latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(promotedCaseId))
                .andExpect(jsonPath("$.data.items[0].searchDocument").value(org.hamcrest.Matchers.containsString(bodyKeyword)));

        Case promotedCase = caseRepository.findById(promotedCaseId).orElseThrow();
        assertThat(promotedCase.getSourceProjectId()).isEqualTo(sourceProject.getId());
        assertThat(promotedCase.getArchiveSummary()).doesNotContain(bodyKeyword);
        assertThat(promotedCase.getDocumentSnapshotText()).contains(bodyKeyword);
        assertThat(promotedCase.getSearchDocument()).contains(bodyKeyword);

        var snapshot = snapshotRepository.findTopByProjectIdOrderByCapturedAtDesc(sourceProject.getId())
                .orElseThrow();
        assertThat(snapshot.getDocumentSnapshotText()).contains(bodyKeyword);
        assertThat(snapshot.getArchiveSummary()).doesNotContain(bodyKeyword);

        assertThat(archiveRecordRepository.findByProjectIdOrderByArchivedAtDesc(sourceProject.getId())).hasSize(1);
        assertThat(sectionId).isNotNull();
        assertThat(structureId).isNotNull();
    }
}
