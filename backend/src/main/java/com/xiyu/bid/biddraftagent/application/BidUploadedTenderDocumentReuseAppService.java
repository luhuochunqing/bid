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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BidUploadedTenderDocumentReuseAppService {

    private static final String REUSE_UPLOADED_SUCCESS_MESSAGE = "已复用项目已上传的招标文件";
    private final TenderDocumentReuseSourceSelector reuseSourceSelector = new TenderDocumentReuseSourceSelector();

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
    private final TransactionTemplate transactionTemplate;

    public Optional<BidTenderDocumentParseDTO> parseLatestUploadedTenderDocument(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        Project project = requireProject(projectId);
        Tender tender = requireTender(project.getTenderId());
        Optional<BidTenderDocumentParseDTO> uploadedDocumentResult = reuseSourceSelector
                .firstReusableProjectDocument(
                        projectDocumentRepository.findByProjectIdOrderByCreatedAtDesc(projectId),
                        tender.getId()
                )
                .flatMap(document -> parseUploadedDocument(projectId, tender, document));
        return uploadedDocumentResult.isPresent()
                ? uploadedDocumentResult
                : parseTenderSourceDocument(projectId, tender);
    }

    private Optional<BidTenderDocumentParseDTO> parseUploadedDocument(
            Long projectId,
            Tender tender,
            ProjectDocument document
    ) {
        return documentStorage.loadByFileUrl(document.getFileUrl())
                .map(loadedDocument -> parseLoadedDocument(projectId, tender, document, loadedDocument, false));
    }

    private Optional<BidTenderDocumentParseDTO> parseTenderSourceDocument(Long projectId, Tender tender) {
        return reuseSourceSelector.tenderSourceDocument(projectId, tender)
                .flatMap(document -> documentStorage.loadByFileUrl(document.getFileUrl())
                        .map(loadedDocument -> parseLoadedDocument(projectId, tender, document, loadedDocument, true)));
    }

    private BidTenderDocumentParseDTO parseLoadedDocument(
            Long projectId,
            Tender tender,
            ProjectDocument document,
            LoadedTenderDocument loadedDocument,
            boolean saveProjectDocument
    ) {
        String fileName = firstNonBlank(document.getName(), "招标文件");
        String contentType = reuseSourceSelector.contentTypeOf(document);
        ExtractedTenderDocument extracted = textExtractor.extract(fileName, contentType, loadedDocument.content());
        TenderRequirementProfile profile = documentAnalyzer.analyze(new TenderDocumentAnalysisInput(
                projectId,
                tender.getId(),
                fileName,
                extracted.text(),
                extracted.structuredMetadata()
        ));
        PersistedReuseDocument persisted = persistParsedSnapshot(
                projectId,
                tender,
                document,
                loadedDocument.storedDocument(),
                extracted,
                profile,
                saveProjectDocument
        );
        return buildResult(persisted.document(), tender.getId(), persisted.snapshot(), extracted.textLength(), profile);
    }

    private PersistedReuseDocument persistParsedSnapshot(
            Long projectId,
            Tender tender,
            ProjectDocument document,
            StoredTenderDocument storedDocument,
            ExtractedTenderDocument extracted,
            TenderRequirementProfile profile,
            boolean saveProjectDocument
    ) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            ProjectDocument persistedDocument = saveProjectDocument
                    ? projectDocumentRepository.save(document)
                    : document;
            String profileJson = jsonCodec.toJson(profile);
            BidTenderDocumentSnapshot snapshot = documentSnapshotRepository.save(entityFactory.buildSnapshot(
                    projectId,
                    tender.getId(),
                    persistedDocument,
                    storedDocument,
                    extracted,
                    profile,
                    profileJson
            ));
            List<BidRequirementItem> items = entityFactory.buildItems(projectId, tender.getId(), persistedDocument.getId(), profile);
            requirementItemRepository.saveAll(items);
            snapshotUpdater.apply(tender, profile);
            tenderRepository.save(tender);
            return new PersistedReuseDocument(persistedDocument, snapshot);
        }));
    }

    private BidTenderDocumentParseDTO buildResult(
            ProjectDocument document,
            Long tenderId,
            BidTenderDocumentSnapshot snapshot,
            int textLength,
            TenderRequirementProfile profile
    ) {
        return BidTenderDocumentParseDTO.builder()
                .document(BidTenderDocumentDTO.builder()
                        .id(document.getId())
                        .projectId(document.getProjectId())
                        .tenderId(tenderId)
                        .name(firstNonBlank(document.getName(), snapshot.getFileName()))
                        .fileType(firstNonBlank(document.getFileType(), TenderDocumentFileType.toProjectDocumentType(
                                snapshot.getFileName(),
                                snapshot.getContentType()
                        )))
                        .size(document.getSize())
                        .fileUrl(firstNonBlank(document.getFileUrl(), snapshot.getFileUrl()))
                        .snapshotId(snapshot.getId())
                        .extractedTextLength(textLength)
                        .build())
                .requirementProfile(profile)
                .message(REUSE_UPLOADED_SUCCESS_MESSAGE)
                .build();
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", String.valueOf(projectId)));
    }

    private Tender requireTender(Long tenderId) {
        return tenderRepository.findById(tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", String.valueOf(tenderId)));
    }

    private String firstNonBlank(String preferred, String fallback) {
        String normalized = trimToNull(preferred);
        return normalized != null ? normalized : fallback;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record PersistedReuseDocument(ProjectDocument document, BidTenderDocumentSnapshot snapshot) {
    }
}
