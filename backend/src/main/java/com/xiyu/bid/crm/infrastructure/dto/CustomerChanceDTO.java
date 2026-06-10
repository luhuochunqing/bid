package com.xiyu.bid.crm.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * CRM 商机列表查询筛选条件。
 * <p>对应客户接口 POST /customer-chance/page-list 的 body 内层。
 * <p>注意：客户接口文档 body 中没有 tenderSubject 参数，但实际 CRM 按招标主体模糊匹配商机名称。
 */
public record CustomerChanceDTO(
    @JsonProperty("groupName") List<String> groupName,
    @JsonProperty("name") String name,
    @JsonProperty("code") String code,
    @JsonProperty("projectStatus") List<Integer> projectStatus,
    @JsonProperty("projectRisk") List<Integer> projectRisk,
    @JsonProperty("cooperationStatus") Integer cooperationStatus,
    @JsonProperty("tenderSubject") String tenderSubject,
    @JsonProperty("evaluationStartTime") String evaluationStartTime,
    @JsonProperty("evaluationEndTime") String evaluationEndTime,
    @JsonProperty("projectLeaderName") List<String> projectLeaderName,
    @JsonProperty("projectLeaderNo") String projectLeaderNo,
    @JsonProperty("updateStartAt") String updateStartAt,
    @JsonProperty("updateEndAt") String updateEndAt,
    @JsonProperty("selectAll") Boolean selectAll,
    @JsonProperty("selectList") List<Integer> selectList,
    @JsonProperty("notSelectList") List<Integer> notSelectList,
    @JsonProperty("timeSort") Integer timeSort
) {}
