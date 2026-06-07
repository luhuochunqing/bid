package com.xiyu.bid.biddraftagent.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record BidDraftSnapshot(
    Long projectId,
    Long tenderId,
    String projectName,
    String projectDescription,
    String sourceReasoningSummary,
    String customerName,
    String customerType,
    String region,
    String industry,
    BigDecimal budget,
    LocalDate deadline,
    String tenderTitle,
    String tenderDescription,
    String purchaserName,
    String source,
    List<String> tenderTags,
    List<String> structuredRequirementSignals,
    List<String> requiredMaterialSignals,
    List<String> scoringSignals,
    List<String> qualificationSignals,
    List<String> templateSignals,
    List<String> caseSignals) {

  public BidDraftSnapshot {
    tenderTags = normalizeList(tenderTags);
    structuredRequirementSignals = normalizeList(structuredRequirementSignals);
    requiredMaterialSignals = normalizeList(requiredMaterialSignals);
    scoringSignals = normalizeList(scoringSignals);
    qualificationSignals = normalizeList(qualificationSignals);
    templateSignals = normalizeList(templateSignals);
    caseSignals = normalizeList(caseSignals);
  }

  public List<String> textSegments() {
    List<String> segments = new ArrayList<>();
    addIfPresent(segments, projectName);
    addIfPresent(segments, projectDescription);
    addIfPresent(segments, sourceReasoningSummary);
    addIfPresent(segments, customerName);
    addIfPresent(segments, customerType);
    addIfPresent(segments, region);
    addIfPresent(segments, industry);
    addIfPresent(segments, budget == null ? null : budget.toPlainString());
    addIfPresent(segments, deadline == null ? null : deadline.toString());
    addIfPresent(segments, tenderTitle);
    addIfPresent(segments, tenderDescription);
    addIfPresent(segments, purchaserName);
    addIfPresent(segments, source);
    segments.addAll(tenderTags);
    segments.addAll(structuredRequirementSignals);
    segments.addAll(requiredMaterialSignals);
    segments.addAll(scoringSignals);
    segments.addAll(qualificationSignals);
    segments.addAll(templateSignals);
    segments.addAll(caseSignals);
    return List.copyOf(segments);
  }

  public String corpus() {
    return textSegments().stream()
        .filter(Objects::nonNull)
        .map(value -> value.replaceAll("\\s+", " ").trim().toLowerCase(java.util.Locale.ROOT))
        .filter(value -> !value.isBlank())
        .reduce("", (left, right) -> left.isBlank() ? right : left + " " + right);
  }

  private static void addIfPresent(List<String> segments, String value) {
    if (value != null && !value.isBlank()) {
      segments.add(value.trim());
    }
  }

  private static List<String> normalizeList(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    return values.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .toList();
  }
}
