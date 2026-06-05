package com.xiyu.bid.biddraftagent.domain;

import java.util.ArrayList;
import java.util.List;

public final class GapCheckPolicy {

  public GapCheckResult check(
      BidDraftSnapshot snapshot,
      RequirementClassification classification,
      MaterialMatchScore score) {
    List<String> gaps = new ArrayList<>();
    List<String> suggestions = new ArrayList<>();

    if (snapshot == null) {
      gaps.add("项目快照不能为空");
      suggestions.add("请先加载项目、标讯与知识快照");
      return new GapCheckResult(false, gaps, suggestions);
    }

    if (snapshot.projectName() == null || snapshot.projectName().isBlank()) {
      gaps.add("项目名称缺失");
      suggestions.add("补充项目名称后再生成草稿");
    }
    if (snapshot.tenderTitle() == null || snapshot.tenderTitle().isBlank()) {
      gaps.add("标讯标题缺失");
      suggestions.add("补充标讯标题和正文描述");
    }
    if (classification.hasPricingRequirement() && snapshot.budget() == null) {
      gaps.add("价格条款缺少预算或限价依据");
      suggestions.add("补充预算、限价或报价说明");
    }
    if (classification.hasLegalRequirement() && snapshot.templateSignals().isEmpty()) {
      gaps.add("法务条款缺少模板或合同参考");
      suggestions.add("补充合同模板、法务条款或授权依据");
    }
    if (classification.hasQualificationRequirement() && snapshot.qualificationSignals().isEmpty()) {
      gaps.add("资质要求缺少证书或业绩材料");
      suggestions.add("补充资质证书、授权书或业绩证明");
    }
    if (classification.hasTechnicalRequirement() && snapshot.caseSignals().isEmpty()) {
      gaps.add("技术要求缺少案例或方案参考");
      suggestions.add("补充方案、案例或实施经验");
    }
    if (classification.hasDeliveryRequirement()
        && (snapshot.projectDescription() == null || snapshot.projectDescription().isBlank())) {
      gaps.add("交付要求缺少项目描述");
      suggestions.add("补充项目描述和交付范围");
    }
    if (score.score() < 60) {
      gaps.add("材料覆盖度不足");
      suggestions.add("继续补齐关键材料后再输出正式写作建议");
    }

    return new GapCheckResult(gaps.isEmpty(), gaps, suggestions);
  }
}
