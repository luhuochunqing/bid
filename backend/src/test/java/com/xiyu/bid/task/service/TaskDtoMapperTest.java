package com.xiyu.bid.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Verifies that TaskDtoMapper correctly maps task attachments from project_documents
 * into TaskDTO.attachments when linkedEntityType='TASK'.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskDtoMapper 任务附件映射测试")
class TaskDtoMapperTest {

    @Mock
    private ProjectDocumentRepository projectDocumentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("toDTO 将 linkedEntityType='TASK' 的 project_documents 映射为 attachments")
    void toDTO_mapsTaskAttachments() {
        TaskDtoMapper mapper = new TaskDtoMapper(objectMapper, projectDocumentRepository);

        Task task = Task.builder()
                .id(1L)
                .projectId(10L)
                .title("测试任务")
                .status(Task.Status.TODO)
                .priority(Task.Priority.MEDIUM)
                .build();

        ProjectDocument doc = ProjectDocument.builder()
                .id(100L)
                .projectId(10L)
                .name("参考文档.pdf")
                .size("2.5MB")
                .fileType("application/pdf")
                .documentCategory("TASK_ATTACHMENT")
                .linkedEntityType("TASK")
                .linkedEntityId(1L)
                .fileUrl("/files/100")
                .uploaderId(5L)
                .uploaderName("张三")
                .createdAt(LocalDateTime.now())
                .build();

        when(projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc("TASK", 1L))
                .thenReturn(List.of(doc));

        var dto = mapper.toDTO(task);

        assertThat(dto.getAttachments())
                .as("TaskDTO.attachments 应包含关联的 project_documents")
                .isNotNull()
                .hasSize(1);

        ProjectDocumentDTO attachment = dto.getAttachments().get(0);
        assertThat(attachment.getId()).isEqualTo(100L);
        assertThat(attachment.getName()).isEqualTo("参考文档.pdf");
        assertThat(attachment.getFileUrl()).isEqualTo("/files/100");
        assertThat(attachment.getUploader()).isEqualTo("张三");
    }

    @Test
    @DisplayName("toDTO 不把 TASK_DELIVERABLE 项目文档混入任务附件")
    void toDTO_excludesTaskDeliverableDocumentsFromAttachments() {
        TaskDtoMapper mapper = new TaskDtoMapper(objectMapper, projectDocumentRepository);

        Task task = Task.builder()
                .id(5L)
                .projectId(10L)
                .title("测试任务")
                .status(Task.Status.TODO)
                .priority(Task.Priority.MEDIUM)
                .build();

        ProjectDocument attachment = ProjectDocument.builder()
                .id(103L)
                .projectId(10L)
                .name("任务附件.docx")
                .documentCategory("TASK_ATTACHMENT")
                .linkedEntityType("TASK")
                .linkedEntityId(5L)
                .build();
        ProjectDocument deliverable = ProjectDocument.builder()
                .id(104L)
                .projectId(10L)
                .name("交付物.docx")
                .documentCategory("TASK_DELIVERABLE")
                .linkedEntityType("TASK")
                .linkedEntityId(5L)
                .build();

        when(projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc("TASK", 5L))
                .thenReturn(List.of(deliverable, attachment));

        var dto = mapper.toDTO(task);

        assertThat(dto.getAttachments())
                .extracting(ProjectDocumentDTO::getName)
                .containsExactly("任务附件.docx");
    }

    @Test
    @DisplayName("toDTO 当 task 无附件时返回空列表")
    void toDTO_returnsEmptyListWhenNoAttachments() {
        TaskDtoMapper mapper = new TaskDtoMapper(objectMapper, projectDocumentRepository);

        Task task = Task.builder()
                .id(2L)
                .projectId(10L)
                .title("无附件任务")
                .status(Task.Status.TODO)
                .priority(Task.Priority.MEDIUM)
                .build();

        when(projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc("TASK", 2L))
                .thenReturn(Collections.emptyList());

        var dto = mapper.toDTO(task);

        assertThat(dto.getAttachments())
                .as("TaskDTO.attachments 应为空列表而非 null")
                .isNotNull()
                .isEmpty();
    }

    @Test
    @DisplayName("toDTO 只返回 TASK_ATTACHMENT 分类文档")
    void toDTO_onlyReturnsTaskAttachmentDocuments() {
        TaskDtoMapper mapper = new TaskDtoMapper(objectMapper, projectDocumentRepository);

        Task task = Task.builder()
                .id(3L)
                .projectId(10L)
                .title("测试任务")
                .status(Task.Status.TODO)
                .priority(Task.Priority.MEDIUM)
                .build();

        ProjectDocument taskDoc = ProjectDocument.builder()
                .id(101L)
                .projectId(10L)
                .name("任务附件.docx")
                .documentCategory("TASK_ATTACHMENT")
                .linkedEntityType("TASK")
                .linkedEntityId(3L)
                .build();
        ProjectDocument uncategorizedDoc = ProjectDocument.builder()
                .id(102L)
                .projectId(10L)
                .name("历史未分类文档.docx")
                .linkedEntityType("TASK")
                .linkedEntityId(3L)
                .build();
        ProjectDocument otherCategoryDoc = ProjectDocument.builder()
                .id(103L)
                .projectId(10L)
                .name("其他任务文档.docx")
                .documentCategory("OTHER")
                .linkedEntityType("TASK")
                .linkedEntityId(3L)
                .build();

        when(projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc("TASK", 3L))
                .thenReturn(List.of(otherCategoryDoc, uncategorizedDoc, taskDoc));

        var dto = mapper.toDTO(task);

        assertThat(dto.getAttachments())
                .extracting(ProjectDocumentDTO::getName)
                .containsExactly("任务附件.docx");
    }

    @Test
    @DisplayName("toDTO(task, assigneeName) 也映射 attachments")
    void toDTO_withAssigneeName_mapsAttachments() {
        TaskDtoMapper mapper = new TaskDtoMapper(objectMapper, projectDocumentRepository);

        Task task = Task.builder()
                .id(4L)
                .projectId(10L)
                .title("测试任务")
                .status(Task.Status.TODO)
                .priority(Task.Priority.MEDIUM)
                .build();

        ProjectDocument doc = ProjectDocument.builder()
                .id(102L)
                .name("附件.xlsx")
                .documentCategory("TASK_ATTACHMENT")
                .linkedEntityType("TASK")
                .linkedEntityId(4L)
                .build();

        when(projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc("TASK", 4L))
                .thenReturn(List.of(doc));

        var dto = mapper.toDTO(task, "李四");

        assertThat(dto.getAttachments()).hasSize(1);
        assertThat(dto.getAssigneeName()).isEqualTo("李四");
    }

    @Test
    @DisplayName("toDTOs 批量映射时每个 task 都加载自己的 attachments")
    void toDTOs_mapsAttachmentsForEachTask() {
        TaskDtoMapper mapper = new TaskDtoMapper(objectMapper, projectDocumentRepository);

        Task task1 = Task.builder().id(1L).projectId(10L).title("任务1").status(Task.Status.TODO).priority(Task.Priority.MEDIUM).build();
        Task task2 = Task.builder().id(2L).projectId(10L).title("任务2").status(Task.Status.TODO).priority(Task.Priority.MEDIUM).build();

        ProjectDocument doc1 = ProjectDocument.builder().id(201L).name("附件1.pdf").documentCategory("TASK_ATTACHMENT").linkedEntityType("TASK").linkedEntityId(1L).build();
        ProjectDocument doc2 = ProjectDocument.builder().id(202L).name("附件2.pdf").documentCategory("TASK_ATTACHMENT").linkedEntityType("TASK").linkedEntityId(2L).build();

        when(projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc("TASK", 1L))
                .thenReturn(List.of(doc1));
        when(projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc("TASK", 2L))
                .thenReturn(List.of(doc2));

        var dtos = mapper.toDTOs(List.of(task1, task2));

        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getAttachments()).hasSize(1);
        assertThat(dtos.get(0).getAttachments().get(0).getName()).isEqualTo("附件1.pdf");
        assertThat(dtos.get(1).getAttachments()).hasSize(1);
        assertThat(dtos.get(1).getAttachments().get(0).getName()).isEqualTo("附件2.pdf");
    }
}
