// Input: 商机预测提醒命中的项目关键标识
// Output: 事件库消息 data 载荷（纯数据 record）
// Pos: integration/tenderevent/domain/ - 商机预测提醒事件载荷
package com.xiyu.bid.integration.tenderevent.domain;

import java.time.LocalDate;

/**
 * 商机预测提醒事件载荷（纯数据、不可变）。
 *
 * <p>随 {@link TenderEventCode#OPPORTUNITY_PREDICTION} 事件发布到西域事件库，
 * 供西域侧消费后创建/跟进商机。只携带关键标识，不携带业务细节。
 */
public record OpportunityPredictionEventPayload(
        Long projectId,
        String projectName,
        String purchaserName,
        LocalDate servicePeriodEndDate,
        LocalDate predictedBidDate
) {}
