package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.dto.ScoreParseReviewDTO;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreParseReviewTotalPolicyTest {
    @Test
    void distinguishesUnknownConflictOneSchemeAcrossTablesAndMultipleSchemes() {
        var task = ScoreParseTask.builder().taskId("task").runSummary(
                "{\"tableSplits\":[{\"tableId\":\"a\",\"declaredTotal\":100},"
                        + "{\"tableId\":\"b\",\"declaredTotal\":150}]}").build();
        var multiTable = scheme("scheme-a", "MULTI_TABLE_SAME_SCHEME", "VERIFIED", "100", List.of("a", "b"));
        var unknownMultiTable = scheme("scheme-a", "MULTI_TABLE_SAME_SCHEME", "UNKNOWN", null, List.of("a", "b"));
        var conflict = scheme("scheme-a", "MULTI_TABLE_SAME_SCHEME", "CONFLICT", null, List.of("a", "b"));
        var anotherScheme = scheme("scheme-b", "SINGLE_TABLE", "VERIFIED", "150", List.of("c"));

        assertThat(ScoreParseReviewTotalPolicy.adoptionStatus(task, List.of(multiTable))).isEqualTo("DECLARED_TOTAL");
        assertThat(ScoreParseReviewTotalPolicy.adoptionStatus(task, List.of(unknownMultiTable))).isEqualTo("UNKNOWN_TOTAL");
        assertThat(ScoreParseReviewTotalPolicy.adoptionStatus(task, List.of(conflict))).isEqualTo("CONFLICTING_TOTAL");
        assertThat(ScoreParseReviewTotalPolicy.adoptionStatus(task, List.of(multiTable, anotherScheme)))
                .isEqualTo("SCHEME_SELECTION_REQUIRED");
        assertThat(ScoreParseReviewAppService.declaredTotal(task)).isNull();
        assertThat(ScoreParseReviewTotalPolicy.adoptionStatus(task)).isEqualTo("UNKNOWN_TOTAL");
    }

    @Test
    void uncoveredSourceRowsOverrideOtherwiseValidTotals() {
        var task = ScoreParseTask.builder().taskId("task").runSummary(
                "{\"sourceDiagnostics\":[\"SOURCE_SCORE_ROW_UNCOVERED:t:t-r3\"]}").build();
        var valid = scheme("scheme-a", "SINGLE_TABLE", "VERIFIED", "100", List.of("t"));
        assertThat(ScoreParseReviewTotalPolicy.hasUncoveredRows(task)).isTrue();
        assertThat(ScoreParseReviewTotalPolicy.adoptionStatus(task, List.of(valid))).isEqualTo("SOURCE_ROWS_UNCOVERED");
        task.setRunSummary("{\"sourceDiagnostics\":[\"OTHER\"]}");
        assertThat(ScoreParseReviewTotalPolicy.hasUncoveredRows(task)).isFalse();
    }

    private static ScoreParseReviewDTO.Scheme scheme(String id, String layout, String status,
                                                      String total, List<String> tableIds) {
        return new ScoreParseReviewDTO.Scheme(id, id, tableIds, layout, status,
                total == null ? null : new BigDecimal(total), total == null ? "" : "本方案总分" + total + "分",
                "text:line[1]", false);
    }
}
