// Input: documenteditor workflow services
// Output: Document Editor facade over split-first workflow services, including draft tree import
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.documenteditor.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.documenteditor.dto.DocumentReminderDTO;
import com.xiyu.bid.documenteditor.dto.DraftTreeUpsertRequest;
import com.xiyu.bid.documenteditor.dto.DraftTreeUpsertResultDTO;
import com.xiyu.bid.documenteditor.dto.DocumentSectionDTO;
import com.xiyu.bid.documenteditor.dto.DocumentStructureDTO;
import com.xiyu.bid.documenteditor.dto.SectionAssignmentRequest;
import com.xiyu.bid.documenteditor.dto.SectionCreateRequest;
import com.xiyu.bid.documenteditor.dto.SectionLockRequest;
import com.xiyu.bid.documenteditor.dto.SectionReminderRequest;
import com.xiyu.bid.documenteditor.dto.SectionReorderRequest;
import com.xiyu.bid.documenteditor.dto.SectionUpdateRequest;
import com.xiyu.bid.documenteditor.dto.StructureCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文档编辑器服务
 * 作为 facade 维持原有 public API，内部按职责委托到更小的工作流服务。
 */
@Service
@RequiredArgsConstructor
public class DocumentEditorService {

    private final DocumentStructureService structureService;
    private final DocumentSectionCommandService sectionCommandService;
    private final DocumentSectionCollaborationService sectionCollaborationService;
    private final DocumentSectionTreeService sectionTreeService;
    private final DocumentDraftTreeImportService draftTreeImportService;

    @Auditable(action = "CREATE", entityType = "DocumentStructure", description = "Create document structure")
    @Transactional
    public DocumentStructureDTO createStructure(StructureCreateRequest request) {
        return structureService.createStructure(request);
    }

    public DocumentStructureDTO getStructure(Long projectId) {
        return structureService.getStructure(projectId);
    }

    @Auditable(action = "CREATE", entityType = "DocumentSection", description = "Add document section")
    @Transactional
    public DocumentSectionDTO addSection(Long projectId, SectionCreateRequest request) {
        return sectionCommandService.addSection(projectId, request);
    }

    @Auditable(action = "UPDATE", entityType = "DocumentSection", description = "Update document section")
    @Transactional
    public DocumentSectionDTO updateSection(Long projectId, Long sectionId, SectionUpdateRequest request) {
        return sectionCommandService.updateSection(projectId, sectionId, request);
    }

    @Transactional
    public DocumentSectionDTO assignSection(Long projectId, SectionAssignmentRequest request) {
        return sectionCollaborationService.assignSection(projectId, request);
    }

    @Transactional
    public DocumentSectionDTO updateLock(Long projectId, SectionLockRequest request) {
        return sectionCollaborationService.updateLock(projectId, request);
    }

    @Transactional
    public DocumentReminderDTO createReminder(Long projectId, SectionReminderRequest request) {
        return sectionCollaborationService.createReminder(projectId, request);
    }

    @Auditable(action = "DELETE", entityType = "DocumentSection", description = "Delete document section")
    @Transactional
    public void deleteSection(Long projectId, Long sectionId) {
        sectionCommandService.deleteSection(projectId, sectionId);
    }

    @Transactional
    public void reorderSections(Long projectId, SectionReorderRequest request) {
        sectionCommandService.reorderSections(projectId, request);
    }

    public List<DocumentSectionDTO> getSectionTree(Long projectId) {
        return sectionTreeService.getSectionTree(projectId);
    }

    @Transactional
    public DraftTreeUpsertResultDTO upsertDraftTree(Long projectId, DraftTreeUpsertRequest request) {
        return draftTreeImportService.upsertDraftTree(projectId, request);
    }
}
