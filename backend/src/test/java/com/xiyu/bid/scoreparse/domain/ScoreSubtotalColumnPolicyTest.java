package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreSubtotalColumnPolicyTest {

    @Test
    void bracketWeightPatternMatchesVariousBracketStyles() {
        var pattern = ScoreParseConstants.BRACKET_WEIGHT_PATTERN;

        Matcher m1 = pattern.matcher("商务评分（20分）");
        assertThat(m1.find()).isTrue();
        assertThat(m1.group(1)).isEqualTo("20");

        Matcher m2 = pattern.matcher("技术评分(45分)");
        assertThat(m2.find()).isTrue();
        assertThat(m2.group(1)).isEqualTo("45");

        Matcher m3 = pattern.matcher("业绩[10分]");
        assertThat(m3.find()).isTrue();
        assertThat(m3.group(1)).isEqualTo("10");

        Matcher m4 = pattern.matcher("财务状况【5分】");
        assertThat(m4.find()).isTrue();
        assertThat(m4.group(1)).isEqualTo("5");
    }

    @Test
    void selectsColumnWithSquareOrThickBracketHeader() {
        var summary = List.of(cell("1-3", 1, 3, "10分"));
        var cells = List.of(
                cell("0-0", 0, 0, "序号"),
                cell("0-1", 0, 1, "评分因素"),
                cell("0-2", 0, 2, "评分标准"),
                cell("0-3", 0, 3, "分值[分]"),
                cell("1-3", 1, 3, "10分")
        );
        var table = new CanonicalTable("t1", 1, "第三章 评标办法", 2, 4, List.of(), cells, true);
        var selection = ScoreSubtotalColumnPolicy.select(table, 1, summary);

        assertThat(selection.unresolved()).isFalse();
        assertThat(selection.column()).isEqualTo(3);
    }

    private static CanonicalCellDto cell(String id, int row, int col, String text) {
        return new CanonicalCellDto(id, row, col, text, text, 1, 1, false, "");
    }
}
