// Input: 招标资质要求原文/匹配证书名/状态/标书页码/标书摘录/剩余天数/核查说明
// Output: 资质匹配结构化比对条目（供前端弹窗表格/卡片直观展示）
// Pos: compliance/domain/ - 资质匹配明细模型
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain;

import java.io.Serializable;

/**
 * 资质匹配结构化比对条目.
 *
 * <p>承载"招标要求 vs 我司证书 vs 标书页码"的结构化比对结果，
 * 供前端弹窗以表格/卡片形式直观呈现核查依据与符合性状态。
 */
public record QualificationDetail(
        String requirement,
        String matchedCertName,
        String status,
        Integer bidPageNumber,
        String bidQuote,
        Integer remainingDays,
        String remark
) implements Serializable {

    public QualificationDetail {
        requirement = requirement == null ? "" : requirement.trim();
        matchedCertName = matchedCertName == null ? "" : matchedCertName.trim();
        status = status == null ? "UNMATCHED" : status.trim();
        bidQuote = bidQuote == null ? "" : bidQuote.trim();
        remark = remark == null ? "" : remark.trim();
    }
}
