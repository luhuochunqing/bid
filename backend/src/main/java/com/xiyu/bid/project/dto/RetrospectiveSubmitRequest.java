// Input: 提交复盘的 HTTP 请求体 (PRD §3.3.1.5)
// Output: 校验后的 DTO
// Pos: project/dto/
package com.xiyu.bid.project.dto;

import com.xiyu.bid.project.core.BidResultType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrospectiveSubmitRequest {
    @NotNull
    private BidResultType resultType;

    // 会议信息（蓝图标明必填）
    private String meetingTime;          // ISO datetime
    private String meetingFormat;        // ONLINE / OFFLINE
    private String meetingParticipants;

    // 中标字段
    private String winFactors;           // 中标优势（富文本）
    private String processHighlights;    // 流程亮点（富文本）
    private String postWinImprovements;  // 后续改进建议（富文本）

    // 未中标字段
    private List<String> lossReasonFlags; // 丢标原因多选标记
    private String processProblems;       // 流程存在问题（富文本）
    private String postLossMeasures;      // 具体改进措施（富文本）

    // 可选
    private List<Long> reportFileIds;     // 复盘报告附件ID
    private String summary;               // 摘要（补充）

    // 向后兼容（旧代码传入的字段）
    private String lossReasons;
    private String competitorNotes;
    private String improvementActions;
}
