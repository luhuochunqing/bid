package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BaiduRepeatedHeaderContinuationTest {
    private static final String[] HEADER = {"序号", "评价维度", "评分细则", "分值"};

    @Test void sameTableWithRepeatedHeaderKeepsSourcePagesAndRemovesDuplicateHeader() {
        var first = table("p9", 9, "评分规则", HEADER, new String[]{"1", "价格", "得60分", "60"}, new String[]{"2", "服务", "得40分", "40"});
        var next = table("p10", 10, "评分规则", HEADER, new String[]{"", "", "续页描述", ""}, new String[]{"3", "合计", "", "100"});
        var actual = BaiduCrossPageTableLinker.mergeCrossPageScoreTables(List.of(first, next), java.util.Map.of(9, first, 10, first));
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0).rows()).isEqualTo(5);
        assertThat(actual.get(0).cells()).filteredOn(c -> "合计".equals(c.rawText()))
                .singleElement().satisfies(c -> assertThat(c.sourcePage()).isEqualTo(10));
    }

    @Test void separateTablesWithRepeatedHeadersMustNotBeMerged() {
        var first = table("p9", 9, "评分规则", HEADER, new String[]{"1", "价格", "得60分", "60"}, new String[]{"2", "服务", "得40分", "40"});
        var closed = table("closed", 9, "评分规则", HEADER, new String[]{"1", "价格", "得60分", "60"}, new String[]{"2", "总分", "", "100"});
        var afterClosed = table("after", 10, "评分规则", HEADER, new String[]{"", "", "续页描述", ""}, new String[]{"3", "其他", "得10分", "10"});
        assertThat(BaiduCrossPageTableLinker.mergeCrossPageScoreTables(List.of(closed, afterClosed), java.util.Map.of(9, closed, 10, closed))).hasSize(2);
        for (var next : List.of(
                table("reset", 10, "评分规则", HEADER, new String[]{"", "", "续页描述", ""}, new String[]{"1", "能力", "得10分", "10"}),
                table("gap", 10, "评分规则", HEADER, new String[]{"", "", "续页描述", ""}, new String[]{"4", "能力", "得10分", "10"}),
                table("remote", 11, "评分规则", HEADER, new String[]{"", "", "续页描述", ""}, new String[]{"3", "能力", "得10分", "10"}),
                table("other", 10, "另一方案", HEADER, new String[]{"", "", "续页描述", ""}, new String[]{"3", "能力", "得10分", "10"}))) {
            assertThat(BaiduCrossPageTableLinker.mergeCrossPageScoreTables(List.of(first, next), java.util.Map.of(9, first, 10, first, 11, first))).hasSize(2);
        }
    }

    @Test void cannotSkipAnInterveningIndependentScoreTable() {
        var first = table("first", 9, "评分规则", HEADER, new String[]{"1", "价格", "得60分", "60"}, new String[]{"2", "服务", "得40分", "40"});
        var middle = table("middle", 9, "评分规则", HEADER, new String[]{"10", "其他", "得20分", "20"});
        var next = table("next", 10, "评分规则", HEADER, new String[]{"", "", "续页描述", ""}, new String[]{"3", "能力", "得10分", "10"});
        assertThat(BaiduCrossPageTableLinker.mergeCrossPageScoreTables(List.of(first, middle, next), java.util.Map.of(9, first, 10, first))).hasSize(3);
    }
    @Test void inferredOrDefaultSectionAloneCannotProveContinuation() {
        var first = table("first", 9, "评审办法/详细评审标准", HEADER, new String[]{"1", "价格", "得60分", "60"}, new String[]{"2", "服务", "得40分", "40"});
        var next = table("next", 10, "评审办法/详细评审标准", HEADER, new String[]{"", "", "续页描述", ""}, new String[]{"3", "能力", "得10分", "10"});
        assertThat(BaiduCrossPageTableLinker.mergeCrossPageScoreTables(List.of(first, next))).hasSize(2);
    }
    private CanonicalTable table(String id, int page, String section, String[]... rows) {
        var cells = new ArrayList<CanonicalCellDto>();
        for (int row = 0; row < rows.length; row++) for (int col = 0; col < rows[row].length; col++) {
            String value = rows[row][col];
            cells.add(new CanonicalCellDto(id + "_r" + row + "_c" + col, row, col, value, value,
                    1, 1, false, "pdf:page=" + page + ";row=" + row + ";col=" + col, page, id + "-r" + row));
        }
        return new CanonicalTable(id, page, section, rows.length, 4, List.of(), cells, true, "PDF");
    }
}
