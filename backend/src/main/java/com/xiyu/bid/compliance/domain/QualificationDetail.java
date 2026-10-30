// Input: 招标资质要求/匹配证书/检索状态/证明位置/剩余天数/说明/材料事实；旧记录保守推导事实
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
        String remark,
        Finding finding
) implements Serializable {

    public enum Finding { LOOKUP_MISS, EVIDENCE_INCOMPLETE, MATCHED_REFERENCE, EXPIRED, GRADE_INSUFFICIENT, UNKNOWN }

    public QualificationDetail(final String requirement, final String matchedCertName, final String status,
            final Integer bidPageNumber, final String bidQuote, final Integer remainingDays, final String remark) {
        this(requirement, matchedCertName, status, bidPageNumber, bidQuote, remainingDays, remark, null);
    }

    public QualificationDetail {
        requirement = requirement == null ? "" : requirement.trim();
        matchedCertName = matchedCertName == null ? "" : matchedCertName.trim();
        status = status == null ? "UNMATCHED" : status.trim();
        bidQuote = bidQuote == null ? "" : bidQuote.trim();
        remark = remark == null ? "" : remark.trim();
        if (finding == null) {
            finding = "UNMATCHED".equalsIgnoreCase(status) ? Finding.LOOKUP_MISS
                    : "EXPIRED".equalsIgnoreCase(status) && !matchedCertName.isBlank()
                        && remainingDays != null && remainingDays < 0 ? Finding.EXPIRED
                    : ("MATCHED".equalsIgnoreCase(status) || "ATTENTION".equalsIgnoreCase(status))
                        && !matchedCertName.isBlank() && bidPageNumber != null && bidPageNumber > 0 && !bidQuote.isBlank()
                        ? Finding.MATCHED_REFERENCE : Finding.EVIDENCE_INCOMPLETE;
        }
        if (finding == Finding.EXPIRED && (matchedCertName.isBlank() || remainingDays == null || remainingDays >= 0)) {
            finding = Finding.UNKNOWN;
        }
    }

    public QualificationDetail pendingReview() {
        return new QualificationDetail(requirement, matchedCertName, "ATTENTION", bidPageNumber, bidQuote,
                remainingDays, remark, finding);
    }
}
