// Input: documentexport repositories, DTOs, and support services
// Output: Document Export business service operations
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.documentexport.service;

import com.xiyu.bid.documentexport.dto.DocumentArchiveRecordCreateRequest;
import com.xiyu.bid.documentexport.dto.DocumentArchiveRecordDTO;
import com.xiyu.bid.documentexport.dto.DocumentCaseSnapshotDTO;
import com.xiyu.bid.documentexport.dto.DocumentExportCreateRequest;
import com.xiyu.bid.documentexport.dto.DocumentExportDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentExportService {

    private final DocumentExportQueryService queryService;
    private final DocumentExportCommandService exportCommandService;
    private final DocumentArchiveCommandService archiveCommandService;

    @Transactional(readOnly = true)
    public List<DocumentExportDTO> getExports(Long projectId) {
        log.info("DocumentExport getExports: projectId={}", projectId);
        return queryService.getExports(projectId);
    }

    @Transactional
    public DocumentExportDTO createExport(Long projectId, DocumentExportCreateRequest request) {
        log.info("DocumentExport createExport: projectId={}, format={}, exportedBy={}",
                projectId, request.getFormat(), request.getExportedBy());
        try {
            DocumentExportDTO result = exportCommandService.createExport(projectId, request);
            log.info("DocumentExport createExport success: projectId={}, exportId={}", projectId, result.getId());
            return result;
        } catch (RuntimeException ex) {
            log.error("DocumentExport createExport failed: projectId={}", projectId, ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentArchiveRecordDTO> getArchiveRecords(Long projectId) {
        log.info("DocumentExport getArchiveRecords: projectId={}", projectId);
        return queryService.getArchiveRecords(projectId);
    }

    @Transactional
    public DocumentArchiveRecordDTO createArchiveRecord(Long projectId, DocumentArchiveRecordCreateRequest request) {
        log.info("DocumentExport createArchiveRecord: projectId={}, archivedBy={}, reason={}",
                projectId, request.getArchivedBy(), request.getArchiveReason());
        try {
            DocumentArchiveRecordDTO result = archiveCommandService.createArchiveRecord(projectId, request);
            log.info("DocumentExport createArchiveRecord success: projectId={}, archiveId={}", projectId, result.getId());
            return result;
        } catch (RuntimeException ex) {
            log.error("DocumentExport createArchiveRecord failed: projectId={}", projectId, ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public DocumentCaseSnapshotDTO getCaseSnapshot(Long projectId) {
        log.info("DocumentExport getCaseSnapshot: projectId={}", projectId);
        return queryService.getCaseSnapshot(projectId);
    }
}
