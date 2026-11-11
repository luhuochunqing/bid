package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreRule;
import com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata;
import com.xiyu.bid.scoreparse.domain.SourceSpan;
import com.xiyu.bid.scoreparse.entity.ScoreParseEvidence;
import com.xiyu.bid.scoreparse.repository.ScoreParseEvidenceRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseFactRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ScoreZeroDeductionEvidenceTest {
    @Test void realSourceRestorationWriterReviewAndAdoptionPreserveVerifiedDeduction() throws Exception {
        var json = new ObjectMapper();
        var loader = getClass().getClassLoader();
        var artifact = json.readValue(loader.getResourceAsStream("scoreparse-golden/case-scoring-only-adoption.artifact.json"),
                com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.class);
        var expected = json.readTree(loader.getResourceAsStream("scoreparse-golden/case-scoring-only-adoption.expected.json"));
        var table = artifact.tables().getFirst();
        var models = new java.util.ArrayList<com.xiyu.bid.scoreparse.domain.MultimodalScoreFact>();
        for (int row = 1; row < table.rows(); row++) {
            final int current = row;
            var cells = table.cells().stream().filter(c -> c.row() == current).toList();
            var maximum = cells.get(3).rawText().isBlank() ? BigDecimal.ZERO : new BigDecimal(cells.get(3).rawText());
            models.add(new com.xiyu.bid.scoreparse.domain.MultimodalScoreFact("第三章 评审办法", cells.get(0).rawText(), "",
                    cells.get(1).rawText(), maximum, cells.get(2).rawText(), "OBJECTIVE", 1, "t1",
                    List.of(cells.get(2).cellId()), cells.get(2).rawText(), 1.0));
        }
        var validation = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator()
                .validate(models, artifact, "本项目评分总分100分", new BigDecimal("100"));
        var sourceFacts = models.stream().map(f -> TextPrimaryScoreFactMapper.map(f, artifact, validation)).toList();
        var facts = mock(ScoreParseFactRepository.class);
        var evidence = mock(ScoreParseEvidenceRepository.class);
        new ScoreParseFactEvidenceWriter(facts, evidence, json).persist(1L, 9L, sourceFacts,
                validation.sourceVerification().qualifications());
        ArgumentCaptor<List<com.xiyu.bid.scoreparse.entity.ScoreParseFact>> storedFacts = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<ScoreParseEvidence>> storedEvidence = ArgumentCaptor.forClass(List.class);
        verify(facts).saveAll(storedFacts.capture());
        verify(evidence).saveAll(storedEvidence.capture());
        long id = 1;
        for (var e : storedEvidence.getValue()) e.setId(id++);
        var inputs = new java.util.ArrayList<com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy.CandidateInput>();
        for (var f : storedFacts.getValue()) {
            var local = storedEvidence.getValue().stream().filter(e -> f.getFactId().equals(e.getFactId())).toList();
            var view = ScoreParseReviewFactMapper.toFactDTO(f, local);
            if (expected.path("trustedDeductionDimension").asText().equals(f.getDimension()))
                assertThat(view.candidateEligible()).as("source=%s; qualification=%s", sourceFacts.getLast(),
                        validation.sourceVerification().qualifications().getLast()).isTrue();
            inputs.add(new com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy.CandidateInput(f.getFactId(), f.getCode(),
                    f.getDimension(), f.getWeight(), f.getSourceText(), f.getParentFactId(), null, null,
                    ScoreParseReviewFactMapper.verifiedZeroDeduction(f, local)));
        }
        var selected = inputs.stream().map(i -> i.factId()).collect(java.util.stream.Collectors.toSet());
        var result = new com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy().adopt(inputs, selected, java.util.Map.of());
        assertThat(result.success()).isTrue();
        assertThat(result.candidates()).hasSize(expected.path("trustedCount").asInt());
        assertThat(result.totalWeight()).isEqualByComparingTo("100");
        assertThat(result.candidates()).extracting(com.xiyu.bid.scoreparse.domain.ScoreCandidate::dim)
                .containsExactly("供应商业绩", "技术方案", "质量扣分");
        assertThat(storedFacts.getValue()).hasSize(expected.path("factCount").asInt());
    }

    @Test void onlyCompleteSourceQualifiedDeductionGetsTrustedMarker() {
        for (String scenario : List.of("complete", "missing-spans", "wrong-scheme", "not-deduction")) {
            boolean complete = !"missing-spans".equals(scenario);
            boolean trusted = "complete".equals(scenario);
            var facts = mock(ScoreParseFactRepository.class);
            var evidence = mock(ScoreParseEvidenceRepository.class);
            var writer = new ScoreParseFactEvidenceWriter(facts, evidence, new ObjectMapper());
            String rule = "not-deduction".equals(scenario) ? "具备有效营业执照" : "每发生一次扣2分";
            var span = new SourceSpan("doc", "sha", "docx", "scheme", 1, "t", "c", 0, rule.length(), rule, "c");
            var metadata = new ScoreSourceMetadata("", "", complete ? List.of(span) : List.of(), List.of());
            var fact = new ScoreFact("deduction", "t", List.of("c"), rule, "1", "扣分", BigDecimal.ZERO,
                    null, ScoreRule.DEDUCTION, "", "DETERMINISTIC", 1.0, ScoreFact.ValidationStatus.REVIEW_REQUIRED,
                    ChildAggregation.SUM, metadata);
            var qualification = new OriginalScoreEvidencePolicy.Qualification("deduction", "scheme", "wrong-scheme".equals(scenario) ? "OUT_OF_SCHEME" : "IN_SCHEME", true, null, "c");
            writer.persist(1L, 9L, List.of(fact), List.of(qualification));
            ArgumentCaptor<List<ScoreParseEvidence>> saved = ArgumentCaptor.forClass(List.class);
            verify(evidence).saveAll(saved.capture());
            var markers = saved.getValue().stream().filter(e -> "SOURCE_VERIFIED_ZERO_DEDUCTION".equals(e.getKind())).toList();
            assertThat(markers).as(scenario).hasSize(trusted ? 1 : 0);
            if (trusted) assertThat(markers.getFirst().getQuotedText()).isEqualTo(rule);
        }
    }
}
