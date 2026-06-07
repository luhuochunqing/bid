// Input: 商务条款条目
// Output: 带子类型标签的单条商务条款
// Pos: biddraftagent/domain/commercial — 商务条款条目值对象

package com.xiyu.bid.biddraftagent.domain.commercial;

public record CommercialRequirementItem(
    String text,
    CommercialSubType subType
) {}
