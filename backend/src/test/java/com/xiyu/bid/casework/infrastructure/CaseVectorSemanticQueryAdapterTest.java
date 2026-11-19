package com.xiyu.bid.casework.infrastructure;

import com.xiyu.bid.ai.client.AiProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaseVectorSemanticQueryAdapterTest {

    @Mock
    private AiProvider aiProvider;

    @Mock
    private KnowledgeCaseRepository knowledgeCaseRepository;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("向量相似度映射：将 bid_case_slice.id 映射为内容匹配的 knowledge_case.id，杜绝跨表主键张冠李戴")
    void mapsOnlyWhenSourceAndPreviewIdentifyOneCase(boolean ambiguous) {
        BidCaseSlice slice = coldChainSlice();

        BidCaseSliceVectorCache vectorCache = new BidCaseSliceVectorCache();
        vectorCache.load(List.of(slice));

        KnowledgeCase unrelatedWithSameId = new KnowledgeCase();
        unrelatedWithSameId.setId(42L);
        unrelatedWithSameId.setScoringPointTitle("物流配送能力");
        unrelatedWithSameId.setResponseText("提供覆盖全国的车辆运输与配送服务。");
        unrelatedWithSameId.setStatus("ACTIVE");

        KnowledgeCase realTarget = new KnowledgeCase();
        realTarget.setId(9001L);
        realTarget.setScoringPointTitle("冷链恒温监控方案");
        realTarget.setSourceSectionTitle("冷链恒温监控方案");
        realTarget.setResponseText("实时监控冷链仓储温湿度并在超限后自动触发多级告警与喷淋降温处置流程，确保全程可追溯。");
        realTarget.setStatus("ACTIVE");
        realTarget.setObsBidFileKey("2026年（投标文件）/p1/bid.docx");

        KnowledgeCase differentProject = new KnowledgeCase();
        differentProject.setId(9002L);
        differentProject.setScoringPointTitle(realTarget.getScoringPointTitle());
        differentProject.setResponseText(realTarget.getResponseText());
        differentProject.setObsBidFileKey("2026年（投标文件）/p2/bid.docx");
        differentProject.setStatus("ACTIVE");

        KnowledgeCase differentSection = new KnowledgeCase();
        differentSection.setId(9003L);
        differentSection.setScoringPointTitle(realTarget.getScoringPointTitle());
        differentSection.setResponseText("实时监控冷链仓储温湿度并在超限后自动触发多级告警，告警仅留痕，后续改为执行仓库盘点。");
        differentSection.setObsBidFileKey(realTarget.getObsBidFileKey());
        differentSection.setStatus("ACTIVE");

        when(aiProvider.embed("智能温控")).thenReturn(new float[]{1.0f, 0.0f});
        List<KnowledgeCase> cases = new ArrayList<>(List.of(unrelatedWithSameId, realTarget, differentProject, differentSection));
        if (ambiguous) {
            KnowledgeCase samePreview = new KnowledgeCase();
            samePreview.setId(9004L);
            samePreview.setStatus("ACTIVE");
            samePreview.setSourceSectionTitle(realTarget.getSourceSectionTitle());
            samePreview.setObsBidFileKey(realTarget.getObsBidFileKey());
            samePreview.setResponseText(slice.getTextPreview() + "随后记录交接信息。");
            cases.add(samePreview);
        }
        when(knowledgeCaseRepository.findAll(ArgumentMatchers.<Specification<KnowledgeCase>>any()))
                .thenReturn(cases);

        CaseVectorSemanticQueryAdapter adapter = new CaseVectorSemanticQueryAdapter(
                vectorCache, aiProvider, null, knowledgeCaseRepository);

        Map<Long, Double> result = adapter.querySimilarities("智能温控");

        if (ambiguous) {
            assertThat(result).isEmpty();
        } else {
            assertThat(result).containsOnlyKeys(9001L);
            assertThat(result.get(9001L)).isGreaterThanOrEqualTo(0.99);
        }
    }

    @Test
    @DisplayName("仓储缺失安全兜底：未注入 KnowledgeCaseRepository 时返回空 Map，绝不泄漏切片主键")
    void returnsEmptyMapWhenKnowledgeCaseRepositoryIsNull() {
        BidCaseSlice slice = coldChainSlice();

        BidCaseSliceVectorCache vectorCache = new BidCaseSliceVectorCache();
        vectorCache.load(List.of(slice));

        when(aiProvider.embed("智能温控")).thenReturn(new float[]{1.0f, 0.0f});

        CaseVectorSemanticQueryAdapter adapter = new CaseVectorSemanticQueryAdapter(vectorCache, aiProvider, null);
        assertThat(adapter.querySimilarities("智能温控")).isEmpty();
    }

    private static BidCaseSlice coldChainSlice() {
        BidCaseSlice slice = new BidCaseSlice();
        slice.setId(42L);
        slice.setProjectDir("p1");
        slice.setProjectIdx(1);
        slice.setDocxFile("bid.docx");
        slice.setDocxLabel("技术");
        slice.setSectionIdx(1);
        slice.setLevel(1);
        slice.setTitle("冷链恒温监控方案");
        slice.setTextPreview("实时监控冷链仓储温湿度并在超限后自动触发多级告警与喷淋降温处置流程。");
        slice.setTextLength(36);
        slice.setParaCount(1);
        slice.setEmbedding(EmbeddingVectorCodec.encode(new float[]{1.0f, 0.0f}));
        return slice;
    }
}
