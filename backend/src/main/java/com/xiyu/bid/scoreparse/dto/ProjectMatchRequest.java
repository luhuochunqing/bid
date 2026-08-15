package com.xiyu.bid.scoreparse.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 项目业绩匹配请求（POST /api/knowledge/project/match，FR-010）。
 *
 * @param projectTypeKeywords 项目类型/行业关键词（project_type 枚举名或中文、industry 文本，命中任一即算）
 * @param signedAfter         签约日期下限（含）
 * @param minContractAmount   合同金额下限（含；存量行 contract_amount 为 NULL 时跳过金额比对不失配）
 * @param requiredCount       要求业绩条数
 */
public record ProjectMatchRequest(
        List<String> projectTypeKeywords,
        LocalDate signedAfter,
        BigDecimal minContractAmount,
        Integer requiredCount
) {
}
