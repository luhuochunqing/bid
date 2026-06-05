package com.xiyu.bid.crm.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * CRM 商机单条记录 VO。
 * <p>对应客户接口 POST /customer-chance/page-list 返回的 dataList 元素。
 */
public record CustomerChanceVO(
    @JsonProperty("id") Long id,
    @JsonProperty("code") String code,
    @JsonProperty("name") String name,
    @JsonProperty("groupName") String groupName,
    @JsonProperty("groupId") Integer groupId,
    @JsonProperty("tenderSubject") String tenderSubject,
    @JsonProperty("tenderSubjectId") Integer tenderSubjectId,
    @JsonProperty("projectLeaderName") String projectLeaderName,
    @JsonProperty("projectLeaderNo") String projectLeaderNo,
    @JsonProperty("secondDeptLeaderName") String secondDeptLeaderName,
    @JsonProperty("secondDeptLeaderNo") String secondDeptLeaderNo,
    @JsonProperty("projectStatus") Integer projectStatus,
    @JsonProperty("projectStatusText") String projectStatusText,
    @JsonProperty("cooperationStatus") Integer cooperationStatus,
    @JsonProperty("winningVendor") String winningVendor,
    @JsonProperty("bidFailureReason") String bidFailureReason,
    @JsonProperty("missReason") String missReason,
    @JsonProperty("feedBack") String feedBack,
    @JsonProperty("projectRisk") String projectRisk,
    @JsonProperty("projectRiskText") String projectRiskText,
    @JsonProperty("evaluationTime") String evaluationTime,
    @JsonProperty("planSupplierCount") Long planSupplierCount,
    @JsonProperty("ecommerceMroAmount") BigDecimal ecommerceMroAmount,
    @JsonProperty("customerRevenue") BigDecimal customerRevenue,
    @JsonProperty("bidDocumentDisadvantage") String bidDocumentDisadvantage,
    @JsonProperty("riskPrediction") String riskPrediction,
    @JsonProperty("backupPlan") Boolean backupPlan,
    @JsonProperty("backupPlanText") String backupPlanText,
    @JsonProperty("managerUnderstandProcess") String managerUnderstandProcess,
    @JsonProperty("projectGap") String projectGap,
    @JsonProperty("gapFile") String gapFile,
    @JsonProperty("remark") String remark,
    @JsonProperty("createBy") String createBy,
    @JsonProperty("createByName") String createByName,
    @JsonProperty("updateBy") String updateBy,
    @JsonProperty("updateByName") String updateByName,
    @JsonProperty("createAt") String createAt,
    @JsonProperty("updateAt") String updateAt,
    @JsonProperty("activeRecord") String activeRecord,
    @JsonProperty("activeRecordTime") String activeRecordTime,
    @JsonProperty("activeRecordCreateBy") String activeRecordCreateBy,
    @JsonProperty("transferVisible") Boolean transferVisible,
    @JsonProperty("bidRemainTime") String bidRemainTime
) {}
