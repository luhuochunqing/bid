// Input: 商机预测提醒命中的项目全量展示字段
// Output: 事件库消息 data 载荷（纯数据 record）
// Pos: integration/tenderevent/domain/ - 商机预测提醒事件载荷
package com.xiyu.bid.integration.tenderevent.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 商机预测提醒事件载荷（纯数据、不可变）。
 *
 * <p>随 {@link TenderEventCode#OPPORTUNITY_PREDICTION} 事件发布到西域事件库，
 * 供西域侧消费后创建/跟进商机。应 CRM 全量字段推送要求，携带商机预测表格
 * 前端展示字段（项目名称/项目状态/招标主体/项目负责人/客户类型/项目类型/开标时间/
 * 项目服务周期/服务周期截止时间/预测招标时间）。
 *
 * <p>{@code resultType/customerType/projectType} 存后端枚举码，序列化时
 * （{@code OpportunityPredictionEventPublisher.buildData}）统一转中文标签。
 */
public record OpportunityPredictionEventPayload(
        Long projectId,
        String projectName,
        String resultType,
        String customer,
        String projectLeaderName,
        String customerType,
        String projectType,
        String bidOpenTime,
        BigDecimal servicePeriodYears,
        LocalDate servicePeriodEndDate,
        LocalDate predictedBidDate
) {}
