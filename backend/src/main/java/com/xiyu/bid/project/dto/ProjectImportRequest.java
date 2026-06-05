package com.xiyu.bid.project.dto;

import com.xiyu.bid.entity.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 历史档案批量导入请求 DTO。
 * 用于管理员从外部系统导入历史项目档案。
 * 区别于普通创建：可指定立项/评标/结项时间戳，不被系统自动覆盖。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectImportRequest {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 500)
    private String name;

    @NotNull(message = "标讯ID不能为空")
    @Positive
    private Long tenderId;

    private Project.Status status;

    @NotNull(message = "项目经理ID不能为空")
    @Positive
    private Long managerId;

    private List<Long> teamMembers;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private String sourceModule;

    private String sourceCustomerId;

    private String sourceCustomer;

    private String sourceOpportunityId;

    private String customer;

    private BigDecimal budget;

    private String industry;

    private String customerType;

    private String region;

    private String platform;

    private LocalDate deadline;

    private String description;

    private String remark;

    /**
     * 立项时间（可选）。指定后以该值填充，不被系统自动覆盖。
     */
    private LocalDateTime initiatedAt;

    /**
     * 评标时间（可选）。指定后以该值填充，不被系统自动覆盖。
     */
    private LocalDateTime evaluatingAt;

    /**
     * 结项时间（可选）。指定后以该值填充，不被系统自动覆盖。
     */
    private LocalDateTime closedAt;
}
