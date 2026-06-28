package com.xiyu.bid.task.service;

import com.xiyu.bid.entity.Task;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.task.dto.TaskAssignmentRequest;
import com.xiyu.bid.task.dto.TaskDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import com.xiyu.bid.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskAssignmentSupport assignmentSupport;
    @Mock
    private TaskDtoMapper taskDtoMapper;
    @Mock
    private TaskPermissionGuard taskPermissionGuard;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TaskService taskService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void createSystemTaskBypassesPermissionCheck() {
        TaskDTO taskDTO = TaskDTO.builder()
                .projectId(10L)
                .title("System Task")
                .build();

        TaskAssignmentSupport.AssignmentSnapshot snapshot = new TaskAssignmentSupport.AssignmentSnapshot(
                1L, "dept", "Dept Name", "role", "Role Name"
        );

        when(assignmentSupport.resolveAssignmentSnapshot(any(), isNull())).thenReturn(snapshot);
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task t = invocation.getArgument(0);
            t.setId(100L);
            return t;
        });

        TaskDTO expectedDto = TaskDTO.builder().id(100L).build();
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(taskDtoMapper.toDTO(any(Task.class), isNull(), isNull())).thenReturn(expectedDto);

        TaskDTO result = taskService.createSystemTask(taskDTO);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);

        // Verify taskPermissionGuard was NEVER called
        verify(taskPermissionGuard, never()).assertCanManageTask(any());
        
        // Verify createdBy is set to "system"
        verify(taskRepository).save(argThat(task -> "system".equals(task.getCreatedBy())));
    }
}
