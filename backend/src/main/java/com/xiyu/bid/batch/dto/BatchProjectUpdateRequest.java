package com.xiyu.bid.batch.dto;

import com.xiyu.bid.entity.Project;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for batch updating projects.
 * Allows updating status and/or manager for multiple projects.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchProjectUpdateRequest {

    @NotEmpty(message = "Project IDs list cannot be empty")
    private List<@NotNull(message = "Project ID cannot be null") Long> projectIds;

    /**
     * New status to set for all projects (optional)
     */
    private Project.Status status;

    /**
     * New manager ID to assign to all projects (optional)
     */
    private Long managerId;

    /**
     * Optional remark for audit trail
     */
    private String remark;

    /**
     * Validate that at least one field is being updated.
     */
    public boolean hasUpdates() {
        return status != null || managerId != null;
    }
}
