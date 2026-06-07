// Input: documenteditor service and request DTOs
// Output: Document Editor REST API endpoints, including draft tree import
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.documenteditor.controller;

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
import com.xiyu.bid.documenteditor.service.DocumentEditorService;
import com.xiyu.bid.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文档编辑器控制器
 * 提供文档结构和章节管理的REST API
 */
@RestController
@RequestMapping("/api/documents/{projectId}/editor")
@RequiredArgsConstructor
@Slf4j
public class DocumentEditorController {

    private final DocumentEditorService documentEditorService;

    /**
     * 获取文档结构
     *
     * @param projectId 项目ID
     * @return 文档结构
     */
    @GetMapping("/structure")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<DocumentStructureDTO>> getStructure(
            @PathVariable Long projectId) {
        DocumentStructureDTO structure = documentEditorService.getStructure(projectId);
        return ResponseEntity.ok(ApiResponse.success(structure));
    }

    /**
     * 创建文档结构
     *
     * @param projectId 项目ID
     * @param request 创建请求
     * @return 创建的文档结构
     */
    @PostMapping("/structure")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<DocumentStructureDTO>> createStructure(
            @PathVariable Long projectId,
            @Valid @RequestBody StructureCreateRequest request) {
        // 确保请求中的项目ID与路径参数一致
        request.setProjectId(projectId);
        DocumentStructureDTO structure = documentEditorService.createStructure(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(structure));
    }

    /**
     * 获取章节树
     *
     * @param projectId 项目ID
     * @return 章节树
     */
    @GetMapping("/sections/tree")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<DocumentSectionDTO>>> getSectionTree(
            @PathVariable Long projectId) {
        List<DocumentSectionDTO> tree = documentEditorService.getSectionTree(projectId);
        return ResponseEntity.ok(ApiResponse.success(tree));
    }

    /**
     * 批量导入草稿树
     *
     * @param projectId 项目ID
     * @param request 导入请求
     * @return 导入结果
     */
    @PostMapping("/draft-tree")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<DraftTreeUpsertResultDTO>> upsertDraftTree(
            @PathVariable Long projectId,
            @Valid @RequestBody DraftTreeUpsertRequest request) {
        DraftTreeUpsertResultDTO result = documentEditorService.upsertDraftTree(projectId, request);
        return ResponseEntity.ok(ApiResponse.success("Draft tree imported successfully", result));
    }

    /**
     * 添加章节
     *
     * @param projectId 项目ID
     * @param request 创建请求
     * @return 创建的章节
     */
    @PostMapping("/sections")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<DocumentSectionDTO>> addSection(
            @PathVariable Long projectId,
            @Valid @RequestBody SectionCreateRequest request) {
        DocumentSectionDTO section = documentEditorService.addSection(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(section));
    }

    /**
     * 更新章节
     *
     * @param projectId 项目ID
     * @param id 章节ID
     * @param request 更新请求
     * @return 更新后的章节
     */
    @PutMapping("/sections/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<DocumentSectionDTO>> updateSection(
            @PathVariable Long projectId,
            @PathVariable Long id,
            @RequestBody SectionUpdateRequest request) {
        DocumentSectionDTO section = documentEditorService.updateSection(projectId, id, request);
        return ResponseEntity.ok(ApiResponse.success(section));
    }

    @PostMapping("/assignments")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<DocumentSectionDTO>> assignSection(
            @PathVariable Long projectId,
            @Valid @RequestBody SectionAssignmentRequest request) {
        DocumentSectionDTO section = documentEditorService.assignSection(projectId, request);
        return ResponseEntity.ok(ApiResponse.success("Section assigned successfully", section));
    }

    @PostMapping("/locks")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<DocumentSectionDTO>> updateLock(
            @PathVariable Long projectId,
            @Valid @RequestBody SectionLockRequest request) {
        DocumentSectionDTO section = documentEditorService.updateLock(projectId, request);
        return ResponseEntity.ok(ApiResponse.success("Section lock updated successfully", section));
    }

    @PostMapping("/reminders")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<DocumentReminderDTO>> createReminder(
            @PathVariable Long projectId,
            @Valid @RequestBody SectionReminderRequest request) {
        DocumentReminderDTO reminder = documentEditorService.createReminder(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reminder sent successfully", reminder));
    }

    /**
     * 删除章节
     *
     * @param projectId 项目ID
     * @param id 章节ID
     * @return 成功响应
     */
    @DeleteMapping("/sections/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteSection(
            @PathVariable Long projectId,
            @PathVariable Long id) {
        documentEditorService.deleteSection(projectId, id);
        return ResponseEntity.ok(ApiResponse.success("Section deleted successfully", null));
    }

    /**
     * 重新排序章节
     *
     * @param projectId 项目ID
     * @param request 排序请求
     * @return 成功响应
     */
    @PutMapping("/sections/reorder")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Void>> reorderSections(
            @PathVariable Long projectId,
            @Valid @RequestBody SectionReorderRequest request) {
        documentEditorService.reorderSections(projectId, request);
        return ResponseEntity.ok(ApiResponse.success("Sections reordered successfully", null));
    }
}
