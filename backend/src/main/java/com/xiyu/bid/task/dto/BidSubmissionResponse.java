package com.xiyu.bid.task.dto;

import com.xiyu.bid.task.core.BidSubmissionPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for bid-document submission result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidSubmissionResponse {

    /** Whether submission was accepted. */
    private boolean accepted;

    /** Human-readable message describing the outcome. */
    private String message;

    /** Timestamp when submission was accepted. */
    private LocalDateTime submittedAt;

    /** Total number of tasks in the project. */
    private int totalTasks;

    /** Number of completed tasks. */
    private int completedTasks;

    /** Number of tasks that have at least one deliverable. */
    private int tasksWithDeliverables;

    /** List of gaps if submission was rejected. */
    private List<BidSubmissionPolicy.TaskGap> gaps;
}
