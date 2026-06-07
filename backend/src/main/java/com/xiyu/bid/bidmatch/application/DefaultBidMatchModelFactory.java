package com.xiyu.bid.bidmatch.application;

import com.xiyu.bid.bidmatch.domain.BidMatchScoringModel;

import java.util.List;

public final class DefaultBidMatchModelFactory {

    private DefaultBidMatchModelFactory() {
    }

    public static BidMatchScoringModel create() {
        return new BidMatchScoringModel(
                null,
                "待配置投标匹配评分模型",
                "请按客户要求自定义评分维度、规则和权重后再激活。",
                List.of(),
                1
        );
    }
}
