// Input: LLM JSON response for completeness gap recheck (完整性校验 Agent)
// Output: Mutable POJO – missed items + checked zones
// Pos: scoreparse/infrastructure/openai
// 契约见 specs/041-ai-score-parse-backend/contracts/llm-output-schema.md §2
package com.xiyu.bid.scoreparse.infrastructure.openai;

import java.util.List;

public class ScoreGapRecheckOutput {

    public List<ScoreCandidateOutput.Candidate> missedItems;

    public List<String> checkedZones;
}
