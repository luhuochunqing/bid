package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.BidChapterDirtySet;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreScoringItemPickerTest {

    private final ScoreScoringItemPicker picker = new ScoreScoringItemPicker();

    @Test
    void unmatchedDirtyChaptersFallBackToFull() {
        String original = "# 商务\n原商务\n# 技术\n原技术\n";
        String changed = "# 商务\n原商务\n# 技术\n新技术\n";
        ScoreParseTask last = ScoreParseTask.builder()
                .chapterHashes(BidChapterHashCodec.encode(
                        BidChapterDirtySet.toHashMap(BidChapterDirtySet.split(original))))
                .build();
        ScoreItem item = ScoreItem.builder().id(1L).dim("商务").detail("报价")
                .weight(new BigDecimal("10")).build();
        ScoreResult old = ScoreResult.builder().scoreItemId(1L).quote("见商务部分").build();

        ScoreScoringItemPicker.Plan plan = picker.plan(
                List.of(item), Map.of(1L, old), changed, last, "ALL", List.of());

        assertThat(plan.outcome()).isEqualTo("FULL");
        assertThat(plan.hint()).contains("无法把评分项对应到章节");
        assertThat(plan.toAssess()).containsExactly(item);
    }

    @Test
    void incrementalHintIncludesDirtyTitles() {
        String original = "# 商务\n原商务\n# 技术\n原技术\n";
        String changed = "# 商务\n原商务\n# 技术\n新技术\n";
        ScoreParseTask last = ScoreParseTask.builder()
                .chapterHashes(BidChapterHashCodec.encode(
                        BidChapterDirtySet.toHashMap(BidChapterDirtySet.split(original))))
                .build();
        ScoreItem item = ScoreItem.builder().id(1L).dim("技术").detail("方案")
                .weight(new BigDecimal("10")).build();

        ScoreScoringItemPicker.Plan plan = picker.plan(
                List.of(item), Map.of(), changed, last, "ALL", List.of());

        assertThat(plan.outcome()).isEqualTo("INCREMENTAL");
        assertThat(plan.hint()).contains("技术");
        assertThat(plan.stageToken()).startsWith("INCREMENTAL|").contains("技术");
    }
}
