// Input: projectId + MultipartFile（投标文件）
// Output: BidDocumentUploadDTO（documentId + fileUrl）
// Pos: scoreparse/application — 投标文件上传（spec 041 US4 / contracts/score-parse-api.md §4）
// 维护声明: 维护者按项目SOP；spec Edge Cases 格式与大小双重校验
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.application.BidAgentOperator;
import com.xiyu.bid.biddraftagent.application.BidAgentOperatorResolver;
import com.xiyu.bid.biddraftagent.application.StoredTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.scoreparse.dto.BidDocumentUploadDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * 投标文件上传服务（spec 041 US4 前置）。
 * <p>格式校验 PDF/docx、大小 ≤ 50MB（Edge Cases）；存储复用
 * {@link TenderDocumentStorage}，元数据记 project_document（documentCategory=BID_FILE）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BidDocumentUploadService {

    private static final String DOCUMENT_CATEGORY = "BID_FILE";
    private static final String LINKED_ENTITY_TYPE = "PROJECT";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx");

    private final ProjectAccessScopeService projectAccessScopeService;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final TenderDocumentStorage documentStorage;
    private final BidAgentOperatorResolver operatorResolver;

    @Transactional
    public BidDocumentUploadDTO uploadBidDocument(Long projectId, MultipartFile file) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        validateFile(file);

        String fileName = originalFileName(file);
        byte[] content = fileBytes(file);
        StoredTenderDocument stored = documentStorage.store(
                projectId, fileName, file.getContentType(), content);

        BidAgentOperator operator = operatorResolver.currentOperator();
        ProjectDocument document = projectDocumentRepository.save(ProjectDocument.builder()
                .projectId(projectId)
                .name(fileName)
                .size(formatSize(file.getSize()))
                .fileType(extensionOf(fileName))
                .documentCategory(DOCUMENT_CATEGORY)
                .linkedEntityType(LINKED_ENTITY_TYPE)
                .linkedEntityId(projectId)
                .fileUrl(stored.fileUrl())
                .uploaderId(operator.userId())
                .uploaderName(operator.displayName())
                .build());
        log.info("投标文件上传成功: projectId={}, documentId={}, file={}",
                projectId, document.getId(), fileName);
        return new BidDocumentUploadDTO(document.getId(), stored.fileUrl());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传投标文件");
        }
        String extension = extensionOf(originalFileName(file));
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("投标文件仅支持 PDF 或 docx 格式");
        }
    }

    private static String originalFileName(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        return fileName == null || fileName.isBlank() ? "投标文件" : fileName.trim();
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] fileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("读取投标文件失败", ex);
        }
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1fMB", bytes / 1024.0 / 1024.0);
        }
        return bytes / 1024 + "KB";
    }
}
