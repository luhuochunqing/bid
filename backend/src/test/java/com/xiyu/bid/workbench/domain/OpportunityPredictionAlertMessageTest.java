// Input: 招标主体名称 / 服务周期截止时间（可空）/ 预测招标时间（可空）
// Output: 提醒文案 Title + Body 分支覆盖
// Pos: backend/src/test/java/com/xiyu/bid/workbench/domain/ - 纯核心文案模板单测
package com.xiyu.bid.workbench.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("OpportunityPredictionAlertMessage - 商机预测提醒文案模板")
class OpportunityPredictionAlertMessageTest {

    private static final LocalDate END = LocalDate.of(2026, 8, 14);
    private static final LocalDate PREDICTED = LocalDate.of(2026, 2, 14);

    @Test
    @DisplayName("有服务周期截止时间：标题含招标主体，正文含截止句")
    void withEndDateShouldIncludeDeadlineSentence() {
        var msg = OpportunityPredictionAlertMessage.from("新疆某央企", END, PREDICTED);
        assertEquals("【商机预测】新疆某央企", msg.title());
        assertEquals("服务周期将于 2026-08-14 截止，预测下次招标时间为 2026-02-14，请到CRM创建商机。", msg.body());
    }

    @Test
    @DisplayName("无服务周期截止时间：省略截止句")
    void withoutEndDateShouldOmitDeadlineSentence() {
        var msg = OpportunityPredictionAlertMessage.from("新疆某企业", null, PREDICTED);
        assertEquals("【商机预测】新疆某企业", msg.title());
        assertEquals("预测下次招标时间为 2026-02-14，请到CRM创建商机。", msg.body());
    }

    @Test
    @DisplayName("招标主体为空：标题用兜底占位")
    void blankPurchaserShouldFallback() {
        var msg = OpportunityPredictionAlertMessage.from("  ", null, PREDICTED);
        assertEquals("【商机预测】（未知招标主体）", msg.title());
    }

    @Test
    @DisplayName("预测时间为空：正文用占位符")
    void nullPredictedShouldUsePlaceholder() {
        var msg = OpportunityPredictionAlertMessage.from("新疆某企业", null, null);
        assertEquals("预测下次招标时间为 —，请到CRM创建商机。", msg.body());
    }
}
