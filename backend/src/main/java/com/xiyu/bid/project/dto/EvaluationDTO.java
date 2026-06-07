// Input: ProjectEvaluation 实体 + 关联证据 doc id 列表
// Output: 评标视图 DTO
// Pos: project/dto/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationDTO {
    private Long id;
    private Long projectId;
    private String subStage;
    private LocalDateTime evaluationStartedAt;
    private LocalDateTime boardReceivedAt;
    private LocalDateTime announcedAt;
    private String notes;
    private List<Long> evidenceDocIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    private String background;
    private String competitors;
    private String contractPeriod;
    private Integer shortlistedBidders;
    private BigDecimal platformFee;
    private String previousBid;
    private Boolean recommendation;

    /** 评标阶段是否已完成（已推进到结果确认阶段） */
    private Boolean done;
}
