// Input: 解析产出的评分项数量
// Output: 数量校验结果（0 项判定解析失败）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-006 / FR-007

package com.xiyu.bid.scoreparse.domain;

/**
 * 解析项数量校验（spec 041 FR-006 / FR-007）。
 * <p>0 项 → 解析失败终态：任务 FAILED + 明确 message。
 */
public class ItemCountCheck {

    private static final String FAILURE_MESSAGE = "未在文件中识别到评分标准章节";

    public Result check(int itemCount) {
        boolean failed = itemCount <= 0;
        return new Result(failed, failed ? FAILURE_MESSAGE : null);
    }

    /**
     * @param failed         true 表示解析失败终态
     * @param failureMessage 失败原因（面向用户展示）
     */
    public record Result(boolean failed, String failureMessage) {
    }
}
