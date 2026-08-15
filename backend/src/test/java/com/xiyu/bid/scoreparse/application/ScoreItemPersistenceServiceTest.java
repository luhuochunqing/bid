// Input: projectId/parseTaskId/candidates（合并去重后候选）
// Output: ScoreItemPersistenceService 行为验证（FR-021 覆盖语义 + code fallback + 分类落库）
// Pos: scoreparse/application 单元测试（spec 041 US1/US5）
// 维护声明: 维护者按项目SOP；FR-021 细节自 ScoreParseAppServiceTest 迁入
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FR-021 重新解析覆盖语义测试（spec 041 / data-model.md §score_item）。
 * <p>重新解析 MUST 覆盖解析结果：新批次写入前按 project_id 清理旧
 * score_item，且旧 score_result 随旧 item ID 显式删除（无 FK 级联）。
 */
@ExtendWith(MockitoExtension.class)
class ScoreItemPersistenceServiceTest {

    private static final Long PROJECT_ID = 42L;
    private static final Long PARSE_TASK_ID = 5L;

    @Mock
    private ScoreItemRepository itemRepository;
    @Mock
    private ScoreResultRepository resultRepository;

    private ScoreItemPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new ScoreItemPersistenceService(itemRepository, resultRepository);
    }

    @Test
    void reparse_deletesStaleResultsAndItemsBeforeInsert() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of(oldItem(100L), oldItem(101L)));

        service.persistItems(PROJECT_ID, PARSE_TASK_ID, List.of(
                candidate("A1", "具备 CMMI 5 级认证证书", "60"),
                candidate("A2", "近三年类似项目业绩不少于 3 个", "40")));

        // FR-021：旧打分结果随旧评分项失效清理，再删旧评分项
        verify(resultRepository).deleteByScoreItemIdIn(List.of(100L, 101L));
        verify(itemRepository).deleteByProjectId(PROJECT_ID);

        ArgumentCaptor<List<ScoreItem>> saved = capturedSavedItems();
        assertThat(saved.getValue()).hasSize(2);
        assertThat(saved.getValue().get(0).getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(saved.getValue().get(0).getParseTaskId()).isEqualTo(PARSE_TASK_ID);
    }

    @Test
    void firstParse_noStaleData_skipsDeletion() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of());

        service.persistItems(PROJECT_ID, PARSE_TASK_ID, List.of(
                candidate("A1", "具备 CMMI 5 级认证证书", "60")));

        verify(resultRepository, never()).deleteByScoreItemIdIn(anyList());
        verify(itemRepository, never()).deleteByProjectId(PROJECT_ID);
        verify(itemRepository).saveAll(anyList());
    }

    @Test
    void blankCode_fallsBackToItemIndex() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of());

        service.persistItems(PROJECT_ID, PARSE_TASK_ID, List.of(
                candidate(null, "具备 ISO9001 认证", "30"),
                candidate("  ", "项目负责人具备 PMP", "20")));

        ArgumentCaptor<List<ScoreItem>> saved = capturedSavedItems();
        assertThat(saved.getValue().get(0).getCode()).isEqualTo("1");
        assertThat(saved.getValue().get(0).getItemIndex()).isEqualTo(1);
        assertThat(saved.getValue().get(1).getCode()).isEqualTo("2");
        assertThat(saved.getValue().get(1).getItemIndex()).isEqualTo(2);
    }

    @Test
    void scoreType_classifiedByPolicy_notLlmGuess() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of());

        service.persistItems(PROJECT_ID, PARSE_TASK_ID, List.of(
                // detail 含"证书"关键词 → OBJECTIVE（即使 LLM guess 是 SUBJECTIVE）
                new ScoreCandidate("A1", "资质", "具备 CMMI 5 级认证证书",
                        new BigDecimal("60"), "SUBJECTIVE", null,
                        "具备 CMMI 5 级认证证书", "P47", "SEMANTIC"),
                // detail 含主观词 → SUBJECTIVE
                candidate("A2", "方案优秀程度高、内容完整", "40")));

        ArgumentCaptor<List<ScoreItem>> saved = capturedSavedItems();
        assertThat(saved.getValue().get(0).getScoreType()).isEqualTo("OBJECTIVE");
        assertThat(saved.getValue().get(1).getScoreType()).isEqualTo("SUBJECTIVE");
        assertThat(saved.getValue().get(0).getStatusStage1()).isEqualTo("PENDING");
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<ScoreItem>> capturedSavedItems() {
        ArgumentCaptor<List<ScoreItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(captor.capture());
        return captor;
    }

    private ScoreItem oldItem(Long id) {
        return ScoreItem.builder()
                .id(id).projectId(PROJECT_ID).itemIndex(1)
                .code("OLD").dim("旧项").detail("旧解析结果")
                .weight(new BigDecimal("10")).scoreType("OBJECTIVE")
                .statusStage1("OK").build();
    }

    private ScoreCandidate candidate(String code, String detail, String weight) {
        return new ScoreCandidate(code, "资质", detail, new BigDecimal(weight),
                "OBJECTIVE", null, detail, "P47", "SEMANTIC");
    }
}
