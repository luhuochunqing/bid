package com.xiyu.bid.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 审批统计数据DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalStatisticsDTO {

    /**
     * 总审批数
     */
    private Long totalCount;

    /**
     * 待审批数
     */
    private Long pendingCount;

    /**
     * 已通过数
     */
    private Long approvedCount;

    /**
     * 已驳回数
     */
    private Long rejectedCount;

    /**
     * 已取消数
     */
    private Long cancelledCount;

    /**
     * 今日提交数
     */
    private Long todaySubmitted;

    /**
     * 本月提交数
     */
    private Long monthSubmitted;

    /**
     * 超期审批数
     */
    private Long overdueCount;

    /**
     * 临近截止数 (24小时内)
     */
    private Long nearDueCount;

    /**
     * 平均处理时长（小时）
     */
    private Double avgProcessingHours;

    /**
     * 通过率
     */
    private Double approvalRate;

    /**
     * 按类型统计
     */
    private Map<String, Long> byType;

    /**
     * 按优先级统计
     */
    private Map<Integer, Long> byPriority;
}
