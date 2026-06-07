package com.xiyu.bid.biddraftagent.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RequirementClassificationPolicy {

  private static final List<String> PRICING_KEYWORDS =
      List.of("价格", "报价", "预算", "限价", "税率", "结算", "付款");
  private static final List<String> LEGAL_KEYWORDS =
      List.of("合同", "法务", "合规", "条款", "授权", "声明", "违约");
  private static final List<String> QUALIFICATION_KEYWORDS =
      List.of("资质", "资格", "证书", "认证", "许可", "业绩", "授权书");
  private static final List<String> TECHNICAL_KEYWORDS =
      List.of("技术", "方案", "实施", "架构", "部署", "运维", "培训");
  private static final List<String> DELIVERY_KEYWORDS =
      List.of("交付", "验收", "服务", "售后", "支持", "周期", "上线");
  private static final List<String> COMMERCIAL_KEYWORDS =
      List.of("商务", "投标", "响应", "文件", "标书", "澄清", "答疑");

  public RequirementClassification classify(BidDraftSnapshot snapshot) {
    String corpus = snapshot == null ? "" : snapshot.corpus();
    return new RequirementClassification(
        detectMatches(corpus, PRICING_KEYWORDS),
        detectMatches(corpus, LEGAL_KEYWORDS),
        detectMatches(corpus, QUALIFICATION_KEYWORDS),
        detectMatches(corpus, TECHNICAL_KEYWORDS),
        detectMatches(corpus, DELIVERY_KEYWORDS),
        detectMatches(corpus, COMMERCIAL_KEYWORDS),
        detectOtherMatches(corpus));
  }

  private List<String> detectMatches(String corpus, List<String> keywords) {
    Set<String> matches = new LinkedHashSet<>();
    for (String keyword : keywords) {
      if (corpus.contains(keyword.toLowerCase(Locale.ROOT))) {
        matches.add(keyword);
      }
    }
    return List.copyOf(matches);
  }

  private List<String> detectOtherMatches(String corpus) {
    List<String> matches = new ArrayList<>();
    if (corpus.contains("标讯") || corpus.contains("招标")) {
      matches.add("招标背景");
    }
    if (corpus.contains("项目") || corpus.contains("需求")) {
      matches.add("项目背景");
    }
    if (corpus.contains("客户") || corpus.contains("采购")) {
      matches.add("客户背景");
    }
    return List.copyOf(matches);
  }
}
