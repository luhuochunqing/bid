package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.bootstrap.BidCaseSliceVectorCacheInitializer;
import com.xiyu.bid.casework.infrastructure.BidCaseSlice;
import com.xiyu.bid.casework.infrastructure.BidCaseSliceRepository;
import com.xiyu.bid.casework.infrastructure.EmbeddingVectorCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BatchEmbeddingAppService - 切片批量向量化")
class BatchEmbeddingAppServiceTest {

    private BidCaseSliceRepository repository;
    private AiProvider aiProvider;
    private EmbeddingRateLimiter rateLimiter;
    private BidCaseSliceVectorCacheInitializer cacheInitializer;
    private BatchEmbeddingAppService service;

    @BeforeEach
    void setUp() {
        repository = mock(BidCaseSliceRepository.class);
        aiProvider = mock(AiProvider.class);
        rateLimiter = mock(EmbeddingRateLimiter.class);
        cacheInitializer = mock(BidCaseSliceVectorCacheInitializer.class);
        service = new BatchEmbeddingAppService(repository, aiProvider, rateLimiter, cacheInitializer);
    }

    @Test
    @DisplayName("成功处理所有切片并持久化 embedding")
    void embedAllUnprocessed_withSuccessfulCalls_shouldPersistEmbeddings() {
        BidCaseSlice s1 = slice(1L, "标题一", "正文一");
        BidCaseSlice s2 = slice(2L, "标题二", "正文二");
        when(repository.findUnembedded(any(Pageable.class)))
                .thenReturn(List.of(s1, s2))
                .thenReturn(List.of());
        when(repository.countUnembedded()).thenReturn(0L);
        when(aiProvider.embed(anyString()))
                .thenReturn(new float[]{0.1f, 0.2f})
                .thenReturn(new float[]{0.3f, 0.4f});

        BatchEmbeddingAppService.EmbeddingResult result = service.embedAllUnprocessed(100);

        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        assertThat(result.remaining()).isZero();

        verify(rateLimiter, times(2)).acquire();
        ArgumentCaptor<List<BidCaseSlice>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<BidCaseSlice> saved = captor.getValue();
        assertThat(saved).hasSize(2);

        BidCaseSlice first = saved.get(0);
        assertThat(first.getEmbeddingModel()).isEqualTo(BatchEmbeddingAppService.DEFAULT_EMBEDDING_MODEL);
        assertThat(first.getEmbeddingDim()).isEqualTo(2);
        assertThat(first.getEmbeddingAt()).isNotNull();
        assertThat(EmbeddingVectorCodec.decode(first.getEmbedding())).containsExactly(0.1f, 0.2f);

        BidCaseSlice second = saved.get(1);
        assertThat(EmbeddingVectorCodec.decode(second.getEmbedding())).containsExactly(0.3f, 0.4f);
        verify(cacheInitializer).refreshCacheIncremental(any());
    }

    @Test
    @DisplayName("重试 3 次仍失败应标记为 FAILED")
    void embedAllUnprocessed_withPersistentFailure_shouldMarkFailed() {
        BidCaseSlice slice = slice(3L, "失败标题", "失败正文");
        when(repository.findUnembedded(any(Pageable.class)))
                .thenReturn(List.of(slice))
                .thenReturn(List.of());
        when(repository.countUnembedded()).thenReturn(0L);
        when(aiProvider.embed(anyString())).thenThrow(new RuntimeException("provider down"));

        BatchEmbeddingAppService.EmbeddingResult result = service.embedAllUnprocessed(100);

        assertThat(result.processed()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.remaining()).isZero();

        verify(rateLimiter, times(3)).acquire();
        verify(aiProvider, times(3)).embed(anyString());

        ArgumentCaptor<List<BidCaseSlice>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        BidCaseSlice saved = captor.getValue().get(0);
        assertThat(saved.getEmbeddingModel()).isEqualTo(BatchEmbeddingAppService.FAILURE_MARKER);
        assertThat(saved.getEmbedding()).isNull();
    }

    @Test
    @DisplayName("第二次重试成功不应标记为失败")
    void embedAllUnprocessed_withRetrySuccess_shouldNotMarkFailed() {
        BidCaseSlice slice = slice(4L, "重试标题", "重试正文");
        when(repository.findUnembedded(any(Pageable.class)))
                .thenReturn(List.of(slice))
                .thenReturn(List.of());
        when(repository.countUnembedded()).thenReturn(0L);
        when(aiProvider.embed(anyString()))
                .thenThrow(new RuntimeException("timeout"))
                .thenReturn(new float[]{0.9f});

        BatchEmbeddingAppService.EmbeddingResult result = service.embedAllUnprocessed(100);

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(rateLimiter, times(2)).acquire();

        ArgumentCaptor<List<BidCaseSlice>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        BidCaseSlice saved = captor.getValue().get(0);
        assertThat(saved.getEmbeddingModel()).isEqualTo(BatchEmbeddingAppService.DEFAULT_EMBEDDING_MODEL);
        assertThat(EmbeddingVectorCodec.decode(saved.getEmbedding())).containsExactly(0.9f);
    }

    @Test
    @DisplayName("应使用传入的 batchSize 查询")
    void embedAllUnprocessed_shouldUseProvidedBatchSize() {
        BidCaseSlice slice = slice(5L, "标题", "正文");
        when(repository.findUnembedded(any(Pageable.class)))
                .thenReturn(List.of(slice))
                .thenReturn(List.of());
        when(repository.countUnembedded()).thenReturn(0L);
        when(aiProvider.embed(anyString())).thenReturn(new float[]{0.5f});

        service.embedAllUnprocessed(17);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository, times(2)).findUnembedded(pageableCaptor.capture());
        List<Pageable> pageables = pageableCaptor.getAllValues();
        assertThat(pageables.get(0).getPageSize()).isEqualTo(17);
        assertThat(pageables.get(1).getPageSize()).isEqualTo(17);
    }

    @Test
    @DisplayName("batchSize 超过上限时应被限制为 200")
    void embedAllUnprocessed_withOversizedBatch_shouldClampToMax() {
        BidCaseSlice slice = slice(6L, "标题", "正文");
        when(repository.findUnembedded(any(Pageable.class)))
                .thenReturn(List.of(slice))
                .thenReturn(List.of());
        when(repository.countUnembedded()).thenReturn(0L);
        when(aiProvider.embed(anyString())).thenReturn(new float[]{0.5f});

        service.embedAllUnprocessed(500);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository, times(2)).findUnembedded(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(BatchEmbeddingAppService.MAX_BATCH_SIZE);
    }

    @Test
    @DisplayName("构建的 embedding 文本应包含标题与正文预览")
    void embedAllUnprocessed_shouldBuildTextFromTitleAndPreview() {
        BidCaseSlice slice = slice(7L, "售后保障", "7×24 小时服务");
        when(repository.findUnembedded(any(Pageable.class)))
                .thenReturn(List.of(slice))
                .thenReturn(List.of());
        when(repository.countUnembedded()).thenReturn(0L);
        when(aiProvider.embed(anyString())).thenReturn(new float[]{0.1f});

        service.embedAllUnprocessed(100);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiProvider).embed(textCaptor.capture());
        String text = textCaptor.getValue();
        assertThat(text).contains("售后保障");
        assertThat(text).contains("7×24 小时服务");
    }

    private static BidCaseSlice slice(Long id, String title, String preview) {
        BidCaseSlice slice = new BidCaseSlice();
        slice.setId(id);
        slice.setTitle(title);
        slice.setTextPreview(preview);
        slice.setTextLength(preview.length());
        slice.setParaCount(1);
        return slice;
    }
}
