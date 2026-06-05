// Input: versionhistory repositories, DTOs, and support services
// Output: Version History business service operations
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.versionhistory.service;

import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.audit.service.AuditLogService;
import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.versionhistory.dto.DocumentVersionDTO;
import com.xiyu.bid.versionhistory.dto.VersionCreateRequest;
import com.xiyu.bid.versionhistory.dto.VersionDiffDTO;
import com.xiyu.bid.versionhistory.entity.DocumentVersion;
import com.xiyu.bid.versionhistory.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VersionHistoryService {

    private final DocumentVersionRepository repository;
    private final IAuditLogService auditLogService;
    private final VersionHistoryAccessGuard accessGuard;

    @Transactional(rollbackFor = Exception.class)
    public synchronized DocumentVersionDTO createVersion(VersionCreateRequest request) {
        if (request.getProjectId() == null) {
            throw new IllegalArgumentException("Project ID is required");
        }
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be empty");
        }
        if (request.getCreatedBy() == null) {
            throw new IllegalArgumentException("Created by is required");
        }
        accessGuard.requireProjectAccess(request.getProjectId());

        Integer nextVersionNumber = repository.getNextVersionNumber(request.getProjectId());

        repository.findCurrentVersionByProjectId(request.getProjectId())
                .ifPresent(currentVersion -> {
                    currentVersion.setIsCurrent(false);
                    repository.save(currentVersion);
                });

        DocumentVersion version = DocumentVersion.builder()
                .projectId(request.getProjectId())
                .documentId(request.getDocumentId())
                .versionNumber(nextVersionNumber)
                .content(request.getContent())
                .filePath(request.getFilePath())
                .changeSummary(request.getChangeSummary() != null
                        ? request.getChangeSummary()
                        : "Version " + nextVersionNumber)
                .createdBy(request.getCreatedBy())
                .createdAt(LocalDateTime.now())
                .isCurrent(true)
                .build();

        DocumentVersion savedVersion = repository.save(version);

        logAudit("CREATE", "DocumentVersion", savedVersion.getId().toString(),
                "Created version " + nextVersionNumber + " for project " + request.getProjectId(),
                null, savedVersion.toString(), request.getCreatedBy());

        return DocumentVersionDTO.fromEntity(savedVersion);
    }

    public List<DocumentVersionDTO> getVersionsByProject(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID is required");
        }
        accessGuard.requireProjectAccess(projectId);

        return repository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(DocumentVersionDTO::fromEntity)
                .toList();
    }

    public DocumentVersionDTO getVersion(Long versionId) {
        if (versionId == null) {
            throw new IllegalArgumentException("Version ID is required");
        }

        DocumentVersion version = repository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found with id: " + versionId));
        accessGuard.requireProjectAccess(version.getProjectId());

        return DocumentVersionDTO.fromEntity(version);
    }

    public DocumentVersionDTO getVersion(Long projectId, Long versionId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID is required");
        }
        if (versionId == null) {
            throw new IllegalArgumentException("Version ID is required");
        }
        accessGuard.requireProjectAccess(projectId);

        DocumentVersion version = repository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found with id: " + versionId));
        requireVersionBelongsToProject(version, projectId);
        return DocumentVersionDTO.fromEntity(version);
    }

    public DocumentVersionDTO getLatestVersion(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID is required");
        }
        accessGuard.requireProjectAccess(projectId);

        DocumentVersion version = repository.findCurrentVersionByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("No versions found for project: " + projectId));

        return DocumentVersionDTO.fromEntity(version);
    }

    public VersionDiffDTO compareVersions(Long projectId, Long versionId1, Long versionId2) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID is required");
        }
        if (versionId1 == null || versionId2 == null) {
            throw new IllegalArgumentException("Both version IDs are required");
        }
        accessGuard.requireProjectAccess(projectId);

        DocumentVersion version1 = repository.findById(versionId1)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found with id: " + versionId1));
        DocumentVersion version2 = repository.findById(versionId2)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found with id: " + versionId2));
        requireVersionBelongsToProject(version1, projectId);
        requireVersionBelongsToProject(version2, projectId);

        List<String> differences = VersionDiffCalculator.compute(version1.getContent(), version2.getContent());

        return VersionDiffDTO.builder()
                .version1Id(version1.getId())
                .version2Id(version2.getId())
                .version1Number(version1.getVersionNumber())
                .version2Number(version2.getVersionNumber())
                .content1(version1.getContent())
                .content2(version2.getContent())
                .differences(differences)
                .build();
    }

    @Transactional
    public DocumentVersionDTO rollbackToVersion(Long projectId, Long versionId, Long userId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID is required");
        }
        if (versionId == null) {
            throw new IllegalArgumentException("Version ID is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        accessGuard.requireProjectAccess(projectId);

        DocumentVersion targetVersion = repository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found with id: " + versionId));

        requireVersionBelongsToProject(targetVersion, projectId);

        DocumentVersion currentVersion = repository.findCurrentVersionByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("No current version found for project: " + projectId));

        currentVersion.setIsCurrent(false);
        repository.save(currentVersion);

        Integer nextVersionNumber = repository.getNextVersionNumber(projectId);
        DocumentVersion newVersion = DocumentVersion.builder()
                .projectId(projectId)
                .documentId(targetVersion.getDocumentId())
                .versionNumber(nextVersionNumber)
                .content(targetVersion.getContent())
                .filePath(targetVersion.getFilePath())
                .changeSummary("Rollback to version " + targetVersion.getVersionNumber())
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .isCurrent(true)
                .build();

        DocumentVersion savedVersion = repository.save(newVersion);

        logAudit("ROLLBACK", "DocumentVersion", savedVersion.getId().toString(),
                "Rolled back to version " + targetVersion.getVersionNumber(),
                currentVersion.toString(), savedVersion.toString(), userId);

        return DocumentVersionDTO.fromEntity(savedVersion);
    }

    @Transactional
    public void markAsCurrent(Long versionId) {
        if (versionId == null) {
            throw new IllegalArgumentException("Version ID is required");
        }

        DocumentVersion targetVersion = repository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found with id: " + versionId));
        accessGuard.requireProjectAccess(targetVersion.getProjectId());

        repository.findCurrentVersionByProjectId(targetVersion.getProjectId())
                .ifPresent(current -> {
                    current.setIsCurrent(false);
                    repository.save(current);
                });

        targetVersion.setIsCurrent(true);
        repository.save(targetVersion);
    }

    private void requireVersionBelongsToProject(DocumentVersion version, Long projectId) {
        if (!version.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Version does not belong to the specified project");
        }
    }

    private void logAudit(String action, String entityType, String entityId,
                         String description, String oldValue, String newValue, Long userId) {
        try {
            AuditLogService.AuditLogEntry entry = AuditLogService.AuditLogEntry.builder()
                    .userId(userId != null ? userId.toString() : null)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .description(description)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .success(true)
                    .build();

            auditLogService.log(entry);
        } catch (RuntimeException e) {
            log.error("Failed to log audit entry", e);
        }
    }
}
