package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

/** Explicit local-only replay; customer text and captured API responses never enter Git. */
@EnabledIfEnvironmentVariable(named = "XIYU_PRIMARY_REPLAY_DIR", matches = ".+")
class PrimaryScoreIncidentReplayTest {
    @Test void captured1135DuplicatedFactsCannotBePublishedByFastRoute() throws Exception {
        Path root = Path.of(System.getenv("XIYU_PRIMARY_REPLAY_DIR"));
        String source = Files.readString(root.resolve("tender.txt"));
        var data = new ObjectMapper().readTree(root.resolve("review.json").toFile()).path("data");
        assertThat(data.path("projectId").asLong()).isEqualTo(1135);
        assertThat(data.path("parseTaskId").asLong()).isEqualTo(159);
        var codes = new HashMap<String, String>();
        data.path("facts").forEach(f -> codes.put(f.path("factId").asText(), f.path("code").asText()));
        var facts = new ArrayList<MultimodalScoreFact>();
        data.path("facts").forEach(f -> facts.add(new MultimodalScoreFact("评分", f.path("code").asText(),
                codes.getOrDefault(f.path("parentFactId").asText(), ""), f.path("dimension").asText(),
                f.path("weight").decimalValue(), f.path("sourceText").asText(), f.path("scoreType").asText(),
                null, "", List.of(), "", 1, ChildAggregation.valueOf(f.path("childAggregation").asText()))));
        assertThat(facts).hasSize(61);
        var candidates = PrimaryScoreFactPolicy.candidates(facts, source);
        assertThat(candidates).isEmpty();
        var result = DualTrackScoreExtractor.dispatch(candidates, source, new BigDecimal("100"), List::of);
        assertThat(result.track()).isEqualTo(DualTrackScoreExtractor.Track.FALLBACK);
        System.out.println("1135 task159 replay: 61 recorded facts rejected by primary; original total row=100; no writes");
    }
}
