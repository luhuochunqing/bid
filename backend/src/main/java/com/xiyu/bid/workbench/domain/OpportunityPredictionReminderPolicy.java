// Input: 预测招标时间 / 今天 / 上次提醒时间
// Output: 是否应发送商机预测提醒（boolean）
// Pos: workbench/domain/ - 商机预测提醒纯核心判定策略
package com.xiyu.bid.workbench.domain;

import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商机预测提醒触发策略（纯核心、无副作用）。
 *
 * <p>提醒时机：{@link OpportunityPredictionPolicy#predict} 返回的"预测招标时间"
 * 即"该去创建商机的时点"（有服务周期截止时间时，它就是截止前 6/3 个月）。
 * 当今天已到达该时点（today &gt;= predicted）且该项目从未提醒过（每项目只提醒一次）时触发。
 *
 * <p>纯函数：无副作用，不依赖 Spring，可独立单测。
 */
@UtilityClass
public class OpportunityPredictionReminderPolicy {

    /**
     * 是否应发送商机预测提醒。
     *
     * @param predictedBidDate 预测招标时间（可空）
     * @param today            当前日期
     * @param lastNotifiedAt   该项目上次提醒时间（null 表示从未提醒过）
     * @return 已到预测时点且从未提醒过时为 true
     */
    public static boolean shouldNotify(
            final LocalDate predictedBidDate, final LocalDate today, final LocalDateTime lastNotifiedAt) {
        if (predictedBidDate == null || today == null) {
            return false;
        }
        if (lastNotifiedAt != null) {
            // 每项目只提醒一次：已落提醒日志则不再重复
            return false;
        }
        return !predictedBidDate.isAfter(today);
    }
}
