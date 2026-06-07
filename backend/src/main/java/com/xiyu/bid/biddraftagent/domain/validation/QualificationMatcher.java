// Input: requirements（资质要求列表）、qualifications（企业已持有资质摘要）、referenceDate（参考日期）
// Output: QualificationMatchResult（三态匹配结果：已满足/需关注/不满足）
// Pos: biddraftagent/domain/validation — 资质比对纯核心逻辑（FP-Java 纯函数）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.biddraftagent.domain.validation;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class QualificationMatcher {

  /** 证书即将到期的预警天数阈值（≤ 60 天触发"需关注"） */
  static final int EXPIRY_WARN_DAYS = 60;

  public QualificationMatchResult match(List<String> requirements,
      List<QualificationSummary> qualifications, LocalDate referenceDate) {

    List<QualificationMatchResult.QualificationMatchItem> items = new ArrayList<>();

    if (requirements == null || requirements.isEmpty()) {
      return new QualificationMatchResult(items);
    }

    for (String req : requirements) {
      QualificationSummary matchedQual = null;
      for (QualificationSummary qual : qualifications) {
        String qualName = qual.name();
        if (qualName == null || qualName.length() < 2) continue;

        if (SmartMatchUtils.isSmartMatch(req, qualName)) {
          matchedQual = qual;
          break;
        }
      }

      if (matchedQual == null) {
        // 不满足：未找到匹配项
        items.add(new QualificationMatchResult.QualificationMatchItem(
            req,
            QualificationMatchStatus.UNSATISFIED,
            null, null, null,
            "知识库中未找到匹配项，可能存在废标风险"));
      } else {
        // 已匹配，检查是否需关注（证书即将到期）
        Integer remainingDays = computeRemainingDays(matchedQual, referenceDate);
        if (remainingDays != null && remainingDays <= EXPIRY_WARN_DAYS) {
          // 需关注：证书即将到期
          items.add(new QualificationMatchResult.QualificationMatchItem(
              req,
              QualificationMatchStatus.ATTENTION,
              matchedQual.name(),
              matchedQual.id(),
              remainingDays,
              "证书「" + matchedQual.name() + "」" + remainingDays + "天后到期，建议人工复核"));
        } else {
          // 已满足
          items.add(new QualificationMatchResult.QualificationMatchItem(
              req,
              QualificationMatchStatus.SATISFIED,
              matchedQual.name(),
              matchedQual.id(),
              remainingDays,
              "知识库中已找到匹配项「" + matchedQual.name() + "」，条件符合"));
        }
      }
    }

    return new QualificationMatchResult(items);
  }

  /** 计算资质剩余有效天数，无有效期或已过期返回 null */
  private Integer computeRemainingDays(QualificationSummary qual, LocalDate referenceDate) {
    if (qual.expiryDate() == null) return null;
    long days = ChronoUnit.DAYS.between(referenceDate, qual.expiryDate());
    return days < 0 ? null : (int) days;
  }

  /**
   * 智能匹配：委托给 SmartMatchUtils。
   * - 长名称（length > 5，如"ISO9001质量管理体系"）：使用不区分大小写的子串匹配，误匹配风险低。
   * - 短名称（length <= 5，如"ISO"）：如果全部是英文字母，仅使用词边界正则匹配。
   *   如果包含非英文字母（如中文字符"涉密甲级"或特殊字符"C++"），降级使用 contains。
   */
  boolean isSmartMatch(String source, String target) {
    return SmartMatchUtils.isSmartMatch(source, target);
  }
}
