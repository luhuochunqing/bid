package com.xiyu.bid.dto;

import com.xiyu.bid.entity.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDTO {

    private Long id;
    private String name;
    private Long tenderId;
    private Project.Status status;
    private Long managerId;
    private List<Long> teamMembers;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String sourceModule;
    private String sourceCustomerId;
    private String sourceCustomer;
    private String sourceOpportunityId;
    private String sourceReasoningSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
