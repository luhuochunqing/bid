// Input: LLM JSON response for scoring criteria items
// Output: Mutable POJO – structured scoring criterion (Jackson compatible)
// Pos: biddraftagent/infrastructure/openai

package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.math.BigDecimal;

public class ScoringCriterionOutput {
    public String itemNumber;
    public String dimension;
    public String indicator;
    public BigDecimal weight;
}
