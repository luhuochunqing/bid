// Input: LLM 结构化输出
// Output: 结构化表格列映射 DTO
// Pos: scoreparse/infrastructure/openai — 结构化契约
// 维护声明: 维护者按项目SOP；spec 058 单元格锚定评分解析

package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 表格列语义结构识别 LLM 输出 Schema（spec 058）。
 */
public class ScoreTableSchemaOutput {

    @JsonProperty("code_col")
    public int codeCol = -1;

    @JsonProperty("parent_dim_col")
    public int parentDimCol = -1;

    @JsonProperty("dim_col")
    public int dimCol = -1;

    @JsonProperty("detail_col")
    public int detailCol = 2;

    @JsonProperty("weight_col")
    public int weightCol = 3;

    @JsonProperty("header_rows")
    public int headerRows = 1;

    @JsonProperty("is_percentage")
    public boolean isPercentage = false;

    @JsonProperty("explanation")
    public String explanation;
}
