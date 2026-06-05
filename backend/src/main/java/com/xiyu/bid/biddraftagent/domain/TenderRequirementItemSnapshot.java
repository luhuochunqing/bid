package com.xiyu.bid.biddraftagent.domain;

public record TenderRequirementItemSnapshot(
    String category,
    String title,
    String content,
    boolean mandatory,
    String sourceExcerpt,
    Integer confidence,
    String sectionPath) {
  public TenderRequirementItemSnapshot(
      String category,
      String title,
      String content,
      boolean mandatory,
      String sourceExcerpt,
      Integer confidence) {
    this(category, title, content, mandatory, sourceExcerpt, confidence, null);
  }
}
