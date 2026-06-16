package com.xiyu.bid.wecom;

import com.xiyu.bid.crm.application.CrmMessageService;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler.CrmApiResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 独立的企微消息发送能力。任何模块需要发企微时，按工号直接调用本服务。
 *
 * <p>传输经 crm 模块委托给西域统一消息服务 {@code POST /common/sendMessage}，
 * 固定 {@code flag=3}（仅企微）。站内信不在本能力范围（由 notification 收件箱承担）。
 *
 * <p>不做重试：CRM 层（{@code CrmHttpClient}）已对 5xx 内置重试；
 * 任务级重试由调用方（如 notification 投递管线）负责。
 */
@Service
public class WecomMessageSender {

    /** CRM /common/sendMessage 的"仅企微"推送方式。 */
    public static final int FLAG_WECOM_ONLY = 3;

    /** 本地校验失败哨兵 code（与 CRM 解析失败返回的 -1 靠 message 文本区分）。 */
    private static final int CODE_VALIDATION = -1;

    private final CrmMessageService crmMessageService;

    public WecomMessageSender(CrmMessageService crmMessageService) {
        this.crmMessageService = crmMessageService;
    }

    /**
     * 按工号发送企微消息。
     *
     * @param recipientNos 接收人工号列表；为 null 或空则返回 failure，不调用 CRM
     * @param title        消息标题
     * @param content      消息内容
     * @return 发送结果
     */
    public WecomSendResult send(List<String> recipientNos, String title, String content) {
        if (recipientNos == null || recipientNos.isEmpty()) {
            return WecomSendResult.failure(CODE_VALIDATION, "recipientNos is empty");
        }
        CrmApiResponse api = crmMessageService.sendMessage(recipientNos, title, content, FLAG_WECOM_ONLY);
        return api.success()
            ? WecomSendResult.success(api.code(), api.msg())
            : WecomSendResult.failure(api.code(), api.msg());
    }
}
