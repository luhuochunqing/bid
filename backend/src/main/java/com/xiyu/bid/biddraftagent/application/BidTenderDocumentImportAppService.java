package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.biddraftagent.dto.BidTenderDocumentDTO;
import com.xiyu.bid.biddraftagent.dto.BidTenderDocumentParseDTO;
import com.xiyu.bid.biddraftagent.entity.BidRequirementItem;
import com.xiyu.bid.biddraftagent.entity.BidTenderDocumentSnapshot;
import com.xiyu.bid.biddraftagent.repository.BidRequirementItemRepository;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BidTenderDocumentImportAppService {

    private static final long MAX_FILE_SIZE_BYTES = 30L * 1024L * 1024L;
    private static final String DOCUMENT_CATEGORY = "TENDER_FILE";
    private static final String LINKED_ENTITY_TYPE = "TENDER";
    private static final String PARSE_SUCCESS_MESSAGE = "招标文件已解析，已更新招标要求快照";
    private static final String REUSE_SUCCESS_MESSAGE = "已复用已解析的招标文件";

    private final ProjectAccessScopeService projectAccessScopeService;
    private final ProjectRepository projectRepository;
    private final TenderRepository tenderRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final BidRequirementItemRepository requirementItemRepository;
    private final BidTenderDocumentSnapshotRepository documentSnapshotRepository;
    private final TenderDocumentStorage documentStorage;
    private final TenderDocumentTextExtractor textExtractor;
    private final TenderDocumentAnalyzer documentAnalyzer;
    private final TenderRequirementSnapshotUpdater snapshotUpdater;
    private final TenderRequirementEntityFactory entityFactory;
    private final BidDraftAgentJsonCodec jsonCodec;
    private final BidAgentOperatorResolver operatorResolver;
    private final TransactionTemplate transactionTemplate;

    public BidTenderDocumentParseDTO parseTenderDocument(Long projectId, MultipartFile file) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        validateFile(file);

        Project project = requireProject(projectId);
        Tender tender = requireTender(project.getTenderId());
        byte[] content = fileBytes(file);
        String fileName = originalFileName(file);
        String contentType = file.getContentType();

        StoredTenderDocument storedDocument = documentStorage.store(projectId, fileName, contentType, content);
        ExtractedTenderDocument extracted = textExtractor.extract(fileName, contentType, content);
        TenderRequirementProfile profile = documentAnalyzer.analyze(new TenderDocumentAnalysisInput(
                projectId,
                tender.getId(),
                fileName,
                extracted.text(),
                extracted.structuredMetadata()
        ));

        PersistedTenderDocument persistedDocument = persistParsedTenderDocument(
                projectId,
                tender,
                fileName,
                contentType,
                file.getSize(),
                storedDocument,
                extracted,
                profile
        );
        return buildParseResult(persistedDocument, tender.getId(), extracted.textLength(), profile);
    }

    public Optional<BidTenderDocumentParseDTO> latestParsedTenderDocument(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return documentSnapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(projectId)
                .map(snapshot -> {
                    ProjectDocument document = projectDocumentRepository.findById(snapshot.getProjectDocumentId())
                            .orElse(null);
                    TenderRequirementProfile profile = jsonCodec.fromJson(
                            snapshot.getProfileJson(),
                            TenderRequirementProfile.class
                    );
                    return buildReuseResult(snapshot, document, profile);
                });
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传招标文件");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("招标文件不能超过 30MB");
        }
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", String.valueOf(projectId)));
    }

    private Tender requireTender(Long tenderId) {
        return tenderRepository.findById(tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", String.valueOf(tenderId)));
    }

    private byte[] fileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("读取招标文件失败", ex);
        }
    }

    private String originalFileName(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        return fileName == null || fileName.isBlank() ? "招标文件" : fileName.trim();
    }

    private PersistedTenderDocument persistParsedTenderDocument(
            Long projectId,
            Tender tender,
            String fileName,
            String contentType,
            long size,
            StoredTenderDocument storedDocument,
            ExtractedTenderDocument extracted,
            TenderRequirementProfile profile
    ) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            ProjectDocument projectDocument = saveProjectDocument(
                    projectId,
                    tender,
                    fileName,
                    contentType,
                    size,
                    storedDocument
            );
            BidTenderDocumentSnapshot snapshot = persistRequirementSnapshot(
                    projectId,
                    tender,
                    projectDocument,
                    storedDocument,
                    extracted,
                    profile
            );
            snapshotUpdater.apply(tender, profile);
            tenderRepository.save(tender);
            return new PersistedTenderDocument(projectDocument, snapshot);
        }));
    }

    private ProjectDocument saveProjectDocument(
            Long projectId,
            Tender tender,
            String fileName,
            String contentType,
            long size,
            StoredTenderDocument storedDocument
    ) {
        BidAgentOperator operator = operatorResolver.currentOperator();
        ProjectDocument document = ProjectDocument.builder()
                .projectId(projectId)
                .name(fileName)
                .size(formatSize(size))
                .fileType(TenderDocumentFileType.toProjectDocumentType(fileName, contentType))
                .documentCategory(DOCUMENT_CATEGORY)
                .linkedEntityType(LINKED_ENTITY_TYPE)
                .linkedEntityId(tender.getId())
                .fileUrl(storedDocument.fileUrl())
                .uploaderId(operator.userId())
                .uploaderName(operator.displayName())
                .build();
        return projectDocumentRepository.save(document);
    }

    private BidTenderDocumentSnapshot persistRequirementSnapshot(
            Long projectId,
            Tender tender,
            ProjectDocument document,
            StoredTenderDocument storedDocument,
            ExtractedTenderDocument extracted,
            TenderRequirementProfile profile
    ) {
        String profileJson = jsonCodec.toJson(profile);
        BidTenderDocumentSnapshot snapshot = documentSnapshotRepository.save(entityFactory.buildSnapshot(
                projectId,
                tender.getId(),
                document,
                storedDocument,
                extracted,
                profile,
                profileJson
        ));
        List<BidRequirementItem> items = entityFactory.buildItems(projectId, tender.getId(), document.getId(), profile);
        requirementItemRepository.saveAll(items);
        return snapshot;
    }

    private BidTenderDocumentParseDTO buildParseResult(
            PersistedTenderDocument persistedDocument,
            Long tenderId,
            int textLength,
            TenderRequirementProfile profile
    ) {
        return BidTenderDocumentParseDTO.builder()
                .document(BidTenderDocumentDTO.builder()
                        .id(persistedDocument.document().getId())
                        .projectId(persistedDocument.document().getProjectId())
                        .tenderId(tenderId)
                        .name(persistedDocument.document().getName())
                        .fileType(persistedDocument.document().getFileType())
                        .size(persistedDocument.document().getSize())
                        .fileUrl(persistedDocument.document().getFileUrl())
                        .snapshotId(persistedDocument.snapshot().getId())
                        .extractedTextLength(textLength)
                        .build())
                .requirementProfile(profile)
                .message(PARSE_SUCCESS_MESSAGE)
                .build();
    }

    private BidTenderDocumentParseDTO buildReuseResult(
            BidTenderDocumentSnapshot snapshot,
            ProjectDocument document,
            TenderRequirementProfile profile
    ) {
        return BidTenderDocumentParseDTO.builder()
                .document(BidTenderDocumentDTO.builder()
                        .id(snapshot.getProjectDocumentId())
                        .projectId(snapshot.getProjectId())
                        .tenderId(snapshot.getTenderId())
                        .name(firstNonBlank(document == null ? null : document.getName(), snapshot.getFileName()))
                        .fileType(firstNonBlank(
                                document == null ? null : document.getFileType(),
                                TenderDocumentFileType.toProjectDocumentType(snapshot.getFileName(), snapshot.getContentType())
                        ))
                        .size(document == null ? null : document.getSize())
                        .fileUrl(firstNonBlank(document == null ? null : document.getFileUrl(), snapshot.getFileUrl()))
                        .snapshotId(snapshot.getId())
                        .extractedTextLength(textLength(snapshot.getExtractedText()))
                        .build())
                .requirementProfile(profile)
                .message(REUSE_SUCCESS_MESSAGE)
                .build();
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private int textLength(String text) {
        return text == null ? 0 : text.length();
    }

    private String formatSize(long bytes) {
        long kb = Math.max(1L, Math.round(bytes / 1024.0));
        if (kb < 1024L) {
            return kb + "KB";
        }
        return Math.round(kb / 1024.0) + "MB";
    }

    private record PersistedTenderDocument(ProjectDocument document, BidTenderDocumentSnapshot snapshot) {
    }
}
