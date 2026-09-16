// Input: 招标主体名称 / 服务周期截止时间（可空）/ 预测招标时间
// Output: 商机预测提醒消息模板（Title + Body）
// Pos: workbench/domain/ - 商机预测提醒消息模板（纯数据 record）
package com.xiyu.bid.workbench.domain;

import java.time.LocalDate;

/**
 * 商机预测提醒消息模板（纯数据 record，不读写数据库、不读时间）。
 *
 * <p>文案对齐通知内容模板：
 * {@code 【商机预测】{招标主体名称} 服务周期将于 {到期日期} 截止，
 * 预测下次招标时间为 {预测招标时间}，请到CRM创建商机。}
 *
 * <p>无服务周期截止日期时省略"服务周期将于…截止"句（业务确认）。
 */
public record OpportunityPredictionAlertMessage(String title, String body) {

    public static OpportunityPredictionAlertMessage from(
            final String purchaserName, final LocalDate servicePeriodEndDate, final LocalDate predictedBidDate) {
        String name = nullSafe(purchaserName, "（未知招标主体）");
        String title = String.format("【商机预测】%s", name);
        String predictedText = predictedBidDate == null ? "—" : predictedBidDate.toString();
        String body;
        if (servicePeriodEndDate != null) {
            body = String.format(
                    "服务周期将于 %s 截止，预测下次招标时间为 %s，请到CRM创建商机。",
                    servicePeriodEndDate, predictedText);
        } else {
            body = String.format("预测下次招标时间为 %s，请到CRM创建商机。", predictedText);
        }
        return new OpportunityPredictionAlertMessage(title, body);
    }

    /** 一次性历史补发文案：必须让接收人在标题和正文中都能识别补发性质。 */
    public static OpportunityPredictionAlertMessage historicalReplay(
            final String purchaserName, final String projectName, final LocalDate predictedBidDate) {
        String purchaser = nullSafe(purchaserName, "（未知招标主体）");
        String project = nullSafe(projectName, "（未知项目）");
        String predictedText = predictedBidDate == null ? "—" : predictedBidDate.toString();
        return new OpportunityPredictionAlertMessage(
                String.format("【商机预测·历史补发】%s", purchaser),
                String.format("历史项目“%s”预测下次招标时间为 %s，"
                        + "这是历史数据补发提醒，请到CRM创建商机。", project, predictedText));
    }

    private static String nullSafe(final String raw, final String fallback) {
        return (raw == null || raw.isBlank()) ? fallback : raw;
    }
}
