package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BaiduOcrReplacementGuardTest {
    @Test void keepsPhysicalPagesWhenOcrCombinesTwoSeparatelyIdentifiedItems() {
        var baseline = table("base", "评分规则", new int[]{9, 11, 11, 12}, new String[]{"1", "8", "9", "14"}, new String[]{"10", "5", "10", "100"});
        var ocr = table("ocr", "评分规则", new int[]{11}, new String[]{"8 9"}, new String[]{"5 10"});
        assertThat(BaiduOcrReplacementGuard.preservedPages(List.of(baseline), List.of(ocr))).containsExactlyInAnyOrder(9, 11, 12);
    }
    @Test void neverSubstitutesForUnrelatedOrUnprovenOcr() {
        var baseline = table("base", "评分规则", new int[]{9, 11, 11, 12}, new String[]{"1", "8", "9", "14"}, new String[]{"10", "5", "10", "100"});
        for (var ocr : List.of(
                table("a", "另一评分办法", new int[]{11}, new String[]{"8 9"}, new String[]{"5 10"}),
                table("b", "评分规则", new int[]{10}, new String[]{"8 9"}, new String[]{"5 10"}),
                table("c", "评分规则", new int[]{11}, new String[]{"8"}, new String[]{"5"}),
                table("d", "评分规则", new int[]{11}, new String[]{"8 10"}, new String[]{"5 10"}),
                table("badname", "评分规则", new int[]{11}, new String[]{"8 9"}, new String[]{"5 10"}))) {
            assertThat(BaiduOcrReplacementGuard.preservedPages(List.of(baseline), List.of(ocr))).isEmpty();
        }
    }
    @Test void neverDiscardsAnotherOcrTableOnTheSamePhysicalPage() {
        var baseline = table("base", "评分规则", new int[]{9, 11, 11, 12}, new String[]{"1", "8", "9", "14"}, new String[]{"10", "5", "10", "100"});
        var collapsed = table("ocr", "评分规则", new int[]{11}, new String[]{"8 9"}, new String[]{"5 10"});
        var separate = table("other", "另一评分办法", new int[]{11}, new String[]{"1"}, new String[]{"20"});
        assertThat(BaiduOcrReplacementGuard.preservedPages(List.of(baseline), List.of(collapsed, separate))).isEmpty();
    }
    @Test void actualOriginalCellPagesProveOwnerAndAmbiguousPagesAreExcluded() {
        var original = table("base", "评分规则", new int[]{9, 10, 11, 11, 12}, new String[]{"1", "7", "8", "9", "14"}, new String[]{"10", "10", "5", "10", "100"});
        var owner = BaiduRepeatedHeaderPolicy.originalPageOwners(List.of(original));
        assertThat(owner).containsKeys(9, 10, 11, 12);
        assertThat(owner.get(9)).isSameAs(owner.get(11));
        var other = table("other", "另一评分办法", new int[]{11}, new String[]{"1"}, new String[]{"20"});
        assertThat(BaiduRepeatedHeaderPolicy.originalPageOwners(List.of(original, other))).doesNotContainKey(11);
    }
    private CanonicalTable table(String id, String section, int[] pages, String[] codes, String[] scores) {
        var cells = new ArrayList<CanonicalCellDto>();
        for (int row = 0; row < pages.length; row++) {
            cells.add(new CanonicalCellDto(id + "_r" + row + "_c0", row, 0, codes[row], codes[row], 1, 1, false, "pdf:page=" + pages[row], pages[row], id + "-r" + row));
            String name = id.equals("badname") ? "无关名称" : codes[row].equals("8 9") ? "甲乙" : codes[row].equals("8") ? "甲" : codes[row].equals("9") ? "乙" : "事项" + codes[row];
            cells.add(new CanonicalCellDto(id + "_r" + row + "_c1", row, 1, name, name, 1, 1, false, "pdf:page=" + pages[row], pages[row], id + "-r" + row));
            cells.add(new CanonicalCellDto(id + "_r" + row + "_c2", row, 2, scores[row], scores[row], 1, 1, false, "pdf:page=" + pages[row], pages[row], id + "-r" + row));
        }
        return new CanonicalTable(id, pages[0], section, pages.length, 3, List.of(), cells, true, "PDF");
    }
}
