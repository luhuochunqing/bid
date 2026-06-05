// Input: TenderEvaluationNotificationService, Tender
// Output: void — 发送审核通知
// Pos: Service/业务编排层（命令式外壳）
// 维护声明: 仅封装审核通知发送；不携带标讯评估业务规则。
package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 标讯评估提交审核通知服务。
 * <p>封装评估提交后的通知发送逻辑，保持 {@link TenderEvaluationSubmissionService} 聚焦于编排。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderEvaluationSubmissionNotifier {

    private final TenderEvaluationNotificationService notificationService;

    /**
     * 评估提交后发送审核通知。
     *
     * @param tender 关联标讯
     */
    public void notifyEvaluationSubmitted(Tender tender) {
        notificationService.createEvaluationNotificationTodos(tender);
    }
}
