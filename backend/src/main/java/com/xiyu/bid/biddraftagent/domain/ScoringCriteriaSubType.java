// Input: 评分标准分类枚举
// Output: 六种标签：价格权重、技术评价、服务评价、资质门槛、综合评分、其他
// Pos: biddraftagent/domain — 评分标准子类型值对象

package com.xiyu.bid.biddraftagent.domain;

public enum ScoringCriteriaSubType {
    PRICE_WEIGHT,              // 价格权重 — 价格评分、报价占比
    TECHNICAL_EVALUATION,      // 技术评价 — 技术方案、技术评审
    SERVICE_EVALUATION,        // 服务评价 — 售后服务、响应速度
    QUALIFICATION_THRESHOLD,   // 资质门槛 — 资质要求、资格条件
    COMPREHENSIVE_SCORE,       // 综合评分 — 综合评审、总分
    OTHER                      // 其他 — 无法匹配以上类别的评分项
}
