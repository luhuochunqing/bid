package com.xiyu.bid.documentexport.service;

import com.xiyu.bid.documenteditor.entity.DocumentStructure;
import com.xiyu.bid.documenteditor.repository.DocumentSectionRepository;
import com.xiyu.bid.documenteditor.repository.DocumentStructureRepository;
import com.xiyu.bid.documentexport.dto.DocumentExportCreateRequest;
import com.xiyu.bid.documentexport.dto.DocumentExportDTO;
import com.xiyu.bid.documentexport.entity.DocumentExport;
import com.xiyu.bid.documentexport.entity.DocumentExportFile;
import com.xiyu.bid.documentexport.repository.DocumentExportFileRepository;
import com.xiyu.bid.documentexport.repository.DocumentExportRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentExportCommandService {

    private final ProjectRepository projectRepository;
    private final DocumentStructureRepository structureRepository;
    private final DocumentSectionRepository sectionRepository;
    private final DocumentExportRepository exportRepository;
    private final DocumentExportFileRepository exportFileRepository;
    private final DocumentExportContentBuilder contentBuilder;
    private final DocumentExportAccessGuard accessGuard;

    public DocumentExportDTO createExport(Long projectId, DocumentExportCreateRequest request) {
        accessGuard.requireProjectAccess(projectId);
        Project project = getProject(projectId);
        DocumentStructure structure = getStructure(projectId);

        String normalizedFormat = normalizeFormat(request.getFormat());
        String content = contentBuilder.build(project, structure, sectionRepository.findByStructureId(structure.getId()));
        String fileName = buildFileName(project.getName(), normalizedFormat);
        String contentType = resolveContentType(normalizedFormat);

        DocumentExport savedExport = exportRepository.save(DocumentExport.builder()
                .projectId(projectId)
                .structureId(structure.getId())
                .projectName(project.getName())
                .format(normalizedFormat)
                .fileName(fileName)
                .contentType(contentType)
                .fileSize((long) content.length())
                .exportedBy(request.getExportedBy())
                .exportedByName(request.getExportedByName().trim())
                .build());

        exportFileRepository.save(DocumentExportFile.builder()
                .exportId(savedExport.getId())
                .content(content)
                .build());

        return DocumentExportDTO.builder()
                .id(savedExport.getId())
                .projectId(savedExport.getProjectId())
                .structureId(savedExport.getStructureId())
                .projectName(savedExport.getProjectName())
                .format(savedExport.getFormat())
                .fileName(savedExport.getFileName())
                .contentType(savedExport.getContentType())
                .fileSize(savedExport.getFileSize())
                .exportedBy(savedExport.getExportedBy())
                .exportedByName(savedExport.getExportedByName())
                .exportedAt(savedExport.getExportedAt())
                .content(content)
                .build();
    }

    public Project getProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));
    }

    public DocumentStructure getStructure(Long projectId) {
        return structureRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Document structure not found for project: " + projectId));
    }

    private String normalizeFormat(String format) {
        String normalized = Optional.ofNullable(format).orElse("json").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("json", "txt").contains(normalized)) {
            throw new BusinessException("不支持的导出格式: " + format);
        }
        return normalized;
    }

    private String buildFileName(String projectName, String format) {
        String safeProjectName = Optional.ofNullable(projectName).orElse("document").replaceAll("\\s+", "_");
        return safeProjectName + "_document_export." + format;
    }

    private String resolveContentType(String format) {
        return "txt".equals(format) ? "text/plain;charset=utf-8" : "application/json;charset=utf-8";
    }
}
