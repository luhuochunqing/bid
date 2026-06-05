// Input: QualificationMatcher 的匹配输出
// Output: 含三态（已满足/需关注/不满足）的资质匹配结果列表
// Pos: biddraftagent/domain/validation — 资质匹配结果值对象

package com.xiyu.bid.biddraftagent.domain.validation;

import java.util.List;

/**
 * 资质要求与知识库的匹配结果。
 * 逐条列出招标文件中的每项资质要求，给出三态判定结果。
 *
 * @param items 逐条匹配结果
 */
public record QualificationMatchResult(
    List<QualificationMatchItem> items) {

  /** 单条资质要求的匹配结果 */
  public record QualificationMatchItem(
      String requirementText,                    // 招标文件中的资质要求原文
      QualificationMatchStatus status,           // 三态：已满足/需关注/不满足
      String matchedQualificationName,           // 匹配到的资质名称（仅 SATISFIED/ATTENTION 时有值）
      Long matchedQualificationId,               // 匹配到的资质 ID
      Integer remainingDays,                     // 剩余天数（仅 ATTENTION 时可能有值，如证书即将到期）
      String reason                              // 判定原因说明
  ) {}
}
