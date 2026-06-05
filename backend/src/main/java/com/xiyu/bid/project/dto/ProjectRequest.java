package com.xiyu.bid.project.dto;

import com.xiyu.bid.entity.Project;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
 * 项目请求数传输对象
 * 用于接收前端请求时的数据验证
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectRequest {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 500, message = "项目名称长度不能超过500个字符")
    private String name;

    @NotNull(message = "标讯ID不能为空")
    @Positive(message = "标讯ID必须是正数")
    private Long tenderId;

    private Project.Status status;

    @NotNull(message = "项目经理ID不能为空")
    @Positive(message = "项目经理ID必须是正数")
    private Long managerId;

    @NotEmpty(message = "团队成员不能为空")
    private List<@NotNull(message = "成员ID不能为null") Long> teamMembers;

    @NotNull(message = "开始日期不能为空")
    private LocalDateTime startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDateTime endDate;

    @Size(max = 100, message = "来源模块长度不能超过100个字符")
    private String sourceModule;

    @Size(max = 100, message = "来源客户ID长度不能超过100个字符")
    private String sourceCustomerId;

    @Size(max = 255, message = "来源客户名称长度不能超过255个字符")
    private String sourceCustomer;

    @Size(max = 100, message = "来源机会ID长度不能超过100个字符")
    private String sourceOpportunityId;

    @Size(max = 2000, message = "来源推理摘要长度不能超过2000个字符")
    private String sourceReasoningSummary;

    @Size(max = 50000, message = "竞争对手分析数据过大")
    private String competitorAnalysisJson;
    @Size(max = 50000, message = "任务数据过大")
    private String tasksJson;
    @Size(max = 100000, message = "AI分析数据过大")
    private String aiAnalysisJson;

    @Size(max = 255, message = "客户名称长度不能超过255个字符")
    private String customer;

    @DecimalMin(value = "0.00", message = "预算不能为负数")
    @Digits(integer = 12, fraction = 2, message = "预算格式不正确")
    private BigDecimal budget;

    @Size(max = 50, message = "行业长度不能超过50个字符")
    private String industry;

    @Size(max = 100, message = "客户类型长度不能超过100个字符")
    private String customerType;

    @Size(max = 100, message = "地区长度不能超过100个字符")
    private String region;

    @Size(max = 255, message = "平台名称长度不能超过255个字符")
    private String platform;

    private LocalDate deadline;

    @Size(max = 5000, message = "项目描述长度不能超过5000个字符")
    private String description;

    @Size(max = 5000, message = "备注长度不能超过5000个字符")
    private String remark;

    @Size(max = 1000, message = "标签长度不能超过1000个字符")
    private String tagsJson;

    @Size(max = 255, message = "客户负责人名称长度不能超过255个字符")
    private String customerManager;

    @Size(max = 100, message = "客户负责人ID长度不能超过100个字符")
    private String customerManagerId;

    @AssertTrue(message = "结束日期必须晚于开始日期")
    private boolean isDateRangeValid() {
        if (startDate == null || endDate == null) {
            return false;
        }
        return endDate.isAfter(startDate);
    }
}
