// Input: 文档正文 + 由 sidecar 提供的结构化元数据（章节偏移）
// Output: 按章节切分 + 溢出子切分 + 边界 overlap 的分块文本列表；解析失败时降级到 TenderDocumentTextChunker
// Pos: biddraftagent/infrastructure/openai — LLM 分析前的分块适配器
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.docinsight.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class StructuralDocumentChunker {

    private final ObjectMapper objectMapper;
    private static final int MAX_CHARS = 4000;
    private static final int OVERLAP_CHARS = 200;

    public StructuralDocumentChunker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<DocumentChunk> chunk(String text, String structuredMetadata) {
        if (structuredMetadata == null || structuredMetadata.isBlank()) {
            return TextChunker.split(text, MAX_CHARS, OVERLAP_CHARS).stream()
                    .map(t -> new DocumentChunk(t, List.of()))
                    .toList();
        }

        try {
            JsonNode root = objectMapper.readTree(structuredMetadata);
            JsonNode sectionsNode = root.path("sections");
            
            if (sectionsNode.isMissingNode() || !sectionsNode.isArray() || sectionsNode.isEmpty()) {
                return TextChunker.split(text, MAX_CHARS, OVERLAP_CHARS).stream()
                        .map(t -> new DocumentChunk(t, List.of()))
                        .toList();
            }

            List<DocumentChunk> chunks = new ArrayList<>();

            for (JsonNode section : sectionsNode) {
                int start = section.path("charStart").asInt(0);
                int end = section.path("charEnd").asInt(text.length());
                List<String> path = new ArrayList<>();
                section.path("path").forEach(p -> path.add(p.asText()));
                
                start = Math.max(0, Math.min(start, text.length()));
                end = Math.max(0, Math.min(end, text.length()));
                
                if (start >= end) continue;

                String sectionText = text.substring(start, end);

                if (sectionText.length() > MAX_CHARS) {
                    List<String> subChunks = TextChunker.split(sectionText, MAX_CHARS, OVERLAP_CHARS);
                    for (String subChunk : subChunks) {
                        chunks.add(new DocumentChunk(subChunk, path));
                    }
                } else {
                    chunks.add(new DocumentChunk(sectionText, path));
                }
            }

            return chunks;

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse structured metadata for chunking, falling back to legacy chunker: {}", e.getMessage());
            return TextChunker.split(text, MAX_CHARS, OVERLAP_CHARS).stream()
                    .map(t -> new DocumentChunk(t, List.of()))
                    .toList();
        }
    }
}
