package com.xiyu.bid.biddraftagent.domain;

import java.util.List;

public record ManualConfirmationDecision(
    boolean pricingConfirmationRequired,
    boolean legalConfirmationRequired,
    boolean qualificationAuthenticityConfirmationRequired,
    List<String> reasons) {

  public ManualConfirmationDecision {
    reasons = List.copyOf(reasons);
  }

  public boolean requiresConfirmation() {
    return pricingConfirmationRequired
        || legalConfirmationRequired
        || qualificationAuthenticityConfirmationRequired
        || (!reasons.isEmpty() && reasons.stream().anyMatch(r -> r.contains("存在未闭合材料缺口")));
  }
}
