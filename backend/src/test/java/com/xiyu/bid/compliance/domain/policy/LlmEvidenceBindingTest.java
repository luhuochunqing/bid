package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class LlmEvidenceBindingTest {
    @Test
    void missingModelQuoteMustNotLocateFirstGenericKeyword() {
        var bid = new DocumentContent("bid.pdf", "h", "授权委托书", List.of(
                new DocumentContent.PageContent(2, "目录\n授权委托书......80", List.of()),
                new DocumentContent.PageContent(80, "授权委托书\n授权范围缺失", List.of())));
        var llm = new LlmAdjudicator(p -> LlmVerdict.success(
                new LlmVerdict("MANUAL", "AMBIGUOUS", "需要复核授权范围", "核对正文")));
        var result = llm.adjudicateCandidate(BidQualityCheckItem.AUTHORIZATION_LETTER, bid,
                DocumentContent.empty(), TenderRequirements.empty(), List.of(), List.of());
        assertThat(result.evidences()).noneMatch(e -> e.sourceType().equals("BID"));
    }
    @Test
    void modelQuoteBindsToItsExactTriggerChunk() {
        var bid = new DocumentContent("bid.pdf", "h", "", List.of(
                new DocumentContent.PageContent(2, "授权委托书：完整", List.of()),
                new DocumentContent.PageContent(80, "授权委托书：授权范围缺失", List.of())));
        for (var verdict : List.of(
                new LlmVerdict("MANUAL", "AMBIGUOUS", "需要复核", "核对", 80, "授权范围缺失"),
                new LlmVerdict("MANUAL", "AMBIGUOUS", "需要复核", "核对", 2, "授权范围缺失"),
                new LlmVerdict("MANUAL", "AMBIGUOUS", "需要复核", "核对", 80, "模型杜撰"))) {
            var llm = new LlmAdjudicator(p -> LlmVerdict.success(verdict));
            var result = llm.adjudicateCandidate(BidQualityCheckItem.AUTHORIZATION_LETTER, bid,
                    DocumentContent.empty(), TenderRequirements.empty(), List.of(), List.of());
            var evidence = result.evidences().stream().filter(e -> e.sourceType().equals("BID")).toList();
            if (verdict.evidenceChunk() == 80 && verdict.evidenceQuote().equals("授权范围缺失")) {
                assertThat(evidence).hasSize(1);
                assertThat(evidence.getFirst().pageNumber()).isEqualTo(80);
                assertThat(evidence.getFirst().quote()).isEqualTo("授权范围缺失");
            } else {
                assertThat(evidence).isEmpty();
            }
        }
    }

}
