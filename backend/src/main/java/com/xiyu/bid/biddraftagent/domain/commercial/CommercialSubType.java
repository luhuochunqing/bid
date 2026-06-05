// Input: 商务条款分类枚举
// Output: 六种标签：付款方式、履约保证金、交付周期、质保期、违约责任、知识产权归属
// Pos: biddraftagent/domain/commercial — 商务条款子类型值对象

package com.xiyu.bid.biddraftagent.domain.commercial;

public enum CommercialSubType {
    PAYMENT_TERMS,       // 付款方式
    PERFORMANCE_BOND,    // 履约保证金
    DELIVERY_CYCLE,      // 交付周期
    WARRANTY_PERIOD,     // 质保期
    BREACH_LIABILITY,    // 违约责任
    IP_OWNERSHIP          // 知识产权归属
}
