package com.xiyu.bid.projectworkflow.core;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pure score draft business rules.
 */
public final class ScoreDraftPolicy {

    private ScoreDraftPolicy() {
    }

    public static UpdateDecision decideUpdate(UpdateCommand command) {
        if (command.currentStatus() == DraftStatus.GENERATED) {
            return UpdateDecision.failure(RuleFailure.GENERATED_NOT_EDITABLE);
        }

        Long assigneeId = command.assigneeId();
        String assigneeName = trimToNull(command.assigneeName());
        DraftStatus status = resolveNextDraftStatus(command.requestedStatus(), assigneeId, assigneeName);
        String skipReason = resolveSkipReason(status, command.requestedSkipReason());

        if (status == DraftStatus.READY && assigneeId == null && assigneeName == null) {
            return UpdateDecision.failure(RuleFailure.READY_REQUIRES_ASSIGNEE);
        }

        return UpdateDecision.success(
            assigneeId,
            assigneeName,
            command.dueDate(),
            trimNullable(command.generatedTaskTitle()),
            trimNullable(command.generatedTaskDescription()),
            status,
            skipReason
        );
    }

    public static GenerationDecision decideGeneration(List<DraftStatus> statuses) {
        boolean allReady = statuses.stream().allMatch(status -> status == DraftStatus.READY);
        return allReady
            ? GenerationDecision.success()
            : GenerationDecision.failure(RuleFailure.ONLY_READY_DRAFTS_CAN_GENERATE_TASKS);
    }

    private static DraftStatus resolveNextDraftStatus(
        DraftStatus requestedStatus,
        Long assigneeId,
        String assigneeName
    ) {
        if (requestedStatus != null) {
            return requestedStatus;
        }
        if (assigneeId != null || assigneeName != null) {
            return DraftStatus.READY;
        }
        return DraftStatus.DRAFT;
    }

    private static String resolveSkipReason(DraftStatus status, String requestedSkipReason) {
        String skipReason = trimToNull(requestedSkipReason);
        if (status == DraftStatus.SKIPPED && skipReason == null) {
            return "人工暂不生成";
        }
        return skipReason;
    }

    private static String trimNullable(String value) {
        return value != null ? value.trim() : null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public enum DraftStatus {
        DRAFT,
        READY,
        SKIPPED,
        GENERATED
    }

    public enum RuleFailure {
        GENERATED_NOT_EDITABLE,
        READY_REQUIRES_ASSIGNEE,
        ONLY_READY_DRAFTS_CAN_GENERATE_TASKS
    }

    public record UpdateCommand(
        DraftStatus currentStatus,
        Long assigneeId,
        String assigneeName,
        LocalDateTime dueDate,
        String generatedTaskTitle,
        String generatedTaskDescription,
        DraftStatus requestedStatus,
        String requestedSkipReason
    ) {
    }

    public record UpdateDecision(
        boolean ok,
        RuleFailure failure,
        Long assigneeId,
        String assigneeName,
        LocalDateTime dueDate,
        String generatedTaskTitle,
        String generatedTaskDescription,
        DraftStatus status,
        String skipReason
    ) {
        static UpdateDecision success(
            Long assigneeId,
            String assigneeName,
            LocalDateTime dueDate,
            String generatedTaskTitle,
            String generatedTaskDescription,
            DraftStatus status,
            String skipReason
        ) {
            return new UpdateDecision(
                true,
                null,
                assigneeId,
                assigneeName,
                dueDate,
                generatedTaskTitle,
                generatedTaskDescription,
                status,
                skipReason
            );
        }

        static UpdateDecision failure(RuleFailure failure) {
            return new UpdateDecision(false, failure, null, null, null, null, null, null, null);
        }
    }

    public record GenerationDecision(boolean ok, RuleFailure failure) {
        static GenerationDecision success() {
            return new GenerationDecision(true, null);
        }

        static GenerationDecision failure(RuleFailure failure) {
            return new GenerationDecision(false, failure);
        }
    }
}
