package com.xiyu.bid.biddraftagent.domain;

import java.util.ArrayList;
import java.util.List;

public final class MaterialMatchScoringPolicy {

  public MaterialMatchScore score(
      BidDraftSnapshot snapshot, RequirementClassification classification) {
    List<String> matchedCategories = new ArrayList<>();
    List<String> missingCategories = new ArrayList<>();
    List<String> matchedSignals = new ArrayList<>();
    List<String> missingSignals = new ArrayList<>();

    evaluateCategory(
        "pricing",
        classification.hasPricingRequirement(),
        matches(snapshot.corpus(), "价格", "报价", "预算", "限价", "结算", "付款"),
        matchedCategories,
        missingCategories,
        matchedSignals,
        missingSignals);
    evaluateCategory(
        "legal",
        classification.hasLegalRequirement(),
        matches(snapshot.corpus(), "合同", "法务", "合规", "条款", "授权", "声明"),
        matchedCategories,
        missingCategories,
        matchedSignals,
        missingSignals);
    evaluateCategory(
        "qualification",
        classification.hasQualificationRequirement(),
        !snapshot.qualificationSignals().isEmpty(),
        matchedCategories,
        missingCategories,
        matchedSignals,
        missingSignals);
    evaluateCategory(
        "technical",
        classification.hasTechnicalRequirement(),
        matches(snapshot.corpus(), "技术", "方案", "实施", "架构", "部署", "运维", "培训"),
        matchedCategories,
        missingCategories,
        matchedSignals,
        missingSignals);
    evaluateCategory(
        "delivery",
        classification.hasDeliveryRequirement(),
        matches(snapshot.corpus(), "交付", "验收", "服务", "售后", "支持", "周期", "上线"),
        matchedCategories,
        missingCategories,
        matchedSignals,
        missingSignals);
    evaluateCategory(
        "commercial",
        classification.hasCommercialRequirement(),
        matches(snapshot.corpus(), "商务", "投标", "响应", "文件", "标书", "澄清", "答疑"),
        matchedCategories,
        missingCategories,
        matchedSignals,
        missingSignals);

    int totalCategoryCount = classification.categoryCount();
    int matchedCategoryCount = matchedCategories.size();
    int score = totalCategoryCount == 0 ? 100 : (matchedCategoryCount * 100) / totalCategoryCount;

    return new MaterialMatchScore(
        score,
        matchedCategoryCount,
        totalCategoryCount,
        matchedCategories,
        missingCategories,
        matchedSignals,
        missingSignals);
  }

  private void evaluateCategory(
      String category,
      boolean required,
      boolean supported,
      List<String> matchedCategories,
      List<String> missingCategories,
      List<String> matchedSignals,
      List<String> missingSignals) {
    if (!required) {
      return;
    }
    if (supported) {
      matchedCategories.add(category);
      matchedSignals.add(category + ":supported");
    } else {
      missingCategories.add(category);
      missingSignals.add(category + ":missing");
    }
  }

  private boolean matches(String corpus, String... keywords) {
    for (String keyword : keywords) {
      if (corpus.contains(keyword)) {
        return true;
      }
    }
    return false;
  }
}
