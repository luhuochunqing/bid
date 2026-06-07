package com.xiyu.bid.crm.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Routes and splits message batches according to the CRM batch size limit.
 */
public final class CrmMessageRouter {

    private CrmMessageRouter() {
    }

    /**
     * Splits a message list into sub-batches respecting the max batch size.
     */
    public static List<List<Map<String, Object>>> splitBatches(
            List<Map<String, Object>> messages, int maxBatchSize) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<List<Map<String, Object>>> batches = new ArrayList<>();
        for (int i = 0; i < messages.size(); i += maxBatchSize) {
            int end = Math.min(i + maxBatchSize, messages.size());
            batches.add(new ArrayList<>(messages.subList(i, end)));
        }
        return batches;
    }
}
