// Input: current-user project scope and a record project id
// Output: visibility decision for project-linked records
// Pos: Pure Core/纯核心策略
package com.xiyu.bid.access.core;

import java.util.Collection;

/**
 * Shared project-linked record visibility policy.
 */
public final class ProjectLinkedRecordVisibilityPolicy {

    private ProjectLinkedRecordVisibilityPolicy() {
    }

    public static boolean visible(boolean admin, Collection<Long> allowedProjectIds, Long recordProjectId) {
        if (admin || recordProjectId == null) {
            return true;
        }
        return allowedProjectIds != null && allowedProjectIds.contains(recordProjectId);
    }
}
