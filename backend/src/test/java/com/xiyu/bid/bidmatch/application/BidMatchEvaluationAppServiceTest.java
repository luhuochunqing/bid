package com.xiyu.bid.bidmatch.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.bidmatch.domain.BidMatchDimension;
import com.xiyu.bid.bidmatch.domain.BidMatchModelVersionSnapshot;
import com.xiyu.bid.bidmatch.domain.BidMatchRule;
import com.xiyu.bid.bidmatch.domain.BidMatchScoringModel;
import com.xiyu.bid.bidmatch.domain.MatchEvidence;
import com.xiyu.bid.bidmatch.dto.BidMatchEvaluationResponse;
import com.xiyu.bid.bidmatch.infrastructure.persistence.entity.BidMatchScoreEvaluationEntity;
import com.xiyu.bid.bidmatch.infrastructure.persistence.repository.BidMatchScoreEvaluationJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidMatchEvaluationAppServiceTest {

    @Mock
    private BidMatchModelAppService modelAppService;

    @Mock
    private BidMatchEvidenceAssembler evidenceAssembler;

    @Mock
    private BidMatchScoreEvaluationJpaRepository evaluationRepository;

    @Test
    @DisplayName("评分评估一次读取 active version，并用同一份快照和版本ID落库")
    void evaluate_ShouldReadActiveVersionOnce() {
        BidMatchModelVersionSnapshot snapshot = snapshot();
        when(modelAppService.activeVersion())
                .thenReturn(new BidMatchActiveModelVersion(7001L, snapshot));
        when(evidenceAssembler.assemble(110L)).thenReturn(new BidMatchEvidenceBundle(
                new MatchEvidence(
                        "evidence-110",
                        Map.of("tender.searchText", "智慧园区数字化运维平台"),
                        Map.of(),
                        Set.of()
                ),
                Map.of("tender", Map.of("id", 110L)),
                "evidence-110"
        ));
        when(evaluationRepository.save(any(BidMatchScoreEvaluationEntity.class)))
                .thenAnswer(invocation -> {
                    BidMatchScoreEvaluationEntity entity = invocation.getArgument(0);
                    entity.setId(9001L);
                    return entity;
                });
        BidMatchJsonCodec jsonCodec = new BidMatchJsonCodec(new ObjectMapper().findAndRegisterModules());
        BidMatchEvaluationAppService service = new BidMatchEvaluationAppService(
                modelAppService,
                evidenceAssembler,
                evaluationRepository,
                jsonCodec,
                new BidMatchEvaluationMapper(jsonCodec)
        );

        BidMatchEvaluationResponse response = service.evaluate(110L);

        ArgumentCaptor<BidMatchScoreEvaluationEntity> entityCaptor =
                ArgumentCaptor.forClass(BidMatchScoreEvaluationEntity.class);
        verify(evaluationRepository).save(entityCaptor.capture());
        BidMatchScoreEvaluationEntity saved = entityCaptor.getValue();
        assertThat(saved.getModelId()).isEqualTo(77L);
        assertThat(saved.getModelVersionId()).isEqualTo(7001L);
        assertThat(saved.getModelVersionNo()).isEqualTo(3);
        assertThat(response.modelVersionId()).isEqualTo(7001L);
        verify(modelAppService).activeVersion();
        verify(modelAppService, never()).activeSnapshot();
        verify(modelAppService, never()).activeVersionId();
    }

    private BidMatchModelVersionSnapshot snapshot() {
        BidMatchScoringModel model = new BidMatchScoringModel(
                77L,
                "自定义匹配模型",
                "测试 active version 只读取一次",
                List.of(BidMatchDimension.enabled("tender", "标讯文本", 100, List.of(
                        BidMatchRule.keywordAny(
                                "keyword",
                                "关键词命中",
                                "tender.searchText",
                                List.of("智慧园区"),
                                100
                        )
                ))),
                5
        );
        return new BidMatchModelVersionSnapshot(77L, 3, model);
    }
}
