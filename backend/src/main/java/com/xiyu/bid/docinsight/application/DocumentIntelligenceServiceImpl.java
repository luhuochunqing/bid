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

    @Override
    public StoredDocument storeOnly(String profileCode, String entityId, MultipartFile file) {
        checkProjectAccess(profileCode, entityId);
        try {
            byte[] content = file.getBytes();
            return storage.store(profileCode, entityId, file.getOriginalFilename(), file.getContentType(), content);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file", e);
        }
    }

    private DocumentAnalysisResult parse(String profileCode, StoredDocument stored, String fileName, String contentType, byte[] content) {
        long extractStart = System.currentTimeMillis();
        ExtractedDocument extracted = extractor.extract(fileName, contentType, content);
        long extractDuration = System.currentTimeMillis() - extractStart;

        String extractedText = extracted.text();
        String extractorSource = extracted.extras() != null
                ? String.valueOf(extracted.extras().getOrDefault("fallbackReason", "sidecar"))
                : "sidecar";
        int usableChars = countUsableChars(extractedText);

        log.info("Document extraction finished: fileName={}, extractor={}, duration={}ms, textLength={}, usableChars={}",
                fileName, extractorSource, extractDuration,
                extractedText == null ? 0 : extractedText.length(), usableChars);

        DocumentAnalyzer analyzer = analyzers.stream()
                .filter(a -> a.supports(profileCode))
                .findFirst()
                .orElseThrow(() -> new UnsupportedProfileException(profileCode));

        // 扫描件检测：提取文本过短则返回警告，不进行 AI 分析
        if (extractedText == null || extractedText.trim().length() < 100) {
            log.warn("Document {} appears to be a scanned PDF (extracted text length: {}), skipping AI analysis",
                    fileName, extractedText == null ? 0 : extractedText.trim().length());
            return new DocumentAnalysisResult(
                    stored.fileUrl(),
                    Map.of(),
                    List.of(),
                    extractedText,
                    List.of("SCANNED_DOCUMENT: 该文件可能是扫描件（图片型PDF），无法提取文本内容。" +
                            "请尝试上传可编辑的PDF/Word文件，或直接使用粘贴识别功能。")
            );
        }

        // 额外检测：如果可用字符比例过低，可能是二进制文件被错误提取
        if (extractedText.length() > 0 && usableChars < extractedText.length() * 0.3) {
            log.warn("Document {} has low usable character ratio ({}%), possible binary extraction failure",
                    fileName, (100 * usableChars) / extractedText.length());
        }

        List<DocumentChunk> chunks = chunker.chunk(extractedText, extracted.structuredMetadata());

        DocumentAnalysisInput input = new DocumentAnalysisInput(
                stored.fileUrl(),
                fileName,
                extractedText,
                extracted.structuredMetadata(),
                chunks,
                profileCode,
                Map.of()
        );

        return analyzer.analyze(input);
    }

    /**
     * Count "usable" characters: ASCII printable + CJK unified ideographs + common CJK punctuation.
     * This avoids false positives for Chinese documents where ASCII-only ratio would be low.
     */
    private int countUsableChars(String text) {
        if (text == null) return 0;
        int count = 0;
        for (char c : text.toCharArray()) {
            if (c >= 32 && c <= 126) {
                count++;
            } else if (c >= '\u4e00' && c <= '\u9fff') {
                // CJK Unified Ideographs
                count++;
            } else if (c >= '\u3000' && c <= '\u303f') {
                // CJK Symbols and Punctuation
                count++;
            } else if (c >= '\uff00' && c <= '\uffef') {
                // Halfwidth and Fullwidth Forms (includes fullwidth punctuation)
                count++;
            }
        }
        return count;
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
