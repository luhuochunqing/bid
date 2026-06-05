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
    public String purchaserName;
    public String budget;
    public String region;
    public String industry;
    public String tenderAgency;
    public String bidOpeningTime;
    public String contactName;
    public String contactPhone;
    public String contactEmail;
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
