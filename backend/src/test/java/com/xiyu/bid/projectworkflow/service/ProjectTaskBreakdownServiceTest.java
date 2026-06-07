package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.documenteditor.entity.DocumentSection;
import com.xiyu.bid.documenteditor.entity.DocumentStructure;
import com.xiyu.bid.documenteditor.repository.DocumentSectionRepository;
import com.xiyu.bid.documenteditor.repository.DocumentStructureRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.projectworkflow.core.TaskBreakdownPolicy;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskViewDTO;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.task.service.TaskHistoryRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectTaskBreakdownServiceTest {

    private ProjectTaskRequirementSourceGateway requirementSourceGateway;
    private DocumentStructureRepository documentStructureRepository;
    private DocumentSectionRepository documentSectionRepository;
    private TaskRepository taskRepository;
    private ProjectTaskBreakdownService service;

    @BeforeEach
    void setUp() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        taskRepository = mock(TaskRepository.class);
        requirementSourceGateway = mock(ProjectTaskRequirementSourceGateway.class);
        documentStructureRepository = mock(DocumentStructureRepository.class);
        documentSectionRepository = mock(DocumentSectionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        ProjectWorkflowGuardService guardService = new ProjectWorkflowGuardService(
                projectRepository,
                mock(ProjectAccessScopeService.class),
                taskRepository,
                mock(ProjectDocumentRepository.class),
                mock(ProjectScoreDraftRepository.class)
        );
        ProjectTaskWorkflowService taskWorkflowService = new ProjectTaskWorkflowService(guardService, taskRepository, userRepository, new com.fasterxml.jackson.databind.ObjectMapper(), mock(TaskHistoryRecorder.class), mock(com.xiyu.bid.projectworkflow.service.ProjectTaskDeliverableCollector.class));
        ProjectTaskBreakdownSourceReader sourceReader = new ProjectTaskBreakdownSourceReader(
                requirementSourceGateway,
                documentStructureRepository,
                documentSectionRepository
        );
        service = new ProjectTaskBreakdownService(
                guardService,
                sourceReader,
                new ProjectTaskBreakdownTaskCreator(taskWorkflowService, taskRepository),
                taskRepository
        );
        when(projectRepository.findById(1001L)).thenReturn(Optional.of(Project.builder()
                .id(1001L)
                .status(Project.Status.INITIATED)
                .deadline(LocalDate.of(2026, 5, 20))
                .build()));
    }

    @Test
    void decomposeProjectTasks_ShouldCreateTasksFromTenderRequirementItems() {
        when(requirementSourceGateway.latestRequirementSourcesForProject(1001L)).thenReturn(List.of(
                requirement("commercial", "商务条款响应", "按招标文件完成商务偏离表"),
                requirement("technical", "技术实施方案", "提交平台对接和实施计划")
        ));
        when(documentStructureRepository.findByProjectId(1001L)).thenThrow(new AssertionError("有需求项时不应回退章节来源"));
        List<Task> persistedTasks = new ArrayList<>();
        when(taskRepository.findByProjectId(1001L)).thenAnswer(invocation -> List.copyOf(persistedTasks));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> saveTo(persistedTasks, invocation.getArgument(0)));

        List<ProjectTaskViewDTO> tasks = service.decomposeProjectTasks(1001L);

        assertThat(tasks).extracting(ProjectTaskViewDTO::getName)
                .containsExactly("商务标：商务条款响应", "技术标：技术实施方案");
        verify(taskRepository, times(2)).save(any(Task.class));
    }

    @Test
    void decomposeProjectTasks_ShouldFallbackToDocumentSectionsWhenRequirementsAreMissing() {
        when(requirementSourceGateway.latestRequirementSourcesForProject(1001L)).thenReturn(List.of());
        when(documentStructureRepository.findByProjectId(1001L)).thenReturn(Optional.of(DocumentStructure.builder()
                .id(3001L)
                .projectId(1001L)
                .build()));
        when(documentSectionRepository.findByStructureId(3001L)).thenReturn(List.of(DocumentSection.builder()
                .id(4001L)
                .structureId(3001L)
                .parentId(null)
                .title("商务标")
                .content("商务条款和资质响应")
                .orderIndex(1)
                .build()));
        List<Task> persistedTasks = new ArrayList<>();
        when(taskRepository.findByProjectId(1001L)).thenAnswer(invocation -> List.copyOf(persistedTasks));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> saveTo(persistedTasks, invocation.getArgument(0)));

        List<ProjectTaskViewDTO> tasks = service.decomposeProjectTasks(1001L);

        assertThat(tasks).extracting(ProjectTaskViewDTO::getName)
                .containsExactly("商务标：商务标");
    }

    @Test
    void decomposeProjectTasks_ShouldReturnExistingTasksWhenGeneratedTitleAlreadyExists() {
        Task existingTask = Task.builder()
                .id(9301L)
                .projectId(1001L)
                .title("商务标：商务条款响应")
                .description("已有任务")
                .status(Task.Status.TODO)
                .priority(Task.Priority.MEDIUM)
                .build();
        when(requirementSourceGateway.latestRequirementSourcesForProject(1001L)).thenReturn(List.of(
                requirement("commercial", "商务条款响应", "按招标文件完成商务偏离表")
        ));
        when(taskRepository.findByProjectId(1001L)).thenReturn(List.of(existingTask));

        List<ProjectTaskViewDTO> tasks = service.decomposeProjectTasks(1001L);

        assertThat(tasks).extracting(ProjectTaskViewDTO::getName)
                .containsExactly("商务标：商务条款响应");
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void decomposeProjectTasks_ShouldBeIdempotentAcrossRepeatedRequests() {
        when(requirementSourceGateway.latestRequirementSourcesForProject(1001L)).thenReturn(List.of(
                requirement("commercial", "商务条款响应", "按招标文件完成商务偏离表")
        ));
        List<Task> persistedTasks = new ArrayList<>();
        when(taskRepository.findByProjectId(1001L)).thenAnswer(invocation -> List.copyOf(persistedTasks));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> saveTo(persistedTasks, invocation.getArgument(0)));

        service.decomposeProjectTasks(1001L);
        clearInvocations(taskRepository);

        List<ProjectTaskViewDTO> tasks = service.decomposeProjectTasks(1001L);

        assertThat(tasks).extracting(ProjectTaskViewDTO::getName)
                .containsExactly("商务标：商务条款响应");
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void decomposeProjectTasks_ShouldNotDuplicateTasksWhenRequestsRunConcurrently() throws Exception {
        when(requirementSourceGateway.latestRequirementSourcesForProject(1001L)).thenReturn(List.of(
                requirement("commercial", "商务条款响应", "按招标文件完成商务偏离表")
        ));
        List<Task> persistedTasks = new ArrayList<>();
        CountDownLatch firstReadStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstRead = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        when(taskRepository.findByProjectId(1001L)).thenAnswer(invocation -> {
            firstReadStarted.countDown();
            releaseFirstRead.await(2, TimeUnit.SECONDS);
            synchronized (persistedTasks) {
                return List.copyOf(persistedTasks);
            }
        });
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            synchronized (persistedTasks) {
                return saveTo(persistedTasks, invocation.getArgument(0));
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> runAndCaptureFailure(failure));
            assertThat(firstReadStarted.await(1, TimeUnit.SECONDS)).isTrue();
            executor.submit(() -> runAndCaptureFailure(failure));
            releaseFirstRead.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(failure.get()).isNull();
        assertThat(persistedTasks).extracting(Task::getTitle)
                .containsExactly("商务标：商务条款响应");
        verify(taskRepository, timeout(1000).times(1)).save(any(Task.class));
    }

    @Test
    void decomposeProjectTasks_ShouldRunInsideTransaction() throws NoSuchMethodException {
        Method method = ProjectTaskBreakdownService.class.getMethod("decomposeProjectTasks", Long.class);

        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void decomposeProjectTasks_ShouldRejectWhenNoBreakdownSourcesExist() {
        when(requirementSourceGateway.latestRequirementSourcesForProject(1001L)).thenReturn(List.of());
        when(documentStructureRepository.findByProjectId(1001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decomposeProjectTasks(1001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未找到可用于拆解任务的标书拆解结果");
    }

    private TaskBreakdownPolicy.SourceSnapshot requirement(String category, String title, String content) {
        return new TaskBreakdownPolicy.SourceSnapshot(category, title, content);
    }

    private Task saveTo(List<Task> persistedTasks, Task task) {
        task.setId(task.getTitle().startsWith("商务") ? 9101L : 9102L);
        persistedTasks.add(task);
        return task;
    }

    private void runAndCaptureFailure(AtomicReference<Throwable> failure) {
        try {
            service.decomposeProjectTasks(1001L);
        } catch (Throwable throwable) {
            failure.compareAndSet(null, throwable);
        }
    }
}
