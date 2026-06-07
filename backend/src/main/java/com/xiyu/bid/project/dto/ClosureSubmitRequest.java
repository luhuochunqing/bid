// Input: 结项提交 HTTP 请求体（蓝图 §3.3.1.6 - 项目结项）
// Output: 结项服务所需入参（保证金退回登记 + 项目总结）
// Pos: project/dto/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosureSubmitRequest {
    /** 保证金退回状态（NOT_RETURNED/FULLY_RETURNED/TRANSFERRED_TO_FEE/PARTIAL_RETURN_PARTIAL_TRANSFER）。 */
    private String depositReturnStatus;
    /** 保证金退回日期（FULLY_RETURNED 时必填）。 */
    private LocalDateTime depositReturnDate;
    /** 保证金退回凭证文档 ID。 */
    private Long depositReturnEvidenceId;
    /** 转平台服务费金额。 */
    private BigDecimal transferAmount;
    /** 实际退回金额。 */
    private BigDecimal returnedAmount;
    /** 归档位置（可选）。 */
    private String archiveLocation;
    /** 项目总结（蓝图 §3.3.1.6 项目总结字段，非必填）。 */
    private String projectSummary;
    /** 结项备注（可选）。 */
    private String notes;
}
