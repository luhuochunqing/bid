package com.xiyu.bid.biddraftagent.domain;

import java.util.List;

public record MaterialMatchScore(
    int score,
    int matchedCategoryCount,
    int totalCategoryCount,
    List<String> matchedCategories,
    List<String> missingCategories,
    List<String> matchedSignals,
    List<String> missingSignals) {

  public MaterialMatchScore {
    matchedCategories = List.copyOf(matchedCategories);
    missingCategories = List.copyOf(missingCategories);
    matchedSignals = List.copyOf(matchedSignals);
    missingSignals = List.copyOf(missingSignals);
  }
}
