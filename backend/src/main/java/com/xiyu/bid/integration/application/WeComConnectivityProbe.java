package com.xiyu.bid.integration.application;

import com.xiyu.bid.integration.domain.WeComConnectivityResult;

/**
 * Port interface for probing WeCom connectivity.
 * Implementations may call the real WeCom API or return a mock result.
 */
public interface WeComConnectivityProbe {

    WeComConnectivityResult probe(String corpId, String agentId, String plainSecret);
}
