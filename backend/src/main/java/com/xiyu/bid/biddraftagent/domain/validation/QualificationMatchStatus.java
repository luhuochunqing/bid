// Input: 资质匹配状态枚举
// Output: 三态分类：已满足、需关注、不满足
// Pos: biddraftagent/domain/validation — 资质比对状态值对象

package com.xiyu.bid.biddraftagent.domain.validation;

public enum QualificationMatchStatus {
    SATISFIED,      // 已满足 — 知识库中已找到匹配项，条件符合
    ATTENTION,     // 需关注 — 条件临界（如证书即将到期），建议人工复核
    UNSATISFIED     // 不满足 — 知识库中未找到匹配项或条件不符合
}
