package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.KnowledgeCategoryPolicy;
import com.xiyu.bid.scoreparse.domain.ProjectEvidenceRequirementPolicy;
import com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult;

import java.util.ArrayList;
import java.util.List;

/** 将阶段 1 匹配结果转换为面向业务用户的中文得分依据。 */
final class EstimatedScoreBasisFormatter {

    String manualReviewBasis(
            KnowledgeMatchResult match,
            ProjectEvidenceRequirementPolicy.Assessment assessment,
            boolean signedAfterChecked,
            boolean amountChecked) {
        StringBuilder basis = match.matched().isEmpty()
                ? new StringBuilder("业绩库未找到候选资料，且当前条件仍不能自动确认。")
                : new StringBuilder("业绩库找到 ").append(match.matched().size())
                        .append(" 条候选资料，但尚不能确认均为有效业绩。");
        List<String> checked = new ArrayList<>();
        if (signedAfterChecked) {
            checked.add("签约日期");
        }
        if (amountChecked) {
            checked.add("合同金额");
        }
        if (!checked.isEmpty()) {
            basis.append("已按").append(String.join("、", checked)).append("筛选；");
        }
        return basis.append("仍需人工核验：")
                .append(String.join("、", assessment.manualChecks()))
                .append("。案例库与本预判使用的业绩库是不同数据来源。")
                .toString();
    }

    String matchBasis(String category, KnowledgeMatchResult match, boolean flagged) {
        if (flagged && KnowledgeCategoryPolicy.CATEGORY_CERT.equals(category)) {
            return "证书已过期，标书需补充说明或更新证书";
        }
        String sourceLabel = KnowledgeCategoryPolicy.CATEGORY_PROJECT.equals(category) ? "业绩库" : "知识库";
        StringBuilder basis = new StringBuilder(sourceLabel).append("匹配（")
                .append(categoryLabel(category)).append("，").append(tierLabel(match.tier()))
                .append("，匹配比例 ").append(match.matchRatio()).append("%）：")
                .append(readableDetail(match.matchDetail()));
        if (!match.proofs().isEmpty()) {
            basis.append("；逐记录证明：");
            match.proofs().stream().filter(proof -> !proof.conditions().isEmpty()).forEach(proof -> {
                basis.append("[").append(proof.recordName()).append("：");
                proof.conditions().forEach(condition -> basis.append(condition.condition()).append("=")
                        .append(conditionStatusLabel(condition.status())).append("（")
                        .append(condition.evidence()).append("）；"));
                basis.append("]");
            });
        }
        if (flagged) {
            basis.append("；命中记录含过期或即将到期标记，需更新资料或人工补充说明");
        }
        return basis.toString();
    }

    private String readableDetail(String detail) {
        return String.valueOf(detail)
                .replace("expired=true", "已过期")
                .replace("expireSoon=true", "即将到期")
                .replace("expired=false", "有效期正常")
                .replace("expireSoon=false", "有效期正常");
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case null -> "知识库";
            case KnowledgeCategoryPolicy.CATEGORY_CERT -> "资质证书";
            case KnowledgeCategoryPolicy.CATEGORY_PERSON -> "人员";
            case KnowledgeCategoryPolicy.CATEGORY_PROJECT -> "业绩";
            case KnowledgeCategoryPolicy.CATEGORY_WAREHOUSE -> "仓库";
            case KnowledgeCategoryPolicy.CATEGORY_BRAND -> "品牌授权";
            case KnowledgeCategoryPolicy.CATEGORY_FINANCIAL -> "财务指标";
            case KnowledgeCategoryPolicy.CATEGORY_CREDIT -> "信用评价";
            default -> "知识库";
        };
    }

    private String conditionStatusLabel(String status) {
        return switch (status) {
            case "PASS" -> "已核验";
            case "FAIL" -> "未通过";
            default -> "缺少资料";
        };
    }

    private String tierLabel(String tier) {
        if ("FULL".equals(tier)) {
            return "完全匹配";
        }
        if ("PARTIAL".equals(tier)) {
            return "部分匹配";
        }
        return "未匹配";
    }
}
