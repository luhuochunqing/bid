// Input: LLM JSON response for a single document chunk
// Output: Mutable POJO – tender extraction fields (Jackson + jsonschema-generator compatible)
// Pos: biddraftagent/infrastructure/openai
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.util.List;

public class TenderRequirementOutput {
    public String projectName;
    public String tenderTitle;
    public String tenderScope;
    /** 招标公告完整原文，包含项目概况、资格要求、技术要求、商务要求、评分办法、联系方式等全部章节 */
    public String tenderInfo;
    public String purchaserName;
    public String budget;
    public String region;
    public String industry;
    public String tenderAgency;
    public String bidOpeningTime;
    public String contactName;
    public String contactPhone;
    public String contactLandline;
    public String contactEmail;
    public String contactName2;
    public String contactPhone2;
    public String contactLandline2;
    public String contactEmail2;
    public String customerType;
    public String priority;
    public String publishDate;
    public String deadline;
    public List<String> qualificationRequirements;
    public List<String> technicalRequirements;
    public List<String> commercialRequirements;
    public List<String> scoringCriteria;
    public List<ScoringCriterionOutput> scoringCriteriaItems;
    public String deadlineText;
    public List<String> requiredMaterials;
    public List<String> riskPoints;
    public List<String> tags;
    public List<TenderRequirementItemOutput> requirementItems;
}
