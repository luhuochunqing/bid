package com.xiyu.bid.documentexport.service;

import com.xiyu.bid.documentexport.dto.DocumentArchiveRecordCreateRequest;
import com.xiyu.bid.documentexport.dto.DocumentArchiveRecordDTO;
import com.xiyu.bid.documentexport.dto.DocumentCaseSnapshotDTO;
import com.xiyu.bid.documentexport.dto.DocumentExportCreateRequest;
import com.xiyu.bid.documentexport.dto.DocumentExportDTO;
import com.xiyu.bid.documentexport.entity.DocumentArchiveRecord;
import com.xiyu.bid.documentexport.entity.DocumentExport;
import com.xiyu.bid.documentexport.entity.DocumentExportFile;
import com.xiyu.bid.documentexport.repository.DocumentArchiveRecordRepository;
import com.xiyu.bid.documentexport.repository.DocumentExportFileRepository;
import com.xiyu.bid.documentexport.repository.DocumentExportRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.historyproject.application.HistoricalProjectSnapshotCaptureCommand;
import com.xiyu.bid.historyproject.application.HistoricalProjectSnapshotAppService;
import com.xiyu.bid.historyproject.dto.HistoricalProjectSnapshotDTO;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.core.ProjectStatusPolicy;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentArchiveCommandService {

    private final DocumentExportCommandService exportCommandService;
    private final DocumentArchiveRecordRepository archiveRecordRepository;
    private final DocumentExportRepository exportRepository;
    private final DocumentExportFileRepository exportFileRepository;
    private final HistoricalProjectSnapshotAppService snapshotAppService;
    private final DocumentExportAccessGuard accessGuard;
    private final ProjectInitiationDetailsRepository initiationDetailsRepository;

    public DocumentArchiveRecordDTO createArchiveRecord(Long projectId, DocumentArchiveRecordCreateRequest request) {
        accessGuard.requireProjectAccess(projectId);
        Project project = exportCommandService.getProject(projectId);
        DocumentExportDTO latestExport = exportCommandService.createExport(projectId, buildAutoExportRequest(request));

        DocumentArchiveRecord record = archiveRecordRepository.save(DocumentArchiveRecord.builder()
                .projectId(projectId)
                .structureId(latestExport.getStructureId())
                .archivedBy(request.getArchivedBy())
                .archivedByName(request.getArchivedByName().trim())
                .archiveReason(request.getArchiveReason().trim())
                .exportId(latestExport.getId())
                .build());

        project.setStage(ProjectStage.CLOSED.name());
        String bidResult = initiationDetailsRepository.findByProjectId(projectId)
                .map(d -> d.getBidResultStatus())
                .orElse(null);
        project.setStatus(ProjectStatusPolicy.compute(ProjectStage.CLOSED, bidResult, true));

        DocumentExport export = exportRepository.findById(latestExport.getId()).orElseThrow();
        DocumentExportFile exportFile = exportFileRepository.findByExportId(latestExport.getId()).orElseThrow();
        HistoricalProjectSnapshotDTO capturedSnapshot = snapshotAppService.capture(new HistoricalProjectSnapshotCaptureCommand(
                project.getId(),
                record.getId(),
                export.getId(),
                project.getName(),
                project.getSourceCustomer(),
                project.getSourceModule(),
                project.getSourceReasoningSummary(),
                exportFile.getContent()
        ));
        DocumentCaseSnapshotDTO snapshot = toCaseSnapshotDto(capturedSnapshot);

        return DocumentArchiveRecordDTO.builder()
                .id(record.getId())
                .projectId(record.getProjectId())
                .structureId(record.getStructureId())
                .archivedBy(record.getArchivedBy())
                .archivedByName(record.getArchivedByName())
                .archiveReason(record.getArchiveReason())
                .exportId(record.getExportId())
                .exportFileName(export.getFileName())
                .projectName(project.getName())
                .caseSnapshot(snapshot)
                .archivedAt(record.getArchivedAt())
                .build();
    }

    private DocumentExportCreateRequest buildAutoExportRequest(DocumentArchiveRecordCreateRequest request) {
        DocumentExportCreateRequest exportRequest = new DocumentExportCreateRequest();
        exportRequest.setFormat("json");
        exportRequest.setExportedBy(request.getArchivedBy());
        exportRequest.setExportedByName(request.getArchivedByName());
        return exportRequest;
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
