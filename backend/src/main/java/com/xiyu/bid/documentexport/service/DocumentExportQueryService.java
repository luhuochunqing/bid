package com.xiyu.bid.documentexport.service;

import com.xiyu.bid.documentexport.dto.DocumentArchiveRecordDTO;
import com.xiyu.bid.documentexport.dto.DocumentCaseSnapshotDTO;
import com.xiyu.bid.documentexport.dto.DocumentExportDTO;
import com.xiyu.bid.documentexport.entity.DocumentExport;
import com.xiyu.bid.documentexport.entity.DocumentExportFile;
import com.xiyu.bid.documentexport.repository.DocumentArchiveRecordRepository;
import com.xiyu.bid.documentexport.repository.DocumentExportFileRepository;
import com.xiyu.bid.documentexport.repository.DocumentExportRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.historyproject.application.HistoricalProjectSnapshotAppService;
import com.xiyu.bid.historyproject.dto.HistoricalProjectSnapshotDTO;
import com.xiyu.bid.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentExportQueryService {

    private final DocumentExportRepository exportRepository;
    private final DocumentExportFileRepository exportFileRepository;
    private final DocumentArchiveRecordRepository archiveRecordRepository;
    private final ProjectRepository projectRepository;
    private final HistoricalProjectSnapshotAppService snapshotAppService;
    private final DocumentExportAccessGuard accessGuard;

    public List<DocumentExportDTO> getExports(Long projectId) {
        accessGuard.requireProjectAccess(projectId);
        return exportRepository.findByProjectIdOrderByExportedAtDesc(projectId).stream()
                .map(this::toExportDTO)
                .toList();
    }

    public List<DocumentArchiveRecordDTO> getArchiveRecords(Long projectId) {
        accessGuard.requireProjectAccess(projectId);
        return archiveRecordRepository.findByProjectIdOrderByArchivedAtDesc(projectId).stream()
                .map(record -> {
                    DocumentExport export = record.getExportId() == null
                            ? null
                            : exportRepository.findById(record.getExportId()).orElse(null);
                    Project project = projectRepository.findById(record.getProjectId()).orElse(null);
                    DocumentCaseSnapshotDTO snapshot = null;
                    try {
                        snapshot = toCaseSnapshotDto(snapshotAppService.getLatestSnapshot(record.getProjectId()));
                    } catch (ResourceNotFoundException ignored) {
                    }
                    return DocumentArchiveRecordDTO.builder()
                            .id(record.getId())
                            .projectId(record.getProjectId())
                            .structureId(record.getStructureId())
                            .archivedBy(record.getArchivedBy())
                            .archivedByName(record.getArchivedByName())
                            .archiveReason(record.getArchiveReason())
                            .exportId(record.getExportId())
                            .exportFileName(export != null ? export.getFileName() : null)
                            .projectName(project != null ? project.getName() : null)
                            .caseSnapshot(snapshot)
                            .archivedAt(record.getArchivedAt())
                            .build();
                })
                .toList();
    }

    public DocumentCaseSnapshotDTO getCaseSnapshot(Long projectId) {
        accessGuard.requireProjectAccess(projectId);
        return toCaseSnapshotDto(snapshotAppService.getLatestSnapshot(projectId));
    }

    private DocumentExportDTO toExportDTO(DocumentExport export) {
        String content = exportFileRepository.findByExportId(export.getId())
                .map(DocumentExportFile::getContent)
                .orElse("");
        return DocumentExportDTO.builder()
                .id(export.getId())
                .projectId(export.getProjectId())
                .structureId(export.getStructureId())
                .projectName(export.getProjectName())
                .format(export.getFormat())
                .fileName(export.getFileName())
                .contentType(export.getContentType())
                .fileSize(export.getFileSize())
                .exportedBy(export.getExportedBy())
                .exportedByName(export.getExportedByName())
                .exportedAt(export.getExportedAt())
                .content(content)
                .build();
    }

    private DocumentCaseSnapshotDTO toCaseSnapshotDto(HistoricalProjectSnapshotDTO snapshot) {
        return DocumentCaseSnapshotDTO.builder()
                .projectId(snapshot.getProjectId())
                .archiveRecordId(snapshot.getArchiveRecordId())
                .exportId(snapshot.getExportId())
                .projectName(snapshot.getProjectName())
                .customerName(snapshot.getCustomerName())
                .productLine(snapshot.getProductLine())
                .archiveSummary(snapshot.getArchiveSummary())
                .documentSnapshotText(snapshot.getDocumentSnapshotText())
                .recommendedTags(snapshot.getRecommendedTags())
                .capturedAt(snapshot.getCapturedAt())
                .build();
    }
}
