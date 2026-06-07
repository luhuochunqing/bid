package com.xiyu.bid.biddraftagent.domain;

import java.util.ArrayList;
import java.util.List;

public final class ManualConfirmationPolicy {

  public ManualConfirmationDecision evaluate(
      RequirementClassification classification, GapCheckResult gaps) {
    List<String> reasons = new ArrayList<>();
    boolean pricing = classification.hasPricingRequirement();
    boolean legal = classification.hasLegalRequirement();
    boolean qualification = classification.hasQualificationRequirement();

    if (pricing) {
      reasons.add("价格与报价口径需要人工复核");
    }
    if (legal) {
      reasons.add("法务条款与合同表述需要人工确认");
    }
    if (qualification) {
      reasons.add("资质真实性与有效期需要人工确认");
    }
    if (!gaps.ready()) {
      reasons.add("存在未闭合材料缺口，需人工确认后再定稿");
    }

    return new ManualConfirmationDecision(pricing, legal, qualification, reasons);
  }
}
