package com.xiyu.bid.bidresult.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record CompetitorWinRegistration(
        Long competitorId,
        String competitorName,
        Long projectId,
        Integer skuCount,
        String category,
        String discount,
        String paymentTerms,
        LocalDate wonAt,
        BigDecimal amount,
        String notes
) {
    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();
        if (competitorId == null && (competitorName == null || competitorName.isBlank())) {
            errors.add("竞争对手名称必填");
        }
        if (skuCount != null && skuCount < 0) {
            errors.add("SKU 数量不得为负");
        }
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            errors.add("中标金额不得为负");
        }
        if (notes != null && notes.length() > 2000) {
            errors.add("备注不得超过 2000 字");
        }
        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    public record ValidationResult(boolean valid, List<String> errors) {
        public ValidationResult {
            errors = List.copyOf(errors);
        }

        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }
}
