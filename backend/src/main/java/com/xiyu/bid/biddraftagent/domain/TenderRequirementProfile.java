package com.xiyu.bid.biddraftagent.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record TenderRequirementProfile(
    String projectName,
    String tenderTitle,
    String tenderScope,
    String purchaserName,
    BigDecimal budget,
    String region,
    String industry,
    LocalDate publishDate,
    LocalDateTime deadline,
    List<String> qualificationRequirements,
    List<String> technicalRequirements,
    List<String> commercialRequirements,
    List<String> scoringCriteria,
    List<ScoringCriterion> scoringCriteriaItems,
    String deadlineText,
    List<String> requiredMaterials,
    List<String> riskPoints,
    List<String> tags,
    List<TenderRequirementItemSnapshot> items) {

  private static final Set<String> READABLE_LIST_NOISE =
      Set.of(
          "qualification",
          "technical",
          "commercial",
          "pricing",
          "legal",
          "delivery",
          "scoring",
          "material",
          "other",
          "risk",
          "true",
          "false",
          "null");
  private static final Set<String> PLACEHOLDER_VALUES = Set.of("未明确提及，根据通用要求推断");
  private static final Pattern BARE_INTEGER = Pattern.compile("^\\d{1,3}$");

  public TenderRequirementProfile {
    region = normalizeText(region);
    industry = normalizeText(industry);
    qualificationRequirements = normalizeReadableStrings(qualificationRequirements);
    technicalRequirements = normalizeReadableStrings(technicalRequirements);
    commercialRequirements = normalizeReadableStrings(commercialRequirements);
    scoringCriteria = normalizeReadableStrings(scoringCriteria);
    scoringCriteriaItems = scoringCriteriaItems == null ? List.of() : scoringCriteriaItems;
    requiredMaterials = normalizeReadableStrings(requiredMaterials);
    riskPoints = normalizeReadableStrings(riskPoints);
    tags = normalizeStrings(tags);
    items = normalizeItems(items);
  }

  public TenderRequirementProfile(
      String pProjectName,
      String pTenderTitle,
      String pTenderScope,
      String pPurchaserName,
      List<String> pQualificationRequirements,
      List<String> pTechnicalRequirements,
      List<String> pCommercialRequirements,
      List<String> pScoringCriteria,
      List<ScoringCriterion> pScoringCriteriaItems,
      String pDeadlineText,
      List<String> pRequiredMaterials,
      List<String> pRiskPoints,
      List<String> pTags,
      List<TenderRequirementItemSnapshot> pItems) {
    this(
        pProjectName,
        pTenderTitle,
        pTenderScope,
        pPurchaserName,
        null,
        null,
        null,
        null,
        null,
        pQualificationRequirements,
        pTechnicalRequirements,
        pCommercialRequirements,
        pScoringCriteria,
        pScoringCriteriaItems,
        pDeadlineText,
        pRequiredMaterials,
        pRiskPoints,
        pTags,
        pItems);
  }

  public List<String> requirementSignals() {
    List<String> signals = new ArrayList<>();
    signals.addAll(qualificationRequirements);
    signals.addAll(technicalRequirements);
    signals.addAll(commercialRequirements);
    items.stream()
        .map(TenderRequirementProfile::formatItemSignal)
        .filter(signal -> !signal.isBlank())
        .forEach(signals::add);
    return List.copyOf(signals);
  }

  private static String formatItemSignal(TenderRequirementItemSnapshot item) {
    String title = blankToEmpty(item.title());
    String content = blankToEmpty(item.content());
    String category = blankToEmpty(item.category());
    return (category + " / " + title + " / " + content).trim();
  }

  private static List<String> normalizeStrings(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    return values.stream()
        .filter(Objects::nonNull)
        .map(TenderRequirementProfile::normalizeWhitespace)
        .filter(value -> !value.isBlank())
        .distinct()
        .toList();
  }

  private static List<String> normalizeReadableStrings(List<String> values) {
    return normalizeStrings(values).stream().filter(value -> !isReadableListNoise(value)).toList();
  }

  private static List<TenderRequirementItemSnapshot> normalizeItems(
      List<TenderRequirementItemSnapshot> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    return values.stream().filter(Objects::nonNull).toList();
  }

  private static String blankToEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  private static String normalizeWhitespace(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ");
  }

  private static String normalizeText(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return normalizeWhitespace(value);
  }

  private static boolean isReadableListNoise(String value) {
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return READABLE_LIST_NOISE.contains(normalized)
        || PLACEHOLDER_VALUES.contains(value.trim())
        || BARE_INTEGER.matcher(normalized).matches();
  }

  /** @deprecated Use the full constructor with scoringCriteriaItems parameter. */
  @Deprecated
  public TenderRequirementProfile(
      String pProjectName, String pTenderTitle, String pTenderScope, String pPurchaserName,
      BigDecimal pBudget, String pRegion, String pIndustry,
      LocalDate pPublishDate, LocalDateTime pDeadline,
      List<String> pQualificationRequirements, List<String> pTechnicalRequirements,
      List<String> pCommercialRequirements, List<String> pScoringCriteria,
      String pDeadlineText,
      List<String> pRequiredMaterials, List<String> pRiskPoints,
      List<String> pTags, List<TenderRequirementItemSnapshot> pItems) {
    this(
        pProjectName, pTenderTitle, pTenderScope, pPurchaserName,
        pBudget, pRegion, pIndustry, pPublishDate, pDeadline,
        pQualificationRequirements, pTechnicalRequirements,
        pCommercialRequirements, pScoringCriteria,
        List.of(),
        pDeadlineText, pRequiredMaterials, pRiskPoints, pTags, pItems);
  }

  /** @deprecated Use the full constructor with scoringCriteriaItems parameter. */
  @Deprecated
  public TenderRequirementProfile(
      String pProjectName, String pTenderTitle, String pTenderScope, String pPurchaserName,
      List<String> pQualificationRequirements, List<String> pTechnicalRequirements,
      List<String> pCommercialRequirements, List<String> pScoringCriteria,
      String pDeadlineText,
      List<String> pRequiredMaterials, List<String> pRiskPoints,
      List<String> pTags, List<TenderRequirementItemSnapshot> pItems) {
    this(
        pProjectName, pTenderTitle, pTenderScope, pPurchaserName,
        pQualificationRequirements, pTechnicalRequirements,
        pCommercialRequirements, pScoringCriteria,
        List.of(),
        pDeadlineText, pRequiredMaterials, pRiskPoints, pTags, pItems);
  }

}
