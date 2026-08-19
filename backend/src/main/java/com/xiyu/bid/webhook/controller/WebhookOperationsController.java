// Input: HTTP 运维请求（重推 CRM 回调）
// Output: 重推结果 JSON
// Pos: webhook/controller/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.webhook.controller;

import com.xiyu.bid.webhook.application.ProjectResultRedeliveryService;
import com.xiyu.bid.webhook.application.ProjectResultRedeliveryService.RedeliveryOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook 运维接口。
 * <p>§4.2 结果确认回调重推：!2331 上线后，对历史项目重新推送回调，
 * 用永久有效的集成下载端点 URL 覆盖 CRM 侧已过期的 OBS 预签名 URL。
 * <p>仅限 ADMIN：重推会覆盖 CRM 侧业务数据，属运维动作。
 */
@RestController
@RequestMapping("/api/webhooks/operations")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class WebhookOperationsController {

    private final ProjectResultRedeliveryService redeliveryService;

    /**
     * 重推指定结果记录对应的 §4.2 回调。
     * <p>事件重新发布后由 {@code ProjectResultConfirmedWebhookListener} 用当前代码组装 payload 入队，
     * 走既有的 1min/5min/15min 异步投递与重试机制。
     */
    @PostMapping("/project-result/{resultId}/redeliver")
    public RedeliveryOutcome redeliverProjectResult(@PathVariable Long resultId) {
        return redeliveryService.redeliver(resultId);
    }
}
