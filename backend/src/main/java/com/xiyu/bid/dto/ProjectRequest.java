package com.xiyu.bid.dto;

import com.xiyu.bid.entity.Project;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private List<@NotNull(
            message = "成员ID不能为null") Long> teamMembers;

    @NotNull(message = "开始日期不能为空")
    private LocalDateTime startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDateTime endDate;

    @Size(max = 100,
            message = "来源模块长度不能超过100个字符")
    private String sourceModule;

    @Size(max = 100,
            message = "来源客户ID长度不能超过100个字符")
    private String sourceCustomerId;

    @Size(max = 255,
            message = "来源客户名称长度不能超过255个字符")
    private String sourceCustomer;

    @Size(max = 100,
            message = "来源机会ID长度不能超过100个字符")
    private String sourceOpportunityId;

    @Size(max = 2000,
            message = "来源推理摘要长度不能超过2000个字符")
    private String sourceReasoningSummary;

    @AssertTrue(message = "结束日期必须晚于开始日期")
    private boolean isDateRangeValid() {
        if (startDate == null || endDate == null) {
            return false;
        }
        return endDate.isAfter(startDate);
    }
}
