package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 评估表附件管理服务（V150 新增）。
 * <p>使用 project_documents 表存储评估表相关附件，通过 linked_entity_type=EVALUATION_GAP 关联标讯。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderEvaluationDocumentService {

    public static final String ENTITY_TYPE_EVALUATION_GAP = "EVALUATION_GAP";

    private final ProjectDocumentRepository projectDocumentRepository;
    private final TenderRepository tenderRepository;

    @Transactional
    public ProjectDocument uploadDocument(Long tenderId, MultipartFile file, String uploaderName) {
        if (tenderId == null) {
            throw new IllegalArgumentException("Tender ID cannot be null");
        }
        Tender tender = tenderRepository.findById(tenderId)
                .orElseThrow(() -> new IllegalArgumentException("Tender not found with id: " + tenderId));
        checkTenderStatusEditable(tender);

        ProjectDocument doc = ProjectDocument.builder()
                .projectId(tenderId)
                .name(safeFileName(file.getOriginalFilename()))
                .size(formatSize(file.getSize()))
                .fileType(file.getContentType())
                .documentCategory(ENTITY_TYPE_EVALUATION_GAP)
                .linkedEntityType(ENTITY_TYPE_EVALUATION_GAP)
                .linkedEntityId(tenderId)
                .uploaderName(uploaderName)
                .build();
        return projectDocumentRepository.save(doc);
    }

    @Transactional(readOnly = true)
    public List<ProjectDocument> getDocuments(Long tenderId) {
        return projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc(
                ENTITY_TYPE_EVALUATION_GAP, tenderId);
    }

    @Transactional
    public void deleteDocument(Long documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("Document ID cannot be null");
        }
        ProjectDocument doc = projectDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with id: " + documentId));
        Long tenderId = doc.getLinkedEntityId();
        if (tenderId != null) {
            Tender tender = tenderRepository.findById(tenderId)
                    .orElseThrow(() -> new IllegalArgumentException("Tender not found with id: " + tenderId));
            checkTenderStatusEditable(tender);
        }
        projectDocumentRepository.delete(doc);
    }

    private void checkTenderStatusEditable(Tender tender) {
        Tender.Status status = tender.getStatus();
        if (status == Tender.Status.TRACKING ||
            status == Tender.Status.EVALUATED ||
            status == Tender.Status.BIDDING ||
            status == Tender.Status.ABANDONED) {
            throw new AccessDeniedException("标讯处于跟踪中、已评估、投标中或已放弃状态，评估表对所有角色均为只读");
        }
    }

    private String safeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "unnamed-file";
        }
        return originalFilename.trim().replaceAll("[\\\\/:*?\"<>|]+", "_");
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
