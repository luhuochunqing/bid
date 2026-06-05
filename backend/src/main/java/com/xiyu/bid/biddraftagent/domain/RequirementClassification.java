package com.xiyu.bid.biddraftagent.domain;

import java.util.List;

public record RequirementClassification(
    List<String> pricingRequirements,
    List<String> legalRequirements,
    List<String> qualificationRequirements,
    List<String> technicalRequirements,
    List<String> deliveryRequirements,
    List<String> commercialRequirements,
    List<String> otherRequirements) {

  public RequirementClassification {
    pricingRequirements = List.copyOf(pricingRequirements);
    legalRequirements = List.copyOf(legalRequirements);
    qualificationRequirements = List.copyOf(qualificationRequirements);
    technicalRequirements = List.copyOf(technicalRequirements);
    deliveryRequirements = List.copyOf(deliveryRequirements);
    commercialRequirements = List.copyOf(commercialRequirements);
    otherRequirements = List.copyOf(otherRequirements);
  }

  public boolean hasPricingRequirement() {
    return !pricingRequirements.isEmpty();
  }

  public boolean hasLegalRequirement() {
    return !legalRequirements.isEmpty();
  }

  public boolean hasQualificationRequirement() {
    return !qualificationRequirements.isEmpty();
  }

  public boolean hasTechnicalRequirement() {
    return !technicalRequirements.isEmpty();
  }

  public boolean hasDeliveryRequirement() {
    return !deliveryRequirements.isEmpty();
  }

  public boolean hasCommercialRequirement() {
    return !commercialRequirements.isEmpty();
  }

  public int categoryCount() {
    int count = 0;
    if (hasPricingRequirement()) {
      count++;
    }
    if (hasLegalRequirement()) {
      count++;
    }
    if (hasQualificationRequirement()) {
      count++;
    }
    if (hasTechnicalRequirement()) {
      count++;
    }
    if (hasDeliveryRequirement()) {
      count++;
    }
    if (hasCommercialRequirement()) {
      count++;
    }
    return count;
  }

  public List<String> categories() {
    java.util.ArrayList<String> categories = new java.util.ArrayList<>();
    if (hasPricingRequirement()) {
      categories.add("pricing");
    }
    if (hasLegalRequirement()) {
      categories.add("legal");
    }
    if (hasQualificationRequirement()) {
      categories.add("qualification");
    }
    if (hasTechnicalRequirement()) {
      categories.add("technical");
    }
    if (hasDeliveryRequirement()) {
      categories.add("delivery");
    }
    if (hasCommercialRequirement()) {
      categories.add("commercial");
    }
    return List.copyOf(categories);
  }
}
