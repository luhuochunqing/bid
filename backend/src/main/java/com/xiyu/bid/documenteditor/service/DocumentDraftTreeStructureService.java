// Input: project id and draft tree request
// Output: existing or newly created document structure
// Pos: Service/业务支撑层
// 维护声明: 仅维护草稿树导入的结构获取/创建与项目权限入口；章节 upsert 留在导入服务。
package com.xiyu.bid.documenteditor.service;

import com.xiyu.bid.documenteditor.dto.DraftTreeUpsertRequest;
import com.xiyu.bid.documenteditor.entity.DocumentStructure;
import com.xiyu.bid.documenteditor.repository.DocumentStructureRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class DocumentDraftTreeStructureService {

    private static final String DEFAULT_STRUCTURE_NAME = "Draft Tree";

    private final DocumentStructureRepository structureRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    DocumentStructure resolveStructure(Long projectId, DraftTreeUpsertRequest request) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        return structureRepository.findByProjectId(projectId)
                .orElseGet(() -> structureRepository.save(DocumentStructure.builder()
                        .projectId(projectId)
                        .name(resolveStructureName(request))
                        .build()));
    }

    boolean exists(Long projectId) {
        return structureRepository.findByProjectId(projectId).isPresent();
    }

    private String resolveStructureName(DraftTreeUpsertRequest request) {
        String structureName = trimToNull(request.getStructureName());
        return structureName != null ? structureName : DEFAULT_STRUCTURE_NAME;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
