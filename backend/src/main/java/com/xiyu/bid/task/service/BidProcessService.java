package com.xiyu.bid.task.service;

import com.xiyu.bid.task.core.BidSubmissionPolicy;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.task.repository.TaskDeliverableRepository;
import com.xiyu.bid.task.dto.BidSubmissionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Application service for bid-document submission workflow.
 * Validates project readiness against core policy before accepting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BidProcessService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskDeliverableRepository taskDeliverableRepository;

    /**
     * Submit a project to the bid document process.
     *
     * @param projectId the project id
     * @return submission response with acceptance status
     */
    @Transactional
    public BidSubmissionResponse submitToBidDocument(final Long projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在"));

        var tasks = taskRepository.findByProjectId(projectId);
        int totalTasks = tasks.size();

        long completedCount = tasks.stream()
                .filter(t -> t.getStatus() == Task.Status.COMPLETED)
                .count();

        // Count tasks with at least one deliverable
        long tasksWithDeliverables = 0;
        for (var task : tasks) {
            if (taskDeliverableRepository.countByTaskId(task.getId()) > 0) {
                tasksWithDeliverables++;
            }
        }

        var validation = BidSubmissionPolicy.validateSubmission(
                totalTasks,
                (int) completedCount,
                (int) tasksWithDeliverables);

        if (!validation.submittable()) {
            return BidSubmissionResponse.builder()
                    .accepted(false)
                    .message(validation.reason())
                    .totalTasks(totalTasks)
                    .completedTasks((int) completedCount)
                    .tasksWithDeliverables((int) tasksWithDeliverables)
                    .gaps(validation.gaps())
                    .build();
        }

        log.info("Project {} submitted to bid document", projectId);

        return BidSubmissionResponse.builder()
                .accepted(true)
                .message("所有任务已完成且有关联交付物，可开始标书编制")
                .submittedAt(LocalDateTime.now())
                .totalTasks(totalTasks)
                .completedTasks((int) completedCount)
                .tasksWithDeliverables((int) tasksWithDeliverables)
                .gaps(List.of())
                .build();
    }

    /**
     * Get current bid process readiness status.
     *
     * @param projectId the project id
     * @return status summary with counts
     */
    @Transactional(readOnly = true)
    public BidProcessStatusDTO getBidProcessStatus(final Long projectId) {
        var tasks = taskRepository.findByProjectId(projectId);
        int total = tasks.size();
        long completed = tasks.stream()
                .filter(t -> t.getStatus() == Task.Status.COMPLETED)
                .count();
        long withDeliverables = 0;
        for (var t : tasks) {
            if (taskDeliverableRepository.countByTaskId(t.getId()) > 0) {
                withDeliverables++;
            }
        }
        return new BidProcessStatusDTO(
                total, (int) completed, (int) withDeliverables,
                completed >= total && total > 0);
    }

    /** Bid process status summary record. */
    public record BidProcessStatusDTO(
            /** Total number of tasks. */
            int totalTasks,
            /** Number of completed tasks. */
            int completedTasks,
            /** Number of tasks with deliverables. */
            int tasksWithDeliverables,
            /** Whether the project is submittable. */
            boolean submittable
    ) {
    }
}
