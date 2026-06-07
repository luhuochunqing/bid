// Input: 登记结果的 HTTP 请求体
// Output: 校验后的 DTO
// Pos: project/dto/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.dto;

import com.xiyu.bid.project.core.BidResultType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultRegistrationRequest {
    @NotNull
    private BidResultType resultType;
    private BigDecimal awardAmount;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private List<Long> evidenceFileIds;
    private String summary;
    /** 凭证标签，如"中标通知书"、"流标公告"等，由前端根据结果类型传入。 */
    private String evidenceTags;
    @Valid
    private List<CompetitorRow> competitors;

    /** 竞争对手情况行（PRD §3.3.1.4 竞争对手情况表，四字段均为非必填）。 */
    public record CompetitorRow(
            String name,
            String discount,
            String paymentTerm,
            String notes
    ) {}
}
