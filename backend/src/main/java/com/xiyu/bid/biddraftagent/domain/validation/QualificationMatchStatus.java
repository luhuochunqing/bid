// Input: 资质匹配状态枚举
// Output: 三态分类：已满足、需关注、不满足
// Pos: biddraftagent/domain/validation — 资质比对状态值对象

package com.xiyu.bid.biddraftagent.domain.validation;

import com.xiyu.bid.common.display.DisplayableEnum;

public enum QualificationMatchStatus implements DisplayableEnum {
    SATISFIED("已满足"),
    ATTENTION("需关注"),
    UNSATISFIED("不满足");

    private final String displayName;

    QualificationMatchStatus(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }
}
