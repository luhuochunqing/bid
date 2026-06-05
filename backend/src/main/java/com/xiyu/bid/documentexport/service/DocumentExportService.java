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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentExportService {

    private final DocumentExportQueryService queryService;
    private final DocumentExportCommandService exportCommandService;
    private final DocumentArchiveCommandService archiveCommandService;

    @Transactional(readOnly = true)
    public List<DocumentExportDTO> getExports(Long projectId) {
        return queryService.getExports(projectId);
    }

    @Transactional
    public DocumentExportDTO createExport(Long projectId, DocumentExportCreateRequest request) {
        return exportCommandService.createExport(projectId, request);
    }

    @Transactional(readOnly = true)
    public List<DocumentArchiveRecordDTO> getArchiveRecords(Long projectId) {
        return queryService.getArchiveRecords(projectId);
    }

    @Transactional
    public DocumentArchiveRecordDTO createArchiveRecord(Long projectId, DocumentArchiveRecordCreateRequest request) {
        return archiveCommandService.createArchiveRecord(projectId, request);
    }

    @Transactional(readOnly = true)
    public DocumentCaseSnapshotDTO getCaseSnapshot(Long projectId) {
        return queryService.getCaseSnapshot(projectId);
    }
}
