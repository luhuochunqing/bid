package com.xiyu.bid.casework.infrastructure;

import com.xiyu.bid.casework.dto.ProjectArchiveQuery;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.core.ProjectTypeAliasExpander;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ProjectArchive 查询条件构造器。
 *
 * <p>从 ProjectArchiveWorkflowService.buildSpecification 抽取，遵循 Split-First Rule
 * （ARCHITECTURE.md §Agent Contract 第 9 条）：Application Service 只做编排，查询构造下沉到 infrastructure。
 *
 * <p>所有筛选条件都通过子查询实现，避免主表 JOIN 导致分页失效。
 * 子查询使用 IN 而非 EXISTS，便于 MySQL 优化器自动改写为 semi-join。
 */
public final class ProjectArchiveSpecifications {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private ProjectArchiveSpecifications() {}

    /**
     * 构造档案列表筛选 Specification。
     *
     * @param query              用户筛选条件
     * @param allowedProjectIds  当前用户可访问的项目 ID（null 或空表示不限制）
     * @param isAdmin            是否管理员（管理员忽略 allowedProjectIds）
     */
    public static Specification<ProjectArchive> withFilters(ProjectArchiveQuery query,
                                                            List<Long> allowedProjectIds,
                                                            boolean isAdmin) {
        return (root, criteriaQuery, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (!isAdmin && allowedProjectIds != null && !allowedProjectIds.isEmpty()) {
                predicates.add(root.get("projectId").in(allowedProjectIds));
            }

            if (query.getArchiveId() != null) {
                predicates.add(cb.equal(root.get("id"), query.getArchiveId()));
            }

            if (query.getProjectIds() != null && !query.getProjectIds().isEmpty()) {
                predicates.add(root.get("projectId").in(query.getProjectIds()));
            }

            if (query.getProjectName() != null && !query.getProjectName().trim().isEmpty()) {
                predicates.add(cb.like(root.get("projectName"), "%" + query.getProjectName().trim() + "%"));
            }

            if (query.getProjectStatus() != null && !query.getProjectStatus().isEmpty()) {
                predicates.add(root.get("projectId").in(projectStatusSubquery(criteriaQuery, query.getProjectStatus())));
            }

            if (query.getDocumentCategories() != null && !query.getDocumentCategories().isEmpty()) {
                predicates.add(root.get("id").in(documentCategoriesSubquery(criteriaQuery, query.getDocumentCategories())));
            }

            if (query.getUploadTimeStart() != null || query.getUploadTimeEnd() != null) {
                predicates.add(root.get("id").in(uploadTimeSubquery(criteriaQuery, cb, query)));
            }

            if (query.getCloseTimeStart() != null || query.getCloseTimeEnd() != null) {
                predicates.add(root.get("projectId").in(closeTimeSubquery(criteriaQuery, cb, query)));
            }

            if (query.getProjectType() != null && !query.getProjectType().isEmpty()) {
                Set<String> expandedTypes = ProjectTypeAliasExpander.expand(query.getProjectType());
                predicates.add(root.get("projectId").in(projectTypeSubquery(criteriaQuery, cb, expandedTypes)));
            }

            if (query.getProjectManager() != null && !query.getProjectManager().trim().isEmpty()) {
                predicates.add(root.get("projectId").in(projectManagerSubquery(criteriaQuery, cb, query.getProjectManager().trim())));
            }

            if (query.getBidManager() != null && !query.getBidManager().trim().isEmpty()) {
                predicates.add(root.get("projectId").in(bidManagerSubquery(criteriaQuery, cb, query.getBidManager().trim())));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /** 项目状态筛选：通过 Project.status 匹配（Project.Status 枚举名）。 */
    private static Subquery<Long> projectStatusSubquery(jakarta.persistence.criteria.CriteriaQuery<?> cq,
                                                        List<String> statuses) {
        Subquery<Long> sub = cq.subquery(Long.class);
        Root<Project> pRoot = sub.from(Project.class);
        sub.select(pRoot.get("id"))
           .where(pRoot.get("status").in(statuses));
        return sub;
    }

    /** 文档分类筛选：返回包含至少一个指定分类文件的档案 ID。 */
    private static Subquery<Long> documentCategoriesSubquery(jakarta.persistence.criteria.CriteriaQuery<?> cq,
                                                             List<String> categories) {
        Subquery<Long> sub = cq.subquery(Long.class);
        Root<ArchiveFile> fileRoot = sub.from(ArchiveFile.class);
        sub.select(fileRoot.get("archiveId"))
           .where(fileRoot.get("documentCategory").in(categories));
        return sub;
    }

    /** 上传时间筛选：基于 ArchiveFile.createdAt。 */
    private static Subquery<Long> uploadTimeSubquery(jakarta.persistence.criteria.CriteriaQuery<?> cq,
                                                     jakarta.persistence.criteria.CriteriaBuilder cb,
                                                     ProjectArchiveQuery query) {
        Subquery<Long> sub = cq.subquery(Long.class);
        Root<ArchiveFile> fileRoot = sub.from(ArchiveFile.class);
        sub.select(fileRoot.get("archiveId"));
        List<jakarta.persistence.criteria.Predicate> filePredicates = new ArrayList<>();
        if (query.getUploadTimeStart() != null) {
            LocalDateTime start = LocalDate.parse(query.getUploadTimeStart(), DATE_FORMATTER).atStartOfDay();
            filePredicates.add(cb.greaterThanOrEqualTo(fileRoot.get("createdAt"), start));
        }
        if (query.getUploadTimeEnd() != null) {
            LocalDateTime end = LocalDate.parse(query.getUploadTimeEnd(), DATE_FORMATTER).atTime(23, 59, 59);
            filePredicates.add(cb.lessThanOrEqualTo(fileRoot.get("createdAt"), end));
        }
        sub.where(cb.and(filePredicates.toArray(new jakarta.persistence.criteria.Predicate[0])));
        return sub;
    }

    /** 结项时间筛选：基于 Project.closedAt（首次进入 CLOSED 阶段时填充）。 */
    private static Subquery<Long> closeTimeSubquery(jakarta.persistence.criteria.CriteriaQuery<?> cq,
                                                    jakarta.persistence.criteria.CriteriaBuilder cb,
                                                    ProjectArchiveQuery query) {
        Subquery<Long> sub = cq.subquery(Long.class);
        Root<Project> pRoot = sub.from(Project.class);
        sub.select(pRoot.get("id"));
        List<jakarta.persistence.criteria.Predicate> closePredicates = new ArrayList<>();
        if (query.getCloseTimeStart() != null) {
            LocalDateTime start = LocalDate.parse(query.getCloseTimeStart(), DATE_FORMATTER).atStartOfDay();
            closePredicates.add(cb.greaterThanOrEqualTo(pRoot.get("closedAt"), start));
        }
        if (query.getCloseTimeEnd() != null) {
            LocalDateTime end = LocalDate.parse(query.getCloseTimeEnd(), DATE_FORMATTER).atTime(23, 59, 59);
            closePredicates.add(cb.lessThanOrEqualTo(pRoot.get("closedAt"), end));
        }
        sub.where(cb.and(closePredicates.toArray(new jakarta.persistence.criteria.Predicate[0])));
        return sub;
    }

    /** 项目类型筛选：通过 Project.tenderId → Tender.projectType 匹配（含历史别名）。 */
    private static Subquery<Long> projectTypeSubquery(jakarta.persistence.criteria.CriteriaQuery<?> cq,
                                                      jakarta.persistence.criteria.CriteriaBuilder cb,
                                                      Set<String> expandedTypes) {
        Subquery<Long> sub = cq.subquery(Long.class);
        Root<Project> pRoot = sub.from(Project.class);
        Root<Tender> tRoot = sub.from(Tender.class);
        sub.select(pRoot.get("id"))
           .where(cb.and(
               cb.equal(pRoot.get("tenderId"), tRoot.get("id")),
               tRoot.get("projectType").in(expandedTypes)
           ));
        return sub;
    }

    /** 项目负责人筛选：通过 Project.tenderId → Tender.projectManagerName 精确匹配。 */
    private static Subquery<Long> projectManagerSubquery(jakarta.persistence.criteria.CriteriaQuery<?> cq,
                                                         jakarta.persistence.criteria.CriteriaBuilder cb,
                                                         String projectManager) {
        Subquery<Long> sub = cq.subquery(Long.class);
        Root<Project> pRoot = sub.from(Project.class);
        Root<Tender> tRoot = sub.from(Tender.class);
        sub.select(pRoot.get("id"))
           .where(cb.and(
               cb.equal(pRoot.get("tenderId"), tRoot.get("id")),
               cb.equal(tRoot.get("projectManagerName"), projectManager)
           ));
        return sub;
    }

    /** 投标负责人筛选：通过 ProjectInitiationDetails.biddingLeaderName 精确匹配（CO-421 数据源）。 */
    private static Subquery<Long> bidManagerSubquery(jakarta.persistence.criteria.CriteriaQuery<?> cq,
                                                     jakarta.persistence.criteria.CriteriaBuilder cb,
                                                     String bidManager) {
        Subquery<Long> sub = cq.subquery(Long.class);
        Root<ProjectInitiationDetails> dRoot = sub.from(ProjectInitiationDetails.class);
        sub.select(dRoot.get("projectId"))
           .where(cb.equal(dRoot.get("biddingLeaderName"), bidManager));
        return sub;
    }
}
