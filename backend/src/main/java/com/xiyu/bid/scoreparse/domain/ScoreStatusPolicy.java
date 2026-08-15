// Input: 预计得分 / 权重 / 评分类别 / 标记（过期等）
// Output: statusStage1（OK / DANGER / PENDING）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-015

package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;

/**
 * 满足状态判定策略（spec 041 FR-015）。
 *
 * <p>客观项满分 = OK；零分 = DANGER；部分得分或证书过期 = PENDING；主观项 = PENDING。
 * <p>过期标记优先于零分判定：过期证书算命中（Edge Cases），语义是待确认而非不满足。
 */
public class ScoreStatusPolicy {

    public static final String OK = "OK";
    public static final String DANGER = "DANGER";
    public static final String PENDING = "PENDING";

    private static final String TYPE_SUBJECTIVE = "SUBJECTIVE";

    /**
     * @param estScore  预计得分（主观项 / 未计算为 null）
     * @param weight    评分项权重
     * @param scoreType OBJECTIVE / SUBJECTIVE
     * @param flagged   命中记录携带标记（证书过期 / 授权即将到期）
     */
    public String evaluate(BigDecimal estScore, BigDecimal weight, String scoreType, boolean flagged) {
        if (TYPE_SUBJECTIVE.equals(scoreType)) {
            return PENDING;
        }
        if (estScore == null) {
            return PENDING;
        }
        if (flagged) {
            return PENDING;
        }
        if (estScore.compareTo(weight) >= 0) {
            return OK;
        }
        if (estScore.compareTo(BigDecimal.ZERO) <= 0) {
            return DANGER;
        }
        return PENDING;
    }
}
