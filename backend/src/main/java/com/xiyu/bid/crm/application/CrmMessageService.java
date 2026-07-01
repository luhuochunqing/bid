package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CrmMessageService {

    private static final Logger log = LoggerFactory.getLogger(CrmMessageService.class);

    private final CrmHttpClient httpClient;
    private final CrmAuthService authService;
    private final CrmProperties properties;

    public CrmMessageService(CrmHttpClient httpClient, CrmAuthService authService,
                             CrmProperties properties) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.properties = properties;
    }

    /**
     * 发送消息（企微+站内信）。
     *
     * @param recipientNos 接收人工号列表
     * @param title        消息标题
     * @param content      消息内容
     * @param flag         推送方式：1 企微+站内信, 2 站内信, 3 企微
     * @param username     当前登录用户名（CO-152：用于按用户维度获取 CRM token）
     * @return CRM API 响应
     */
    public CrmResponseHandler.CrmApiResponse sendMessage(
            java.util.List<String> recipientNos,
            String title,
            String content,
            int flag,
            String username) {
        String token = authService.getValidTokenForUser(username);
        String baseUrl = properties.getEffectiveMessageBaseUrl();
        String path = properties.getMessage().getSendPath();

        Map<String, Object> body = Map.of(
                "recipientNos", recipientNos,
                "title", title,
                "content", content,
                "flag", flag
        );

        log.info("Sending CRM message: title={}, recipients={}, flag={}", title, recipientNos.size(), flag);
        return httpClient.post(baseUrl, path, token, body);
    }
}
