// Input: TenderRequirementProfile（预算、发布日期、截止日期等结构化字段）+ 当前日期（caller 注入，纯核心不取系统时间）
// Output: 业务校验警告列表（中文字符串）
// Pos: biddraftagent/domain/validation — 标讯业务合理性校验纯核心
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.biddraftagent.domain.validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;

public class TenderBusinessValidator {

  private static final BigDecimal LOW_BUDGET_THRESHOLD = new BigDecimal("10000");
  private static final BigDecimal HIGH_BUDGET_THRESHOLD = new BigDecimal("10000000000");
  private static final long MIN_PREP_DAYS = 3;

  public List<String> validate(TenderRequirementProfile profile, LocalDate currentDate) {
    List<String> warnings = new ArrayList<>();

    if (profile.budget() != null) {
      if (profile.budget().compareTo(LOW_BUDGET_THRESHOLD) < 0) {
        warnings.add("预算金额异常偏低（低于 1 万元），请核实货币单位是否正确。");
      } else if (profile.budget().compareTo(HIGH_BUDGET_THRESHOLD) > 0) {
        warnings.add("预算金额异常偏高（超过 100 亿元），请确认是否有误。");
      }
    } else {
      warnings.add("未能提取到预算金额，或预算字段为空。");
    }

    LocalDate publishDate = profile.publishDate();
    LocalDateTime deadline = profile.deadline();

    if (publishDate != null && deadline != null) {
      if (deadline.toLocalDate().isBefore(publishDate)) {
        warnings.add("投标截止日期不能早于发布日期。");
      }
      long daysBetween = ChronoUnit.DAYS.between(publishDate, deadline.toLocalDate());
      if (daysBetween < MIN_PREP_DAYS) {
        warnings.add("发布日期到截止日期之间的时间过短（不足 3 天），请核实时间安排。");
      }
    }

    if (deadline != null && currentDate != null && deadline.toLocalDate().isBefore(currentDate)) {
      warnings.add("投标截止日期已过期。");
    }

    return warnings;
  }
}
