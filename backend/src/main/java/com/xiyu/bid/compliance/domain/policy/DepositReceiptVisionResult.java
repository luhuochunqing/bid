package com.xiyu.bid.compliance.domain.policy;

import java.util.List;

/** 保证金凭证视觉提取结果。 */
public record DepositReceiptVisionResult(
        boolean available,
        String unavailableReason,
        List<Observation> observations,
        String model) {

    public DepositReceiptVisionResult {
        unavailableReason = unavailableReason == null ? "" : unavailableReason;
        observations = observations == null ? List.of() : List.copyOf(observations);
        model = model == null ? "" : model;
    }

    public static DepositReceiptVisionResult available(
            final List<Observation> observations, final String model) {
        return new DepositReceiptVisionResult(true, "", observations, model);
    }

    public static DepositReceiptVisionResult unavailable(final String reason) {
        return new DepositReceiptVisionResult(false, reason, List.of(), "");
    }

    public record Observation(
            int pageNumber,
            boolean voucherDetected,
            double confidence,
            String payer,
            String payee,
            String amount,
            String transactionDate,
            String bankSerialNumber,
            Double x,
            Double y,
            Double width,
            Double height) {
        public Observation {
            payer = safe(payer);
            payee = safe(payee);
            amount = safe(amount);
            transactionDate = safe(transactionDate);
            bankSerialNumber = safe(bankSerialNumber);
        }

        private static String safe(final String value) {
            return value == null ? "" : value.trim();
        }
    }
}
