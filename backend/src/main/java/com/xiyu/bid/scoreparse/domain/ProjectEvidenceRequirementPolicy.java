package com.xiyu.bid.scoreparse.domain;

import java.util.ArrayList;
import java.util.List;

/** 识别业绩条款中无法由现有结构化业绩字段自动证明的强制材料。 */
public final class ProjectEvidenceRequirementPolicy {

    public Assessment assess(String detail) {
        if (detail == null || detail.isBlank()) {
            return new Assessment(List.of());
        }
        List<String> manualChecks = new ArrayList<>();
        if (requiresCustomerList(detail)) {
            manualChecks.add(detail.contains("盖章") || detail.contains("加盖")
                    ? "盖章的合作客户名单、项目联系人及供货覆盖城市"
                    : "合作客户名单、项目联系人及供货覆盖区域");
        }
        if (containsAny(detail, "银行流水", "付款流水", "付款凭证", "银行回单", "转账记录")) {
            manualChecks.add(detail.contains("流水") ? "合同付款银行流水" : "合同付款凭证");
        }
        if (detail.contains("发票")) {
            manualChecks.add("对应发票");
        }
        if (containsAny(detail, "合同关键页", "合同复印件", "中标通知书", "中标证明")) {
            manualChecks.add("合同或中标证明材料");
        }
        return new Assessment(manualChecks);
    }

    private boolean requiresCustomerList(String detail) {
        return (detail.contains("客户名单") || detail.contains("客户名录") || detail.contains("客户清单"))
                && (detail.contains("项目联系人") || detail.contains("联系人"))
                && containsAny(detail, "覆盖城市", "供货城市", "供货区域", "覆盖区域");
    }

    private boolean containsAny(String detail, String... terms) {
        for (String term : terms) {
            if (detail.contains(term)) {
                return true;
            }
        }
        return false;
    }

    public record Assessment(List<String> manualChecks) {
        public Assessment {
            manualChecks = manualChecks == null ? List.of() : List.copyOf(manualChecks);
        }

        public boolean requiresManualReview() {
            return !manualChecks.isEmpty();
        }
    }
}
