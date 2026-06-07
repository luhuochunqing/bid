package com.xiyu.bid.biddraftagent.domain;

import java.util.List;

public record WriteCoverageDecision(
    int coverageScore,
    boolean sufficient,
    List<String> coveredSections,
    List<String> missingSections,
    List<String> recommendedSections) {

  public WriteCoverageDecision {
    coveredSections = List.copyOf(coveredSections);
    missingSections = List.copyOf(missingSections);
    recommendedSections = List.copyOf(recommendedSections);
  }
}
