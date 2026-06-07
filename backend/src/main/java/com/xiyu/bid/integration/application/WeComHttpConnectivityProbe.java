package com.xiyu.bid.integration.application;

import com.xiyu.bid.integration.domain.WeComConnectivityResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Real implementation of WeComConnectivityProbe.
 * Makes an actual API call to WeCom by fetching an access token.
 * Active by default (probe.mode=real or not set). Use probe.mode=mock to activate the mock instead.
 */
@Component
@ConditionalOnProperty(name = "wecom.probe.mode", havingValue = "real", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class WeComHttpConnectivityProbe implements WeComConnectivityProbe {

    private final WeComAccessTokenProvider tokenProvider;

    @Override
    public WeComConnectivityResult probe(String corpId, String agentId, String plainSecret) {
        try {
            tokenProvider.getAccessToken(corpId, agentId, plainSecret);
            return new WeComConnectivityResult(true, "企业微信连接成功", LocalDateTime.now());
        } catch (WeComApiException ex) {
            log.warn("WeCom connectivity probe failed: errcode={} msg={}", ex.errcode(), ex.getMessage());
            return new WeComConnectivityResult(
                    false,
                    "errcode=" + ex.errcode() + " errmsg=" + ex.getMessage(),
                    LocalDateTime.now()
            );
        }
    }
}
