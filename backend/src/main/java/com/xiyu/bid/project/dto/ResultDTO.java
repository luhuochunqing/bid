// Input: ProjectResult 实体
// Output: 出参 DTO
// Pos: project/dto/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultDTO {
    private Long id;
    private Long projectId;
    private String resultType;
    private BigDecimal awardAmount;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private List<Long> evidenceFileIds;
    private String summary;
    /** 凭证标签，由后端根据 resultType 自动推导回显（如"中标通知书"），不单独持久化。 */
    private String evidenceTags;
    private LocalDateTime registeredAt;
    private Long registeredBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CompetitorRow> competitors;

    /** 竞争对手情况行（PRD §3.3.1.4）。 */
    public record CompetitorRow(
            String name,
            String discount,
            String paymentTerm,
            String notes
    ) {}
}
