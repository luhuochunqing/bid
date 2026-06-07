package com.xiyu.bid.documenteditor.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.documenteditor.dto.DraftTreeUpsertNodeRequest;
import com.xiyu.bid.documenteditor.dto.DraftTreeUpsertRequest;
import com.xiyu.bid.documenteditor.entity.DocumentSection;
import com.xiyu.bid.documenteditor.entity.DocumentSectionLock;
import com.xiyu.bid.documenteditor.entity.DocumentStructure;
import com.xiyu.bid.documenteditor.entity.SectionType;
import com.xiyu.bid.documenteditor.repository.DocumentSectionLockRepository;
import com.xiyu.bid.documenteditor.repository.DocumentSectionRepository;
import com.xiyu.bid.documenteditor.repository.DocumentStructureRepository;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
@Transactional
class DocumentEditorDraftTreeIntegrationTest {

    private static final Long PROJECT_ID = 3001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentStructureRepository structureRepository;

    @Autowired
    private DocumentSectionRepository sectionRepository;

    @Autowired
    private DocumentSectionLockRepository lockRepository;

    @BeforeEach
    void setUp() {
        lockRepository.deleteAll();
        sectionRepository.deleteAll();
        structureRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void bulkTreeImport_ShouldCreateStructureAndPersistTreeOrderAndMetadata() throws Exception {
        DraftTreeUpsertRequest request = draftRequest("初始草稿内容", "技术方案内容", true);

        mockMvc.perform(post("/api/documents/{projectId}/editor/draft-tree", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.structureCreated").value(true))
                .andExpect(jsonPath("$.data.totalSections").value(3))
                .andExpect(jsonPath("$.data.createdSections").value(3))
                .andExpect(jsonPath("$.data.updatedSections").value(0))
                .andExpect(jsonPath("$.data.skippedSectionsCount").value(0));

        DocumentStructure structure = structureRepository.findByProjectId(PROJECT_ID).orElseThrow();
        assertThat(structure.getName()).isEqualTo("投标草稿");

        List<DocumentSection> sections = sectionRepository.findByStructureId(structure.getId());
        assertThat(sections).hasSize(3);

        DocumentSection root = sectionByTitle(sections, "商务响应");
        DocumentSection tech = sectionByTitle(sections, "技术方案");
        DocumentSection delivery = sectionByTitle(sections, "交付计划");

        assertThat(root.getOrderIndex()).isEqualTo(1);
        assertThat(root.getParentId()).isNull();
        assertThat(tech.getParentId()).isEqualTo(root.getId());
        assertThat(tech.getOrderIndex()).isEqualTo(1);
        assertThat(delivery.getParentId()).isEqualTo(root.getId());
        assertThat(delivery.getOrderIndex()).isEqualTo(2);

        JsonNode metadata = objectMapper.readTree(root.getMetadata());
        assertThat(metadata.path("sectionKey").asText()).isEqualTo("business-response");
        assertThat(metadata.path("runId").asText()).isEqualTo("run-001");
        assertThat(metadata.path("confidence").decimalValue()).isEqualByComparingTo(new BigDecimal("0.91"));
        assertThat(metadata.path("manual").asBoolean()).isFalse();
        assertThat(metadata.path("sourceReferences").size()).isEqualTo(1);
        assertThat(metadata.path("sourceReferences").get(0).asText()).isEqualTo("ai://brief/1");
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void bulkTreeImport_ShouldBeIdempotentWhenRepeated() throws Exception {
        DraftTreeUpsertRequest request = draftRequest("初始草稿内容", "技术方案内容", true);

        mockMvc.perform(post("/api/documents/{projectId}/editor/draft-tree", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        List<DocumentSection> firstRunSections = sectionRepository.findByStructureId(
                structureRepository.findByProjectId(PROJECT_ID).orElseThrow().getId()
        );
        Long rootId = sectionByTitle(firstRunSections, "商务响应").getId();
        Long childId = sectionByTitle(firstRunSections, "技术方案").getId();

        mockMvc.perform(post("/api/documents/{projectId}/editor/draft-tree", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.structureCreated").value(false))
                .andExpect(jsonPath("$.data.totalSections").value(3))
                .andExpect(jsonPath("$.data.createdSections").value(0))
                .andExpect(jsonPath("$.data.updatedSections").value(0))
                .andExpect(jsonPath("$.data.skippedSectionsCount").value(0));

        List<DocumentSection> secondRunSections = sectionRepository.findByStructureId(
                structureRepository.findByProjectId(PROJECT_ID).orElseThrow().getId()
        );
        assertThat(secondRunSections).hasSize(3);
        assertThat(sectionByTitle(secondRunSections, "商务响应").getId()).isEqualTo(rootId);
        assertThat(sectionByTitle(secondRunSections, "技术方案").getId()).isEqualTo(childId);
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void bulkTreeImport_ShouldPreserveExistingSourceMetadataOnUpdate() throws Exception {
        DraftTreeUpsertRequest initialRequest = draftRequest("初始草稿内容", "技术方案内容", false);

        mockMvc.perform(post("/api/documents/{projectId}/editor/draft-tree", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialRequest)))
                .andExpect(status().isOk());

        DraftTreeUpsertRequest updateRequest = DraftTreeUpsertRequest.builder()
                .structureName("投标草稿")
                .sections(List.of(
                        DraftTreeUpsertNodeRequest.builder()
                                .sectionKey("business-response")
                                .title("商务响应")
                                .sectionType(SectionType.CHAPTER)
                                .content("更新后的草稿内容")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/documents/{projectId}/editor/draft-tree", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdSections").value(0))
                .andExpect(jsonPath("$.data.updatedSections").value(1));

        DocumentSection root = sectionByTitle(
                sectionRepository.findByStructureId(structureRepository.findByProjectId(PROJECT_ID).orElseThrow().getId()),
                "商务响应"
        );
        assertThat(root.getContent()).isEqualTo("更新后的草稿内容");

        JsonNode metadata = objectMapper.readTree(root.getMetadata());
        assertThat(metadata.path("sectionKey").asText()).isEqualTo("business-response");
        assertThat(metadata.path("runId").asText()).isEqualTo("run-001");
        assertThat(metadata.path("sourceReferences").size()).isEqualTo(1);
        assertThat(metadata.path("sourceReferences").get(0).asText()).isEqualTo("ai://brief/1");
        assertThat(metadata.path("manual").asBoolean()).isFalse();
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void bulkTreeImport_ShouldSkipLockedSections() throws Exception {
        DraftTreeUpsertRequest initialRequest = draftRequest("初始草稿内容", "技术方案内容", false);

        mockMvc.perform(post("/api/documents/{projectId}/editor/draft-tree", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialRequest)))
                .andExpect(status().isOk());

        DocumentStructure structure = structureRepository.findByProjectId(PROJECT_ID).orElseThrow();
        DocumentSection lockedSection = sectionByTitle(sectionRepository.findByStructureId(structure.getId()), "技术方案");
        lockRepository.save(DocumentSectionLock.builder()
                .projectId(PROJECT_ID)
                .sectionId(lockedSection.getId())
                .locked(true)
                .lockedBy(99L)
                .lockedAt(LocalDateTime.now())
                .build());

        DraftTreeUpsertRequest updateRequest = DraftTreeUpsertRequest.builder()
                .structureName("投标草稿")
                .sections(List.of(
                        DraftTreeUpsertNodeRequest.builder()
                                .sectionKey("business-response")
                                .title("商务响应")
                                .sectionType(SectionType.CHAPTER)
                                .content("初始草稿内容")
                                .runId("run-001")
                                .sourceReferences(List.of("ai://brief/1"))
                                .confidence(new BigDecimal("0.91"))
                                .manual(false)
                                .children(List.of(
                                        DraftTreeUpsertNodeRequest.builder()
                                                .sectionKey("technical-plan")
                                                .title("技术方案")
                                                .sectionType(SectionType.SECTION)
                                                .content("已锁定的新内容")
                                                .build()
                                ))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/documents/{projectId}/editor/draft-tree", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skippedSectionsCount").value(1))
                .andExpect(jsonPath("$.data.skippedSections[0].title").value("技术方案"))
                .andExpect(jsonPath("$.data.skippedSections[0].reason").value("LOCKED"));

        DocumentSection reloadedLockedSection = sectionByTitle(
                sectionRepository.findByStructureId(structure.getId()),
                "技术方案"
        );
        assertThat(reloadedLockedSection.getContent()).isEqualTo("技术方案内容");

        assertThat(lockRepository.findBySectionId(reloadedLockedSection.getId())).isPresent();
    }

    private DraftTreeUpsertRequest draftRequest(String rootContent, String technicalContent, boolean includeDeliveryChild) {
        DraftTreeUpsertNodeRequest root = DraftTreeUpsertNodeRequest.builder()
                .sectionKey("business-response")
                .title("商务响应")
                .sectionType(SectionType.CHAPTER)
                .content(rootContent)
                .runId("run-001")
                .sourceReferences(List.of("ai://brief/1"))
                .confidence(new BigDecimal("0.91"))
                .manual(false)
                .children(includeDeliveryChild
                        ? List.of(
                                DraftTreeUpsertNodeRequest.builder()
                                        .sectionKey("technical-plan")
                                        .title("技术方案")
                                        .sectionType(SectionType.SECTION)
                                        .content(technicalContent)
                                        .build(),
                                DraftTreeUpsertNodeRequest.builder()
                                        .sectionKey("delivery-plan")
                                        .title("交付计划")
                                        .sectionType(SectionType.SECTION)
                                        .content("交付计划内容")
                                        .build()
                        )
                        : List.of(
                                DraftTreeUpsertNodeRequest.builder()
                                        .sectionKey("technical-plan")
                                        .title("技术方案")
                                        .sectionType(SectionType.SECTION)
                                        .content(technicalContent)
                                        .build()
                        ))
                .build();

        return DraftTreeUpsertRequest.builder()
                .structureName("投标草稿")
                .sections(List.of(root))
                .build();
    }

    private DocumentSection sectionByTitle(List<DocumentSection> sections, String title) {
        return sections.stream()
                .filter(section -> title.equals(section.getTitle()))
                .findFirst()
                .orElseThrow();
    }
}
