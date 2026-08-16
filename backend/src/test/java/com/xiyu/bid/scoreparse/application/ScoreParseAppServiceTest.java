// Input: mock 快照/LLM/仓库依赖 + 注入自代理
// Output: FR-021 重新解析覆盖语义断言（旧 results+items 失效清理、首次解析零删除）
// Pos: scoreparse/application — spec 041 US5（T043）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.entity.BidTenderDocumentSnapshot;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.dto.ScoreParseItemsDTO;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FR-021 重新解析覆盖语义测试（spec 041 US5 / data-model.md §score_item）。
 * <p>重新解析 MUST 覆盖解析结果：新批次写入前按 project_id 清理旧
 * score_item 与级联的 score_result（评分项变化后旧打分结果失效）。
 */
@ExtendWith(MockitoExtension.class)
class ScoreParseAppServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final String TASK_ID = "task-reparse";

    @Mock
    private ScoreParseTaskRepository taskRepository;
    @Mock
    private ScoreItemRepository itemRepository;
    @Mock
    private ScoreResultRepository resultRepository;
    @Mock
    private ScoreParseTaskStateService stateService;
    @Mock
    private ScoreParseProgressService progressService;
    @Mock
    private BidTenderDocumentSnapshotRepository snapshotRepository;
    @Mock
    private ProjectAccessScopeService projectAccessScopeService;
    @Mock
    private OpenAiScoreAnalyzer scoreAnalyzer;
    @Mock
    private EstimatedScoreService estimatedScoreService;
    @Mock
    private ScoreItemPersistenceService itemPersistenceService;

    private ScoreParseAppService service;

    @BeforeEach
    void setUp() {
        service = new ScoreParseAppService(
                taskRepository, itemRepository, resultRepository,
                stateService, progressService, snapshotRepository,
                projectAccessScopeService, scoreAnalyzer, estimatedScoreService,
                itemPersistenceService);
        ReflectionTestUtils.setField(service, "self", service);
    }

    @Test
    void reparse_delegatesPersistenceWithCandidates() {
        mockHappyPathParseFlow();

        service.executeParseAsync(TASK_ID);

        // FR-021 覆盖语义委托给 ScoreItemPersistenceService（其测试类覆盖删除细节）
        ArgumentCaptor<List<ScoreCandidate>> candidates = capturedPersistedCandidates();
        assertThat(candidates.getValue()).hasSize(2);
        verify(stateService).markCompleted(TASK_ID);
    }

    @Test
    void firstParse_alsoDelegatesPersistence() {
        mockHappyPathParseFlow();

        service.executeParseAsync(TASK_ID);

        verify(itemPersistenceService).persistItems(
                eq(PROJECT_ID), eq(5L), anyList());
        verify(stateService).markCompleted(TASK_ID);
    }

    private void mockHappyPathParseFlow() {
        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(
                ScoreParseTask.builder()
                        .id(5L).taskId(TASK_ID).projectId(PROJECT_ID)
                        .taskType("PARSE").status("PENDING").build()));
        when(snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(PROJECT_ID))
                .thenReturn(Optional.of(BidTenderDocumentSnapshot.builder()
                        .id(9L).projectId(PROJECT_ID)
                        .fileName("tender.pdf").fileUrl("doc-insight://t/1")
                        .extractedText("评分办法：...").build()));
        when(scoreAnalyzer.recallCandidates(anyString(), isNull(), any()))
                .thenReturn(List.of(
                        candidate("A1", "资质", "具备 CMMI 5 级认证证书", "60"),
                        candidate("A2", "业绩", "近三年类似项目业绩不少于 3 个", "40")));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<ScoreCandidate>> capturedPersistedCandidates() {
        ArgumentCaptor<List<ScoreCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(5L), captor.capture());
        return captor;
    }

    private ScoreCandidate candidate(String code, String dim, String detail, String weight) {
        return new ScoreCandidate(code, dim, detail, new BigDecimal(weight),
                "OBJECTIVE", null, detail, "P47", "SEMANTIC");
    }

    @Test
    void getItems_metaCarriesFileNamesAndTimes() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID)).thenReturn(List.of());
        when(snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(PROJECT_ID))
                .thenReturn(Optional.of(BidTenderDocumentSnapshot.builder()
                        .fileName("招标文件-v3.pdf").build()));
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                eq(PROJECT_ID), eq("PARSE"), anyList()))
                .thenReturn(List.of(ScoreParseTask.builder()
                        .id(1L).taskType("PARSE").status("COMPLETED")
                        .completedAt(LocalDateTime.of(2026, 8, 16, 10, 0)).build()));
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                eq(PROJECT_ID), eq("SCORING"), anyList()))
                .thenReturn(List.of(ScoreParseTask.builder()
                        .id(2L).taskType("SCORING").status("COMPLETED")
                        .fileName("投标文件-终稿.docx")
                        .completedAt(LocalDateTime.of(2026, 8, 16, 11, 0)).build()));

        ScoreParseItemsDTO dto = service.getItems(PROJECT_ID);

        assertThat(dto.meta().sourceFileName()).isEqualTo("招标文件-v3.pdf");
        assertThat(dto.meta().parseTime()).isEqualTo(LocalDateTime.of(2026, 8, 16, 10, 0));
        assertThat(dto.meta().bidFileName()).isEqualTo("投标文件-终稿.docx");
        assertThat(dto.meta().scoreTime()).isEqualTo(LocalDateTime.of(2026, 8, 16, 11, 0));
    }

    @Test
    void getItems_metaNullSafeWhenNoSnapshotOrTasks() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID)).thenReturn(List.of());
        when(snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(PROJECT_ID))
                .thenReturn(Optional.empty());
        when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(anyLong(), anyString(), anyList()))
                .thenReturn(List.of());

        ScoreParseItemsDTO dto = service.getItems(PROJECT_ID);

        assertThat(dto.meta().sourceFileName()).isNull();
        assertThat(dto.meta().parseTime()).isNull();
        assertThat(dto.meta().bidFileName()).isNull();
        assertThat(dto.meta().scoreTime()).isNull();
    }
}
