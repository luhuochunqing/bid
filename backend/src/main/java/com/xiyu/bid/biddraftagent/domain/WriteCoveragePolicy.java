package com.xiyu.bid.biddraftagent.domain;

import java.util.ArrayList;
import java.util.List;

public final class WriteCoveragePolicy {

  public WriteCoverageDecision evaluate(
      BidDraftSnapshot snapshot,
      RequirementClassification classification,
      MaterialMatchScore materialScore,
      GapCheckResult gapCheck,
      ManualConfirmationDecision manualConfirmation) {
    List<String> coveredSections = new ArrayList<>();
    List<String> missingSections = new ArrayList<>();
    List<String> recommendedSections = new ArrayList<>();

    if (snapshot.projectName() != null && !snapshot.projectName().isBlank()) {
      coveredSections.add("项目概况");
    } else {
      missingSections.add("项目概况");
    }

    if (classification.hasCommercialRequirement() || snapshot.tenderTitle() != null) {
      coveredSections.add("商务响应");
    } else {
      missingSections.add("商务响应");
    }

    if (classification.hasTechnicalRequirement() || classification.hasDeliveryRequirement()) {
      coveredSections.add("实施方案");
    } else {
      missingSections.add("实施方案");
    }

    if (classification.hasQualificationRequirement()) {
      coveredSections.add("资质证明");
    } else {
      missingSections.add("资质证明");
    }

    if (classification.hasPricingRequirement()) {
      coveredSections.add("报价与价格策略");
    } else {
      missingSections.add("报价与价格策略");
    }

    if (classification.hasLegalRequirement()) {
      coveredSections.add("法务与合规");
    } else {
      missingSections.add("法务与合规");
    }

    recommendedSections.addAll(coveredSections);
    if (manualConfirmation.requiresConfirmation()) {
      recommendedSections.add("人工确认后再提交");
    }
    recommendedSections.addAll(gapCheck.suggestions());

    int coverageScore =
        clamp(
            materialScore.score() + coveredSections.size() * 4 - missingSections.size() * 8,
            0,
            100);
    boolean sufficient = coverageScore >= 60 && gapCheck.gaps().isEmpty();

    return new WriteCoverageDecision(
        coverageScore, sufficient, coveredSections, missingSections, recommendedSections);
  }

  private int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
