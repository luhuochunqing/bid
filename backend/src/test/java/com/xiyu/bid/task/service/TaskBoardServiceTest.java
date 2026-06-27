package com.xiyu.bid.task.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.task.dto.TaskBoardItemDTO;
import com.xiyu.bid.task.repository.TaskDeliverableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskBoardServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private BidDocumentReviewRepository bidDocumentReviewRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    @Mock
    private TaskAssignmentSupport assignmentSupport;

    @Mock
    private TaskDeliverableRepository taskDeliverableRepository;

    private TaskBoardService taskBoardService;

    @BeforeEach
    void setUp() {
        taskBoardService = new TaskBoardService(
                taskRepository,
                bidDocumentReviewRepository,
                projectRepository,
                userRepository,
                projectAccessScopeService,
                assignmentSupport,
                taskDeliverableRepository
        );
    }

    @Test
    void getBoardItemsAggregatesTasksAndBidReviewsForCurrentUser() {
        User currentUser = User.builder().id(1L).username("u1").fullName("User One").build();
        when(assignmentSupport.resolveEnabledUserByUsername("u1")).thenReturn(currentUser);

        Task task = Task.builder()
                .id(10L)
                .projectId(100L)
                .title("普通任务")
                .description("任务描述")
                .status(Task.Status.TODO)
                .priority(Task.Priority.HIGH)
                .assigneeId(1L)
                .build();
        when(taskRepository.findByAssigneeId(1L)).thenReturn(List.of(task));

        BidDocumentReviewEntity review = BidDocumentReviewEntity.builder()
                .id(20L)
                .projectId(100L)
                .reviewerId(1L)
                .submittedBy(2L)
                .status("REVIEWING")
                .build();
        when(bidDocumentReviewRepository.findByReviewerId(1L)).thenReturn(List.of(review));

        Project project = Project.builder().id(100L).name("项目42").build();
        when(projectRepository.findAllById(any())).thenReturn(List.of(project));

        User submitter = User.builder().id(2L).fullName("提交人").build();
        when(userRepository.findByIdIn(any())).thenReturn(List.of(submitter));

        when(projectAccessScopeService.getAllowedProjectIds(currentUser)).thenReturn(List.of());

        List<TaskBoardItemDTO> items = taskBoardService.getBoardItems("u1");

        assertThat(items).hasSize(2);
        TaskBoardItemDTO taskItem = items.stream()
                .filter(i -> "TASK".equals(i.getType()))
                .findFirst()
                .orElseThrow();
        assertThat(taskItem.getTitle()).isEqualTo("普通任务");
        assertThat(taskItem.getStatus()).isEqualTo("TODO");
        assertThat(taskItem.getProjectName()).isEqualTo("项目42");

        TaskBoardItemDTO reviewItem = items.stream()
                .filter(i -> "BID_REVIEW".equals(i.getType()))
                .findFirst()
                .orElseThrow();
        assertThat(reviewItem.getTitle()).isEqualTo("标书审核：项目42");
        assertThat(reviewItem.getStatus()).isEqualTo("REVIEW");
        assertThat(reviewItem.getSubmitterName()).isEqualTo("提交人");
    }

    @Test
    void getBoardItemsFiltersCompletedReviews() {
        // CO-361: 三态模型收口后所有任务均展示，本测试仅验证已完成的标书审核不展示
        User currentUser = User.builder().id(1L).username("u1").fullName("User One").build();
        when(assignmentSupport.resolveEnabledUserByUsername("u1")).thenReturn(currentUser);

        BidDocumentReviewEntity approvedReview = BidDocumentReviewEntity.builder()
                .id(20L)
                .projectId(100L)
                .reviewerId(1L)
                .submittedBy(2L)
                .status("APPROVED")
                .build();
        when(bidDocumentReviewRepository.findByReviewerId(1L)).thenReturn(List.of(approvedReview));
        when(taskRepository.findByAssigneeId(1L)).thenReturn(List.of());

        when(projectAccessScopeService.getAllowedProjectIds(currentUser)).thenReturn(List.of());

        List<TaskBoardItemDTO> items = taskBoardService.getBoardItems("u1");

        assertThat(items).isEmpty();
    }

    @Test
    void getBoardItemsFiltersByProjectVisibility() {
        User currentUser = User.builder().id(1L).username("u1").fullName("User One").build();
        when(assignmentSupport.resolveEnabledUserByUsername("u1")).thenReturn(currentUser);

        Task visibleTask = Task.builder()
                .id(10L)
                .projectId(100L)
                .title("可见任务")
                .status(Task.Status.TODO)
                .assigneeId(1L)
                .build();
        Task invisibleTask = Task.builder()
                .id(11L)
                .projectId(101L)
                .title("不可见任务")
                .status(Task.Status.TODO)
                .assigneeId(1L)
                .build();
        when(taskRepository.findByAssigneeId(1L)).thenReturn(List.of(visibleTask, invisibleTask));
        when(bidDocumentReviewRepository.findByReviewerId(1L)).thenReturn(List.of());

        Project project = Project.builder().id(100L).name("可见项目").build();
        when(projectRepository.findAllById(any())).thenReturn(List.of(project));

        when(projectAccessScopeService.getAllowedProjectIds(currentUser)).thenReturn(List.of(100L));

        List<TaskBoardItemDTO> items = taskBoardService.getBoardItems("u1");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getTitle()).isEqualTo("可见任务");
    }
}
