package com.xiyu.bid.casework.application;
import com.xiyu.bid.casework.dto.ProjectArchiveQuery;
import com.xiyu.bid.casework.dto.ProjectArchiveResponse;
import com.xiyu.bid.casework.dto.ProjectArchiveStatsResponse;
import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ArchiveLog;
import com.xiyu.bid.casework.infrastructure.ArchiveLogRepository;
import com.xiyu.bid.casework.infrastructure.KnowledgeCaseRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.casework.infrastructure.ProjectArchiveRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectArchiveWorkflowService {
    private final ProjectArchiveRepository archiveRepository;
    private final ArchiveFileRepository fileRepository;
    private final ArchiveLogRepository logRepository;
    private final ProjectRepository projectRepository;
    private final KnowledgeCaseRepository knowledgeCaseRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final ProjectArchiveResponseMapper responseMapper;
    public Page<ProjectArchiveResponse> queryProjectArchives(ProjectArchiveQuery query, Pageable pageable) {
        List<Long> allowedProjectIds = projectAccessScopeService.getAllowedProjectIdsForCurrentUser();
        boolean isAdmin = projectAccessScopeService.currentUserHasAdminAccess();
        if (!isAdmin && allowedProjectIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Specification<ProjectArchive> spec = buildSpecification(query, allowedProjectIds, isAdmin);
        Page<ProjectArchive> archives = archiveRepository.findAll(spec, pageable);
        List<ProjectArchiveResponse> content = archives.getContent().stream()
                .map(responseMapper::toResponse)
                .toList();

        return new PageImpl<>(content, pageable, archives.getTotalElements());
    }

    public ProjectArchiveStatsResponse getStats() {
        List<Long> allowedProjectIds = projectAccessScopeService.getAllowedProjectIdsForCurrentUser();
        boolean isAdmin = projectAccessScopeService.currentUserHasAdminAccess();

        long totalArchives = 0;
        long closedProjects = 0;
        long caseCount = knowledgeCaseRepository.count();
        long reuseCount = knowledgeCaseRepository.findAll().stream()
                .mapToLong(c -> c.getReuseCount() != null ? c.getReuseCount() : 0)
                .sum();

        List<ProjectArchive> accessibleArchives;
        if (isAdmin) {
            totalArchives = archiveRepository.count();
            closedProjects = archiveRepository.countByArchiveStatus("ARCHIVED");
            accessibleArchives = archiveRepository.findAll();
        } else if (!allowedProjectIds.isEmpty()) {
            totalArchives = archiveRepository.countByProjectIdIn(allowedProjectIds);
            closedProjects = archiveRepository.countByProjectIdInAndArchiveStatus(allowedProjectIds, "ARCHIVED");
            accessibleArchives = archiveRepository.findAllByProjectIdIn(allowedProjectIds);
        } else {
            accessibleArchives = List.of();
        }

        List<String> projectManagers = responseMapper.collectProjectManagers(accessibleArchives);
        List<String> bidManagers = responseMapper.collectBidManagers(accessibleArchives);

        return new ProjectArchiveStatsResponse(totalArchives, closedProjects, caseCount, reuseCount, projectManagers, bidManagers);
    }

    public List<ProjectArchive> getRawArchives(ProjectArchiveQuery query) {
        List<Long> allowedProjectIds = projectAccessScopeService.getAllowedProjectIdsForCurrentUser();
        boolean isAdmin = projectAccessScopeService.currentUserHasAdminAccess();

        if (!isAdmin && allowedProjectIds.isEmpty()) {
            return Collections.emptyList();
        }

        Specification<ProjectArchive> spec = buildSpecification(query, allowedProjectIds, isAdmin);
        return archiveRepository.findAll(spec);
    }


    @Transactional
    public ProjectArchive createArchive(Long projectId, String projectName, String status) {
        return archiveRepository.findByProjectId(projectId)
                .orElseGet(() -> {
                    ProjectArchive newArchive = new ProjectArchive();
                    newArchive.setProjectId(projectId);
                    newArchive.setProjectName(projectName);
                    newArchive.setArchiveStatus(status != null ? status : "ACTIVE");
                    return archiveRepository.save(newArchive);
                });
    }

    /**
     * 即时归档：项目文档上传时调用，将文件按分类归入项目档案（蓝图 4.1.1.1 要求）。
     */
    @Transactional
    public void attachFileToArchive(Long projectId, String fileName, String category, String physicalPath, Long fileSize, Long uploaderId, String uploaderName) {
        if (projectId == null) return;
        String projName = projectRepository.findById(projectId)
                .map(Project::getName)
                .orElse("项目-" + projectId);
        ProjectArchive archive = createArchive(projectId, projName, "ACTIVE");
        ArchiveFile af = new ArchiveFile();
        af.setArchiveId(archive.getId());
        af.setFileName(fileName != null ? fileName : "未命名文件");
        af.setDocumentCategory(category != null ? category : "OTHER");
        af.setFilePath(physicalPath != null ? physicalPath : "");
        af.setFileSize(fileSize != null ? fileSize : 0L);
        af.setUploadUserId(uploaderId != null ? uploaderId : 0L);
        af.setUploadUserName(uploaderName != null ? uploaderName : "系统");
        fileRepository.save(af);
    }

    @Transactional
    public void recordLog(Long archiveId, Long operatorId, String operatorName, String actionType, String content) {
        ArchiveLog log = new ArchiveLog();
        log.setArchiveId(archiveId);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setActionType(actionType);
        log.setActionContent(content);
        logRepository.save(log);
    }

    public ProjectArchive findArchiveById(Long id) {
        return archiveRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("档案不存在: " + id));
    }

    public void assertCurrentUserCanAccessProject(Long projectId) {
        projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
    }

    private Specification<ProjectArchive> buildSpecification(ProjectArchiveQuery query, List<Long> allowedProjectIds, boolean isAdmin) {
        return (root, criteriaQuery, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (!isAdmin) {
                predicates.add(root.get("projectId").in(allowedProjectIds));
            }

            if (query.getArchiveId() != null) {
                predicates.add(cb.equal(root.get("id"), query.getArchiveId()));
            }

            if (query.getProjectName() != null && !query.getProjectName().trim().isEmpty()) {
                predicates.add(cb.like(root.get("projectName"), "%" + query.getProjectName().trim() + "%"));
            }

            if (query.getProjectStatus() != null && !query.getProjectStatus().isEmpty()) {
                predicates.add(root.get("archiveStatus").in(query.getProjectStatus()));
            }

            if (query.getDocumentCategories() != null && !query.getDocumentCategories().isEmpty()) {
                Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
                Root<ArchiveFile> fileRoot = subquery.from(ArchiveFile.class);
                subquery.select(fileRoot.get("archiveId"))
                        .where(fileRoot.get("documentCategory").in(query.getDocumentCategories()));
                predicates.add(root.get("id").in(subquery));
            }

            if (query.getUploadTimeStart() != null || query.getUploadTimeEnd() != null) {
                Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
                Root<ArchiveFile> fileRoot = subquery.from(ArchiveFile.class);
                subquery.select(fileRoot.get("archiveId"));

                List<jakarta.persistence.criteria.Predicate> filePredicates = new ArrayList<>();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                if (query.getUploadTimeStart() != null) {
                    LocalDateTime start = LocalDate.parse(query.getUploadTimeStart(), formatter).atStartOfDay();
                    filePredicates.add(cb.greaterThanOrEqualTo(fileRoot.get("createdAt"), start));
                }
                if (query.getUploadTimeEnd() != null) {
                    LocalDateTime end = LocalDate.parse(query.getUploadTimeEnd(), formatter).atTime(23, 59, 59);
                    filePredicates.add(cb.lessThanOrEqualTo(fileRoot.get("createdAt"), end));
                }
                subquery.where(cb.and(filePredicates.toArray(new jakarta.persistence.criteria.Predicate[0])));
                predicates.add(root.get("id").in(subquery));
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            if (query.getCloseTimeStart() != null) {
                LocalDateTime start = LocalDate.parse(query.getCloseTimeStart(), formatter).atStartOfDay();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (query.getCloseTimeEnd() != null) {
                LocalDateTime end = LocalDate.parse(query.getCloseTimeEnd(), formatter).atTime(23, 59, 59);
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), end));
            }

            if (query.getProjectType() != null && !query.getProjectType().isEmpty()) {
                Subquery<Long> sub = criteriaQuery.subquery(Long.class);
                Root<Project> pRoot = sub.from(Project.class);
                Root<Tender> tRoot = sub.from(Tender.class);
                sub.select(pRoot.get("id"))
                   .where(cb.and(
                       cb.equal(pRoot.get("tenderId"), tRoot.get("id")),
                       tRoot.get("projectType").in(query.getProjectType())
                   ));
                predicates.add(root.get("projectId").in(sub));
            }

            if (query.getProjectManager() != null && !query.getProjectManager().trim().isEmpty()) {
                Subquery<Long> sub = criteriaQuery.subquery(Long.class);
                Root<Project> pRoot = sub.from(Project.class);
                Root<Tender> tRoot = sub.from(Tender.class);
                sub.select(pRoot.get("id"))
                   .where(cb.and(
                       cb.equal(pRoot.get("tenderId"), tRoot.get("id")),
                       cb.like(tRoot.get("projectManagerName"), "%" + query.getProjectManager().trim() + "%")
                   ));
                predicates.add(root.get("projectId").in(sub));
            }

            if (query.getBidManager() != null && !query.getBidManager().trim().isEmpty()) {
                Subquery<Long> sub = criteriaQuery.subquery(Long.class);
                Root<Project> pRoot = sub.from(Project.class);
                Root<Tender> tRoot = sub.from(Tender.class);
                sub.select(pRoot.get("id"))
                   .where(cb.and(
                       cb.equal(pRoot.get("tenderId"), tRoot.get("id")),
                       cb.like(tRoot.get("biddingPersonName"), "%" + query.getBidManager().trim() + "%")
                   ));
                predicates.add(root.get("projectId").in(sub));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
