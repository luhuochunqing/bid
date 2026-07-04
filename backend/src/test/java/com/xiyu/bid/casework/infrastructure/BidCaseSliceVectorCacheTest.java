package com.xiyu.bid.casework.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BidCaseSliceVectorCache - 内存向量缓存")
class BidCaseSliceVectorCacheTest {

    @Test
    @DisplayName("加载后应按 ID 查询到向量及元数据")
    void loadAndFindById_shouldReturnVectorWithMetadata() {
        BidCaseSliceVectorCache cache = new BidCaseSliceVectorCache();
        BidCaseSlice slice = createSlice(1L, new float[]{1.0f, 0.0f, 0.0f});

        cache.load(List.of(slice));

        assertThat(cache.findById(1L)).isPresent();
        BidCaseSliceVectorCache.BidCaseSliceVector cached = cache.findById(1L).get();
        assertThat(cached.id()).isEqualTo(1L);
        assertThat(cached.projectDir()).isEqualTo("project-a");
        assertThat(cached.docxFile()).isEqualTo("file.docx");
        assertThat(cached.docxLabel()).isEqualTo("技术");
        assertThat(cached.title()).isEqualTo("title");
        assertThat(cached.textPreview()).isEqualTo("preview");
        assertThat(cached.textLength()).isEqualTo(7);
        assertThat(cached.paraCount()).isEqualTo(1);
        assertThat(cached.vector()).containsExactly(1.0f, 0.0f, 0.0f);
    }

    @Test
    @DisplayName("无 embedding 的切片应被忽略")
    void load_sliceWithoutEmbedding_shouldBeIgnored() {
        BidCaseSliceVectorCache cache = new BidCaseSliceVectorCache();
        BidCaseSlice slice = createSliceWithoutEmbedding(2L);

        cache.load(List.of(slice));

        assertThat(cache.findById(2L)).isEmpty();
        assertThat(cache.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("findAll 应返回所有已加载向量")
    void load_multipleSlices_findAllShouldReturnAll() {
        BidCaseSliceVectorCache cache = new BidCaseSliceVectorCache();
        BidCaseSlice s1 = createSlice(1L, new float[]{1.0f, 0.0f});
        BidCaseSlice s2 = createSlice(2L, new float[]{0.0f, 1.0f});

        cache.load(List.of(s1, s2));

        assertThat(cache.findAll()).hasSize(2);
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("clear 后缓存应为空")
    void clear_shouldRemoveAll() {
        BidCaseSliceVectorCache cache = new BidCaseSliceVectorCache();
        cache.load(List.of(createSlice(1L, new float[]{1.0f})));

        cache.clear();

        assertThat(cache.isEmpty()).isTrue();
        assertThat(cache.findAll()).isEmpty();
    }

    @Test
    @DisplayName("重复加载同一 ID 应覆盖旧数据")
    void loadTwice_shouldOverwrite() {
        BidCaseSliceVectorCache cache = new BidCaseSliceVectorCache();
        cache.load(List.of(createSlice(1L, new float[]{1.0f})));
        cache.load(List.of(createSlice(1L, new float[]{2.0f})));

        assertThat(cache.findById(1L)).isPresent();
        assertThat(cache.findById(1L).get().vector()).containsExactly(2.0f);
    }

    @Test
    @DisplayName("null 列表应被安全忽略")
    void load_nullList_shouldDoNothing() {
        BidCaseSliceVectorCache cache = new BidCaseSliceVectorCache();

        cache.load(null);

        assertThat(cache.isEmpty()).isTrue();
    }

    private BidCaseSlice createSlice(Long id, float[] vector) {
        BidCaseSlice slice = new BidCaseSlice();
        slice.setId(id);
        slice.setProjectDir("project-a");
        slice.setProjectIdx(1);
        slice.setDocxFile("file.docx");
        slice.setDocxLabel("技术");
        slice.setSectionIdx(1);
        slice.setLevel(1);
        slice.setTitle("title");
        slice.setTextPreview("preview");
        slice.setTextLength(7);
        slice.setParaCount(1);
        slice.setEmbedding(EmbeddingVectorCodec.encode(vector));
        slice.setEmbeddingModel("test-model");
        slice.setEmbeddingDim(vector.length);
        slice.setEmbeddingAt(LocalDateTime.now());
        slice.setCreatedAt(LocalDateTime.now());
        return slice;
    }

    private BidCaseSlice createSliceWithoutEmbedding(Long id) {
        BidCaseSlice slice = new BidCaseSlice();
        slice.setId(id);
        slice.setProjectDir("project-a");
        slice.setProjectIdx(1);
        slice.setDocxFile("file.docx");
        slice.setDocxLabel("技术");
        slice.setSectionIdx(1);
        slice.setLevel(1);
        slice.setTitle("title");
        slice.setTextPreview("preview");
        slice.setTextLength(7);
        slice.setParaCount(1);
        slice.setCreatedAt(LocalDateTime.now());
        return slice;
    }
}
