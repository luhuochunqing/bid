package com.xiyu.bid.bidresult.integration;

import com.xiyu.bid.entity.User;

record BidResultIntegrationSession(
        User adminUser,
        BidResultIntegrationFixtureSupport fixtures,
        BidResultIntegrationHttpSupport httpSupport
) {
}
