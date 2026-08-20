// Input: 命中页码 pageNumber + 原文摘录 quote
// Output: 不可变定位结果（quote 必须为 PageContent.text 子串）
// Pos: compliance/domain/policy/ - Evidence 定位命中结果（spec 049 US4 / T040，data-model.md §3.3）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

/**
 * Evidence 真实定位命中结果（spec 049 第四步产物）.
 *
 * @param pageNumber 命中页真实页码（来自 PageContent.pageNumber）
 * @param quote 命中位置上下文摘录（必须为对应 PageContent.text 的子串，长度上限约 120 字符）
 */
public record EvidenceHit(int pageNumber, String quote) {

    public EvidenceHit {
        if (quote == null) {
            quote = "";
        }
    }
}
