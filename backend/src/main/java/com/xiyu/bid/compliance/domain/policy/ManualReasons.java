// Input: MANUAL 态 reasonCode（PRD §3.5 五值集合）+ 明细文案 + 证据
// Output: conclusion 带 [reasonCode] 前缀的 MANUAL 结果（纯核心统一格式化，避免散落拼接）
// Pos: compliance/domain/policy/ - reasonCode 文案统一格式化（spec 049 US5 / T049，data-model.md §2.2）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.List;

/**
 * MANUAL 态 reasonCode 前缀统一格式化（纯核心）.
 *
 * <p>MANUAL_REVIEW 必附 reasonCode ∈ PRD §3.5 五值集合，
 * 由本类统一拼接 {@code [reasonCode] detail}，禁止各 lane 散落拼接.
 */
public final class ManualReasons {

    /** 证据不足（单库查询失败/知识库数据缺失）. */
    public static final String EVIDENCE_INSUFFICIENT = "EVIDENCE_INSUFFICIENT";
    /** 语义歧义，LLM 无法给出确定结论. */
    public static final String AMBIGUOUS = "AMBIGUOUS";
    /** 单项 LLM 调用失败/响应不可解析. */
    public static final String LOW_CONFIDENCE = "LOW_CONFIDENCE";
    /** 招标要求不可用（招标未上传/提取失败/AI 未启用）. */
    public static final String REFERENCE_MISSING = "REFERENCE_MISSING";
    /** 需人工复核（含 AI 未启用语义项降级）. */
    public static final String MANUAL_REVIEW_REQUIRED = "MANUAL_REVIEW_REQUIRED";

    private ManualReasons() {
    }

    /**
     * 生成带 reasonCode 前缀的 MANUAL 结果.
     */
    public static QualityCheckItemResult manual(
            final BidQualityCheckItem item,
            final String reasonCode,
            final String detail,
            final String suggestion,
            final List<QualityEvidence> evidences) {
        return QualityCheckItemResult.manualReview(
                item, "[" + reasonCode + "] " + detail, suggestion, evidences);
    }
}
