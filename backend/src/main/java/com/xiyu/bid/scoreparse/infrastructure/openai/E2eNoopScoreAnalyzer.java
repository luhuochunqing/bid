// Input: 无（e2e profile 专用确定性桩）
// Output: OpenAiScoreAnalyzer 的 e2e 替身 —— 不发起任何 LLM 调用
// Pos: scoreparse/infrastructure/openai — E2E 桩（对齐 biddraftagent/E2eTenderDocumentAnalyzer 范式）
// 维护声明:
//   背景：048 提交为 OpenAiScoreAnalyzer 加了 @Profile("!e2e")，但三个消费方
//   （ScoreParseAppService/ScoreScoringAppService/ScoreItemAssessor）按具体类注入且无条件注册，
//   导致 e2e profile 启动即缺 Bean 崩溃（E2E 栈 backend.log 实证 NPE/NoSuchBean）。
//   本桩以 @Primary + @Profile("e2e") 子类补位：召回返回空候选池、打分返回 quoteMissing=true，
//   让 e2e 栈可启动且不产生 LLM 外呼；scoreparse 全链路的功能验证仍由 test profile 单测覆盖。
package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.scoreparse.domain.DocumentImage;
import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@Primary
@Profile("e2e")
public class E2eNoopScoreAnalyzer extends OpenAiScoreAnalyzer {

    public E2eNoopScoreAnalyzer() {
        super(null, null, null);
    }

    @Override
    public RecallOutcome recallCandidates(String fullText, String structuredMetadata,
                                          ProgressReporter progress) {
        return new RecallOutcome(List.of(), 0);
    }

    @Override
    public List<ScoreCandidate> recheckGaps(String fullTextExcerpt, List<ScoreCandidate> knownItems) {
        return List.of();
    }

    @Override
    public ScoreAssessmentOutput assessObjective(String scoreItemDetail, BigDecimal weight,
                                                 String bidDocExcerpt) {
        return missingQuoteOutput();
    }

    @Override
    public ScoreAssessmentOutput assessObjective(String scoreItemDetail, BigDecimal weight,
                                                 String bidDocExcerpt, List<DocumentImage> images) {
        return missingQuoteOutput();
    }

    @Override
    public ScoreAssessmentOutput assessSubjective(String scoreItemDetail, String bidDocExcerpt) {
        ScoreAssessmentOutput output = new ScoreAssessmentOutput();
        output.actualScore = null;
        output.suggestion = "E2E 桩：无主观项建议";
        return output;
    }

    private static ScoreAssessmentOutput missingQuoteOutput() {
        ScoreAssessmentOutput output = new ScoreAssessmentOutput();
        output.quoteMissing = true;
        return output;
    }
}
