package com.xiyu.bid.integration.tenderevent.domain;

import java.time.LocalDateTime;

/**
 * 标讯事件消息体 data（纯核心、不可变）。
 *
 * <p>应 CRM 全量字段推送要求，携带标讯详情页前端展示字段（项目名称/招标主体/总部所在地/
 * 报名截止时间/开标时间/客户类型/优先级/项目类型/联系人1、2 含性质），供 CRM 直接消费。
 *
 * <p>推送触发时机、策略、开关、线程池、流水均不变；{@code customerType/priority/projectType}
 * 存后端枚举码，序列化时（{@code TenderEventPayloadMapper.toData}）统一转中文标签。
 */
public record TenderEventPayload(
        Long tenderId,
        String externalId,
        String title,
        String purchaserName,
        String region,
        LocalDateTime registrationDeadline,
        LocalDateTime bidOpeningTime,
        String customerType,
        String priority,
        String projectType,
        String contactName,
        String contactPhone,
        String contactTel,
        String contactMail,
        String contactNature,
        String contactName2,
        String contactPhone2,
        String contactTel2,
        String contactMail2,
        String contactNature2
) {
}