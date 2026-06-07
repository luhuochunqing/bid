// Input: 技术要点分类枚举
// Output: 四种标签：硬指标、功能、兼容性、加分项
// Pos: biddraftagent/domain/technical — 技术要点子类型值对象

package com.xiyu.bid.biddraftagent.domain.technical;

public enum TechnicalSubType {
    HARD_INDEX,     // 硬指标 — 必须满足的硬性指标（如CPU主频≥2.0GHz，内存≥32GB）
    FUNCTIONAL,     // 功能 — 功能要求（如支持多用户并发、提供报表功能）
    COMPATIBILITY,  // 兼容性 — 兼容性要求（如兼容国产操作系统、支持主流浏览器）
    BONUS           // 加分项 — 加分项/优选条件（如具有同类项目经验优先）
}
