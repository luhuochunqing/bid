package com.xiyu.bid.documenteditor.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.documenteditor.dto.SectionAssignmentRequest;
import com.xiyu.bid.documenteditor.dto.SectionLockRequest;
import com.xiyu.bid.documenteditor.dto.SectionReminderRequest;
import com.xiyu.bid.documenteditor.entity.DocumentSection;
import com.xiyu.bid.documenteditor.entity.DocumentStructure;
import com.xiyu.bid.documenteditor.entity.SectionType;
import com.xiyu.bid.documenteditor.repository.DocumentReminderRepository;
import com.xiyu.bid.documenteditor.repository.DocumentSectionAssignmentRepository;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class DocumentWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentStructureRepository structureRepository;

    @Autowired
    private DocumentSectionRepository sectionRepository;

    @Autowired
    private DocumentSectionAssignmentRepository assignmentRepository;

    @Autowired
    private DocumentSectionLockRepository lockRepository;

    @Autowired
    private DocumentReminderRepository reminderRepository;

    private DocumentStructure structure;
    private DocumentSection section;

    @BeforeEach
    void setUp() {
        reminderRepository.deleteAll();
        lockRepository.deleteAll();
        assignmentRepository.deleteAll();
        sectionRepository.deleteAll();
        structureRepository.deleteAll();

        structure = structureRepository.save(DocumentStructure.builder()
                .projectId(3001L)
                .name("投标文档结构")
                .build());

        section = sectionRepository.save(DocumentSection.builder()
                .structureId(structure.getId())
                .sectionType(SectionType.CHAPTER)
                .title("商务响应")
                .content("初始内容")
                .orderIndex(1)
                .build());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void assignSection_ShouldPersistAssignmentAndReturnEnrichedSection() throws Exception {
        SectionAssignmentRequest request = SectionAssignmentRequest.builder()
                .sectionId(section.getId())
                .owner("李总")
                .assignedBy(9001L)
                .dueDate(LocalDate.of(2026, 3, 20))
                .build();

        mockMvc.perform(post("/api/documents/{projectId}/editor/assignments", structure.getProjectId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(section.getId().intValue()))
                .andExpect(jsonPath("$.data.owner").value("李总"))
                .andExpect(jsonPath("$.data.assignedBy").value(9001))
                .andExpect(jsonPath("$.data.dueDate").value("2026-03-20"))
                .andExpect(jsonPath("$.data.locked").value(false));

        var savedAssignment = assignmentRepository.findBySectionId(section.getId()).orElseThrow();
        assertThat(savedAssignment.getProjectId()).isEqualTo(structure.getProjectId());
        assertThat(savedAssignment.getOwner()).isEqualTo("李总");
        assertThat(savedAssignment.getAssignedBy()).isEqualTo(9001L);
        assertThat(savedAssignment.getDueDate()).isEqualTo(LocalDate.of(2026, 3, 20));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void updateLock_ShouldPersistLockAndReturnSectionState() throws Exception {
        SectionLockRequest request = SectionLockRequest.builder()
                .sectionId(section.getId())
                .locked(true)
                .userId(8001L)
                .build();

        mockMvc.perform(post("/api/documents/{projectId}/editor/locks", structure.getProjectId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(section.getId().intValue()))
                .andExpect(jsonPath("$.data.locked").value(true))
                .andExpect(jsonPath("$.data.lockedBy").value(8001));

        var savedLock = lockRepository.findBySectionId(section.getId()).orElseThrow();
        assertThat(savedLock.getProjectId()).isEqualTo(structure.getProjectId());
        assertThat(savedLock.getLocked()).isTrue();
        assertThat(savedLock.getLockedBy()).isEqualTo(8001L);
        assertThat(savedLock.getLockedAt()).isNotNull();
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void createReminder_ShouldPersistReminderAndReturnCreatedPayload() throws Exception {
        SectionReminderRequest request = SectionReminderRequest.builder()
                .sectionId(section.getId())
                .recipient("王经理")
                .remindedBy(7001L)
                .message("请今天确认商务条款")
                .build();

        mockMvc.perform(post("/api/documents/{projectId}/editor/reminders", structure.getProjectId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sectionId").value(section.getId().intValue()))
                .andExpect(jsonPath("$.data.recipient").value("王经理"))
                .andExpect(jsonPath("$.data.remindedBy").value(7001))
                .andExpect(jsonPath("$.data.message").value("请今天确认商务条款"));

        assertThat(reminderRepository.findAll())
                .singleElement()
                .satisfies(reminder -> {
                    assertThat(reminder.getProjectId()).isEqualTo(structure.getProjectId());
                    assertThat(reminder.getSectionId()).isEqualTo(section.getId());
                    assertThat(reminder.getRecipient()).isEqualTo("王经理");
                    assertThat(reminder.getRemindedBy()).isEqualTo(7001L);
                });
    }
}
