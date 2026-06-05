// Input: DocumentStorage, DocumentTextExtractor, StructuralDocumentChunker, List<DocumentAnalyzer>, ProjectAccessScopeService
// Output: DocumentAnalysisResult — 协调存储、提取、分块、分析各层；仅项目绑定 profile 执行访问范围校验
// Pos: docinsight/application — 文档智能分析主服务实现
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.docinsight.application;

import com.xiyu.bid.docinsight.application.exception.DocumentNotFoundException;
import com.xiyu.bid.docinsight.application.exception.UnsupportedProfileException;
import com.xiyu.bid.docinsight.domain.DocInsightProfiles;
import com.xiyu.bid.docinsight.domain.DocumentChunk;
import com.xiyu.bid.docinsight.domain.StructuralDocumentChunker;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentIntelligenceServiceImpl implements DocumentIntelligenceService {

    private final DocumentStorage storage;
    private final DocumentTextExtractor extractor;
    private final StructuralDocumentChunker chunker;
    private final List<DocumentAnalyzer> analyzers;
    private final ProjectAccessScopeService projectAccessScopeService;

    @Override
    public DocumentAnalysisResult process(String profileCode, String entityId, MultipartFile file) {
        checkProjectAccess(profileCode, entityId);
        try {
            byte[] content = file.getBytes();
            StoredDocument stored = storage.store(profileCode, entityId, file.getOriginalFilename(), file.getContentType(), content);
            return parse(profileCode, stored, file.getOriginalFilename(), file.getContentType(), content);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file", e);
        }
    }

    @Override
    public DocumentAnalysisResult processExisting(String profileCode, String entityId, String storagePath, String fileName, String contentType) {
        checkProjectAccess(profileCode, entityId);

        byte[] content = storage.load(storagePath)
                .orElseThrow(() -> new DocumentNotFoundException(storagePath));

        // lookup re-reads to derive real fileUrl and contentHash; known double-read,
        // pending metadata persistence layer introduction.
        StoredDocument stored = storage.lookup(storagePath)
                .orElseThrow(() -> new DocumentNotFoundException(storagePath));

        return parse(profileCode, stored, fileName, contentType, content);
    }

    private DocumentAnalysisResult parse(String profileCode, StoredDocument stored, String fileName, String contentType, byte[] content) {
        ExtractedDocument extracted = extractor.extract(fileName, contentType, content);
        List<DocumentChunk> chunks = chunker.chunk(extracted.text(), extracted.structuredMetadata());

        DocumentAnalyzer analyzer = analyzers.stream()
                .filter(a -> a.supports(profileCode))
                .findFirst()
                .orElseThrow(() -> new UnsupportedProfileException(profileCode));

        DocumentAnalysisInput input = new DocumentAnalysisInput(
                stored.fileUrl(),
                fileName,
                extracted.text(),
                extracted.structuredMetadata(),
                chunks,
                profileCode,
                Map.of()
        );

        return analyzer.analyze(input);
    }

    /**
     * 当 profileCode 属于项目绑定类型时，校验当前用户是否有权访问对应项目。
     * 抛出 AccessDeniedException 时由 GlobalExceptionHandler 映射到 HTTP 403。
     */
    private void checkProjectAccess(String profileCode, String entityId) {
        if (!DocInsightProfiles.requiresProjectAccess(profileCode)) {
            return;
        }
        long projectId;
        try {
            projectId = Long.parseLong(entityId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("项目型配置的 entityId 必须为有效的项目 ID 数字: " + entityId);
        }
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
    }
}
