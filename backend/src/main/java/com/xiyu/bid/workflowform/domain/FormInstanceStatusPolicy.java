package com.xiyu.bid.workflowform.domain;

import java.util.Map;
import java.util.Set;

public final class FormInstanceStatusPolicy {

    private static final Map<WorkflowFormStatus, Set<WorkflowFormStatus>> ALLOWED = Map.of(
            WorkflowFormStatus.DRAFT, Set.of(WorkflowFormStatus.SUBMITTED),
            WorkflowFormStatus.SUBMITTED, Set.of(WorkflowFormStatus.OA_STARTING, WorkflowFormStatus.OA_APPROVING, WorkflowFormStatus.OA_FAILED),
            WorkflowFormStatus.OA_STARTING, Set.of(WorkflowFormStatus.OA_APPROVING, WorkflowFormStatus.OA_FAILED),
            WorkflowFormStatus.OA_APPROVING, Set.of(WorkflowFormStatus.OA_APPROVED, WorkflowFormStatus.OA_REJECTED, WorkflowFormStatus.OA_FAILED),
            WorkflowFormStatus.OA_APPROVED, Set.of(WorkflowFormStatus.BUSINESS_APPLIED)
    );

    private FormInstanceStatusPolicy() {
    }

    public static boolean canTransit(WorkflowFormStatus from, WorkflowFormStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
