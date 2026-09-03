// Input: LLM structured output wire payload for fact operations (spec 059 Phase 3)
// Output: Jackson-compatible POJO
// Pos: scoreparse/infrastructure/openai
package com.xiyu.bid.scoreparse.infrastructure.openai;

import java.util.List;

public class LlmFactOperationsPayload {

    public List<OperationItem> operations;
    public Summary summary;

    public static class OperationItem {
        public String factId;
        public String action; // KEEP, CLASSIFY, LINK_PARENT, MARK_DUPLICATE, REVIEW_REQUIRED
        public String category;
        public String parentFactId;
        public List<String> duplicateFactIds;
        public Double confidence;
        public List<String> reasonCellIds;
        public String reason;

        // Prohibited trap fields:
        public Double weight;
        public String detail;
        public String sourceText;
        public String newFact;
    }

    public static class Summary {
        public Integer duplicateGroupCount;
        public Integer reviewRequiredCount;
        public String notes;
    }
}
