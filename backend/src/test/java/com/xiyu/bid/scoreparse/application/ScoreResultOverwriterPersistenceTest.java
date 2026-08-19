// Input: 同一 scoreItemId 的旧结果 + 新结果集
// Output: 重新打分覆盖落库不撞 uk_sr_score_item（T020 场景 2 复现：Hibernate flush INSERT 先于 DELETE）
// Pos: scoreparse/application — spec 048 T020 验证发现的重新打分唯一约束冲突修复
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DataJpaTest
@ActiveProfiles("test")
@Import(ScoreResultOverwriter.class)
class ScoreResultOverwriterPersistenceTest {

    @Autowired
    private ScoreResultRepository resultRepository;

    @Autowired
    private ScoreResultOverwriter overwriter;

    @Test
    void replaceAtomically_WithExistingResult_ShouldOverwriteWithoutUniqueViolation() {
        // 首轮打分结果落库
        resultRepository.saveAndFlush(result(1L, "OK", "2.00", "旧依据"));
        assertThat(resultRepository.count()).isEqualTo(1);

        // 重新打分：同 scoreItemId 覆盖（修复前：flush 时 INSERT 先于 DELETE → Duplicate entry）
        assertThatCode(() -> overwriter.replaceAtomically(
                List.of(1L), List.of(result(1L, "DANGER", "0.00", "新依据"))))
                .doesNotThrowAnyException();

        List<ScoreResult> all = resultRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getStatusStage2()).isEqualTo("DANGER");
        assertThat(all.get(0).getEvidence()).isEqualTo("新依据");
    }

    @Test
    void replaceAtomically_WithMixedItems_ShouldReplaceOnlyTargetedIds() {
        resultRepository.saveAndFlush(result(1L, "OK", "2.00", "旧1"));
        resultRepository.saveAndFlush(result(2L, "OK", "5.00", "旧2"));
        resultRepository.saveAndFlush(result(3L, "OK", "7.00", "旧3"));

        overwriter.replaceAtomically(
                List.of(1L, 2L),
                List.of(result(1L, "DANGER", "0.00", "新1"), result(2L, "PENDING", "1.00", "新2")));

        List<ScoreResult> all = resultRepository.findAll();
        assertThat(all).hasSize(3);
        assertThat(all.stream().filter(r -> r.getScoreItemId().equals(1L)).findFirst().orElseThrow().getEvidence()).isEqualTo("新1");
        assertThat(all.stream().filter(r -> r.getScoreItemId().equals(2L)).findFirst().orElseThrow().getEvidence()).isEqualTo("新2");
        assertThat(all.stream().filter(r -> r.getScoreItemId().equals(3L)).findFirst().orElseThrow().getEvidence()).isEqualTo("旧3");
    }

    private ScoreResult result(Long scoreItemId, String status, String score, String evidence) {
        return ScoreResult.builder()
                .scoreItemId(scoreItemId)
                .scoringTaskId(100L)
                .actualScore(new BigDecimal(score))
                .statusStage2(status)
                .evidence(evidence)
                .build();
    }
}
