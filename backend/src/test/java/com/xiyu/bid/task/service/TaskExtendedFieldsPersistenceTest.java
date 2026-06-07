package com.xiyu.bid.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.RoleProfileService;
import com.xiyu.bid.task.dto.TaskDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that the dynamic extended-fields map round-trips through TaskDTO ↔ Task entity
 * inside TaskService. Storage policy: extended fields are persisted as a JSON string in
 * {@code tasks.extended_fields_json} and exposed to callers as {@code Map<String, Object>}.
 *
 * <p>We mirror {@link TaskContentPersistenceTest}'s mock-based pattern – a pure in-memory
 * concern, no Spring context required.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService extendedFields JSON 持久化测试")
class TaskExtendedFieldsPersistenceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private RoleProfileService roleProfileService;

    @Mock
    private TaskHistoryRecorder taskHistoryRecorder;

    private TaskService taskService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(projectRepository.existsById(any(Long.class))).thenReturn(true);
        TaskAssignmentSupport assignmentSupport = new TaskAssignmentSupport(
                userRepository,
                taskRepository,
                projectAccessScopeService,
                roleProfileService
        );
        taskService = new TaskService(
                taskRepository,
                projectAccessScopeService,
                projectRepository,
                assignmentSupport,
                new TaskDtoMapper(objectMapper),
                taskHistoryRecorder
        );
    }

    @Test
    @DisplayName("createTask 将 TaskDTO.extendedFields 序列化为 Task.extendedFieldsJson")
    void createTask_serializesExtendedFieldsToJson() throws Exception {
        when(projectRepository.existsById(10L)).thenReturn(true);
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task toSave = invocation.getArgument(0);
            toSave.setId(99L);
            return toSave;
        });

        Map<String, Object> extended = new LinkedHashMap<>();
        extended.put("tender_chapter", "第3章");
        extended.put("tech_weight", 40);

        TaskDTO input = TaskDTO.builder()
                .projectId(10L)
                .title("写标书")
                .extendedFields(extended)
                .build();

        TaskDTO created = taskService.createTask(input);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        String savedJson = captor.getValue().getExtendedFieldsJson();
        assertThat(savedJson)
                .as("Task entity must receive serialized JSON")
                .isNotNull()
                .contains("tender_chapter", "第3章", "tech_weight", "40");

        // Round-trip parses back to the original keys/values.
        Map<String, Object> roundTrip = objectMapper.readValue(savedJson, Map.class);
        assertThat(roundTrip)
                .containsEntry("tender_chapter", "第3章")
                .containsEntry("tech_weight", 40);

        assertThat(created.getExtendedFields())
                .as("returned DTO must echo extendedFields back to caller")
                .containsEntry("tender_chapter", "第3章")
                .containsEntry("tech_weight", 40);
    }

    @Test
    @DisplayName("getTaskById 将 extendedFieldsJson 反序列化为 Map")
    void getTaskById_deserializesExtendedFields() {
        when(projectRepository.existsById(10L)).thenReturn(true);
        Task stored = Task.builder()
                .id(1L)
                .projectId(10L)
                .title("写标书")
                .status(Task.Status.TODO)
                .priority(Task.Priority.MEDIUM)
                .extendedFieldsJson("{\"foo\":\"bar\"}")
                .build();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(stored));

        TaskDTO loaded = taskService.getTaskById(1L);

        assertThat(loaded.getExtendedFields())
                .as("DTO must expose deserialized map")
                .isNotNull()
                .containsEntry("foo", "bar");
    }

    @Test
    @DisplayName("getTaskById 当 extendedFieldsJson 为空时返回空 Map（而非 null）")
    void getTaskById_emptyMapWhenJsonNull() {
        when(projectRepository.existsById(10L)).thenReturn(true);
        Task stored = Task.builder()
                .id(2L)
                .projectId(10L)
                .title("无扩展字段任务")
                .status(Task.Status.TODO)
                .priority(Task.Priority.MEDIUM)
                .extendedFieldsJson(null)
                .build();
        when(taskRepository.findById(2L)).thenReturn(Optional.of(stored));

        TaskDTO loaded = taskService.getTaskById(2L);

        assertThat(loaded.getExtendedFields())
                .as("frontend should not need null-check; service returns empty map")
                .isNotNull()
                .isEmpty();
    }
}
