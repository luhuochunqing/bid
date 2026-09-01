// Input: LLM JSON response for table-level score item extraction
// Output: Mutable POJO – table classification and structured score candidates
// Pos: scoreparse/infrastructure/openai
package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 评分表格整表结构化抽取 LLM 响应 Schema。
 */
public class TableScoreExtractionOutput {

    /**
     * 表格分类类型：
     * SCORE_TABLE（评标打分细则表）、
     * CONTRACT_ASSESSMENT（合同履约考核表/违约扣款表）、
     * PROCEDURE_TABLE（前附表程序性须知/废标条款/资格初审）、
     * UNKNOWN（其他非评分表）
     */
    @JsonProperty("table_type")
    public String tableType = "SCORE_TABLE";

    /** 是否为真正的评标打分表 */
    @JsonProperty("is_score_table")
    public boolean isScoreTable = true;

    /** 置信度 0.0 ~ 1.0 */
    @JsonProperty("confidence")
    public Double confidence = 1.0;

    /** 若判定为非评分表，说明原因 */
    @JsonProperty("non_score_reason")
    public String nonScoreReason;

    /** 提取出的评分项列表 */
    @JsonProperty("items")
    public List<TableScoreItem> items;

    public static class TableScoreItem {
        /** 评分项编号（如 1、3.3(1)、A1），无则 null */
        @JsonProperty("code")
        public String code;

        /** 所属评审大类/标包（如 商务部分、技术部分），无则 null */
        @JsonProperty("parent_dim")
        public String parentDim;

        /** 评分因素/子项名称（如 财务状况及抗风险能力、供应商业绩、信用评价） */
        @JsonProperty("dim")
        public String dim;

        /** 评分细则（必须是表格单元格原文逐字复制，严禁摘要/改写/缩略） */
        @JsonProperty("detail")
        public String detail;

        /** 满分分值/权重（数值，如 10.0、7.0、2.5、50.0；若有括号分值以括号标注为准） */
        @JsonProperty("weight")
        public Double weight;

        /** 是否为汇总行/大类说明行（如"商务评分标准56分"或"分值构成"）而非末级评分项 */
        @JsonProperty("is_summary_row")
        public boolean isSummaryRow = false;

        /** OBJECTIVE（客观量化）/ SUBJECTIVE（主观描述） */
        @JsonProperty("score_type_guess")
        public String scoreTypeGuess;

        /** 上下文约束/备注，无则 null */
        @JsonProperty("context_note")
        public String contextNote;
    }
}
