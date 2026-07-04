package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.BidCaseSliceMatchCandidate;
import com.xiyu.bid.casework.domain.model.BidCaseSliceMatchCriteria;
import com.xiyu.bid.casework.domain.model.BidCaseSliceRecommendation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BidCaseSliceMatchPolicy")
class BidCaseSliceMatchPolicyTest {

    private final BidCaseSliceMatchPolicy policy = new BidCaseSliceMatchPolicy();

    @Test
    void emptyCandidates_shouldReturnEmptyList() {
        var criteria = new BidCaseSliceMatchCriteria("查询", new float[]{1.0f}, "技术", Set.of("查询"));
        List<BidCaseSliceRecommendation> result = policy.match(criteria, List.of(), 10);
        assertTrue(result.isEmpty());
    }

    @Test
    void nullCriteria_shouldReturnEmptyList() {
        var candidate = candidate(1L, "p1", "技术文件.docx", "技术", "标题", "正文", 100, 5, 1, new float[]{1.0f, 0.0f});
        List<BidCaseSliceRecommendation> result = policy.match(null, List.of(candidate), 10);
        assertTrue(result.isEmpty());
    }

    @Test
    void highCosineAndLabelMatch_shouldRankFirst() {
        var criteria = new BidCaseSliceMatchCriteria(
                "物流系统优势",
                new float[]{1.0f, 0.0f, 0.0f},
                "技术",
                Set.of("物流", "系统")
        );

        var techSlice = candidate(1L, "p1", "技术.docx", "技术", "物流系统优势", "正文", 200, 6, 1, new float[]{1.0f, 0.0f, 0.0f});
        var businessSlice = candidate(2L, "p2", "商务.docx", "商务", "售后服务", "正文", 200, 2, 3, new float[]{0.0f, 1.0f, 0.0f});

        List<BidCaseSliceRecommendation> result = policy.match(criteria, List.of(techSlice, businessSlice), 20);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).sliceId());
        assertTrue(result.get(0).finalScore() > result.get(1).finalScore());
        assertTrue(result.get(0).matchReason().contains("语义相似"));
        assertTrue(result.get(0).matchReason().contains("文件类别一致"));
    }

    @Test
    void titleJaccard_shouldBoostScore() {
        var criteria = new BidCaseSliceMatchCriteria(
                "售后服务保障措施",
                new float[]{1.0f, 0.0f},
                null,
                Set.of("售后", "服务", "保障", "措施")
        );

        var titleMatch = candidate(1L, "p1", "技术.docx", "技术", "售后服务保障措施", "正文", 100, 3, 2, new float[]{0.5f, 0.0f});
        var noTitleMatch = candidate(2L, "p2", "技术.docx", "技术", "其他标题", "正文", 100, 3, 2, new float[]{0.5f, 0.0f});

        List<BidCaseSliceRecommendation> result = policy.match(criteria, List.of(titleMatch, noTitleMatch), 20);

        assertEquals(1L, result.get(0).sliceId());
        assertTrue(result.get(0).finalScore() > result.get(1).finalScore());
        assertTrue(result.get(0).matchReason().contains("标题匹配"));
    }

    @Test
    void sameProjectConcentration_shouldLimitPerProjectCount() {
        var criteria = new BidCaseSliceMatchCriteria(
                "技术方案",
                new float[]{1.0f, 0.0f},
                null,
                Set.of("技术", "方案")
        );

        var p1a = candidate(1L, "p1", "a.docx", "技术", "标题A", "正文", 100, 5, 1, new float[]{1.0f, 0.0f});
        var p1b = candidate(2L, "p1", "b.docx", "技术", "标题B", "正文", 100, 5, 1, new float[]{0.9f, 0.0f});
        var p1c = candidate(3L, "p1", "c.docx", "技术", "标题C", "正文", 100, 5, 1, new float[]{0.8f, 0.0f});
        var p2 = candidate(4L, "p2", "d.docx", "技术", "标题D", "正文", 100, 5, 1, new float[]{0.7f, 0.0f});
        var p3 = candidate(5L, "p3", "e.docx", "技术", "标题E", "正文", 100, 5, 1, new float[]{0.6f, 0.0f});

        List<BidCaseSliceRecommendation> result = policy.match(criteria, List.of(p1a, p1b, p1c, p2, p3), 4);

        assertEquals(4, result.size());
        long p1Count = result.stream().filter(r -> "p1".equals(r.projectDir())).count();
        assertEquals(2, p1Count);
    }

    @Test
    void topK_shouldLimitResultSize() {
        var criteria = new BidCaseSliceMatchCriteria(
                "方案",
                new float[]{1.0f, 0.0f},
                null,
                Set.of("方案")
        );

        var c1 = candidate(1L, "p1", "a.docx", "技术", "标题A", "正文", 100, 5, 1, new float[]{1.0f, 0.0f});
        var c2 = candidate(2L, "p2", "b.docx", "技术", "标题B", "正文", 100, 5, 1, new float[]{0.9f, 0.0f});
        var c3 = candidate(3L, "p3", "c.docx", "技术", "标题C", "正文", 100, 5, 1, new float[]{0.8f, 0.0f});

        List<BidCaseSliceRecommendation> result = policy.match(criteria, List.of(c1, c2, c3), 2);

        assertEquals(2, result.size());
    }

    private BidCaseSliceMatchCandidate candidate(Long id, String projectDir, String docxFile, String docxLabel,
                                                  String title, String textPreview, int textLength, int paraCount,
                                                  int level, float[] vector) {
        return new BidCaseSliceMatchCandidate(id, projectDir, docxFile, docxLabel, title, textPreview,
                textLength, paraCount, level, vector);
    }
}
