package com.xiyu.bid.casework.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.casework.infrastructure.BidCaseSlice;
import com.xiyu.bid.casework.infrastructure.BidCaseSliceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("CaseSliceJsonlImporter - JSONL 切片导入")
class CaseSliceJsonlImporterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("parseSlice: 完整 JSON 记录应映射为 BidCaseSlice")
    void parseSlice_withCompleteRecord_shouldMapAllFields() throws Exception {
        String json = """
                {
                  "project": "2026.01.05-中广核办公",
                  "project_idx": 3,
                  "docx_file": "技术文件/方案.docx",
                  "docx_label": "技术",
                  "section_idx": 42,
                  "level": 2,
                  "title": "售后服务保障措施",
                  "text_preview": "提供7×24小时服务热线",
                  "text_length": 15,
                  "para_count": 3
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        Optional<BidCaseSlice> result = CaseSliceJsonlImporter.parseSlice(node);

        assertThat(result).isPresent();
        BidCaseSlice slice = result.get();
        assertThat(slice.getProjectDir()).isEqualTo("2026.01.05-中广核办公");
        assertThat(slice.getProjectIdx()).isEqualTo(3);
        assertThat(slice.getDocxFile()).isEqualTo("技术文件/方案.docx");
        assertThat(slice.getDocxLabel()).isEqualTo("技术");
        assertThat(slice.getSectionIdx()).isEqualTo(42);
        assertThat(slice.getLevel()).isEqualTo(2);
        assertThat(slice.getTitle()).isEqualTo("售后服务保障措施");
        assertThat(slice.getTextPreview()).isEqualTo("提供7×24小时服务热线");
        assertThat(slice.getTextLength()).isEqualTo(15);
        assertThat(slice.getParaCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("parseSlice: 缺失 title 应返回空")
    void parseSlice_withoutTitle_shouldReturnEmpty() throws Exception {
        String json = "{\"project_idx\":1,\"text_preview\":\"preview\"}";

        Optional<BidCaseSlice> result = CaseSliceJsonlImporter.parseSlice(objectMapper.readTree(json));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("parseSlice: 缺失字段应使用默认值")
    void parseSlice_withMissingFields_shouldUseDefaults() throws Exception {
        String json = "{\"title\":\"仅标题\"}";

        Optional<BidCaseSlice> result = CaseSliceJsonlImporter.parseSlice(objectMapper.readTree(json));

        assertThat(result).isPresent();
        BidCaseSlice slice = result.get();
        assertThat(slice.getProjectDir()).isEmpty();
        assertThat(slice.getProjectIdx()).isZero();
        assertThat(slice.getDocxLabel()).isEmpty();
        assertThat(slice.getLevel()).isEqualTo(1);
    }

    @Test
    @DisplayName("importFromDirectory: 读取 JSONL 并持久化有效记录")
    void importFromDirectory_withValidAndInvalidLines_shouldPersistOnlyValid(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("project_1.jsonl");
        Files.writeString(file, """
                {"project":"p1","project_idx":1,"docx_file":"a.docx","docx_label":"技术","section_idx":1,"level":1,"title":"标题一","text_preview":"预览一","text_length":10,"para_count":2}
                not-json-at-all
                {"project":"p1","project_idx":1,"docx_file":"b.docx","docx_label":"商务","section_idx":2,"level":2,"title":"标题二","text_preview":"预览二","text_length":20,"para_count":1}
                """);

        BidCaseSliceRepository repository = mock(BidCaseSliceRepository.class);
        CaseSliceJsonlImporter importer = new CaseSliceJsonlImporter(repository, objectMapper);

        CaseSliceJsonlImporter.ImportResult result = importer.importFromDirectory(tempDir);

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.filesProcessed()).isEqualTo(1);

        ArgumentCaptor<List<BidCaseSlice>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<BidCaseSlice> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getTitle()).isEqualTo("标题一");
        assertThat(saved.get(1).getTitle()).isEqualTo("标题二");
    }

    @Test
    @DisplayName("importFromDirectory: 空目录应返回零并跳过持久化")
    void importFromDirectory_withEmptyDirectory_shouldReturnZero(@TempDir Path tempDir) {
        BidCaseSliceRepository repository = mock(BidCaseSliceRepository.class);
        CaseSliceJsonlImporter importer = new CaseSliceJsonlImporter(repository, objectMapper);

        CaseSliceJsonlImporter.ImportResult result = importer.importFromDirectory(tempDir);

        assertThat(result.imported()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(result.filesProcessed()).isZero();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("importFromDirectory: 仅匹配 project_*.jsonl 文件")
    void importFromDirectory_shouldIgnoreNonMatchingFiles(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("other.jsonl"),
                "{\"title\":\"ignored\"}\n");
        Files.writeString(tempDir.resolve("project_7.jsonl"),
                "{\"project\":\"p7\",\"project_idx\":7,\"docx_file\":\"x.docx\",\"docx_label\":\"其他\",\"section_idx\":1,\"level\":1,\"title\":\"保留\",\"text_preview\":\"pv\",\"text_length\":2,\"para_count\":1}\n");

        BidCaseSliceRepository repository = mock(BidCaseSliceRepository.class);
        CaseSliceJsonlImporter importer = new CaseSliceJsonlImporter(repository, objectMapper);

        CaseSliceJsonlImporter.ImportResult result = importer.importFromDirectory(tempDir);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.filesProcessed()).isEqualTo(1);
    }
}
