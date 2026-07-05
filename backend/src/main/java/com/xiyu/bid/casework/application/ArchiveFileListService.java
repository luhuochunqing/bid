package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.dto.ArchiveFileListItem;
import com.xiyu.bid.casework.dto.ProjectArchiveQuery;
import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * CO-496: 文档分类下载文件视图列表服务。
 * 复用 ProjectArchiveWorkflowService.getRawArchives 做项目级筛选，再做文件级展开与二次过滤。
 *
 * TODO: 迁移到数据库级分页。当前全量加载所有匹配 archive 的文件后内存分页，
 * 在 archive 数量大时会产生性能问题。应改为 JOIN 查询 + Specification + Pageable 下推到 DB。
 * 触发条件：当 getRawArchives 返回超过 100 条 archive 时，建议优先完成此优化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchiveFileListService {

    private static final int ARCHIVE_COUNT_WARN_THRESHOLD = 100;

    private final ProjectArchiveWorkflowService workflowService;
    private final ArchiveFileRepository fileRepository;
    private final ProjectRepository projectRepository;
    private final TenderRepository tenderRepository;
    private final ProjectInitiationDetailsRepository initiationDetailsRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public Page<ArchiveFileListItem> queryFiles(ProjectArchiveQuery query, Pageable pageable) {
        List<ProjectArchive> archives = workflowService.getRawArchives(query);
        if (archives.isEmpty()) {
            return Page.empty(pageable);
        }
        if (archives.size() > ARCHIVE_COUNT_WARN_THRESHOLD) {
            log.warn("ArchiveFileList 查询返回 {} 条 archive 超过阈值 {}，建议迁移到 DB 级分页", archives.size(), ARCHIVE_COUNT_WARN_THRESHOLD);
        }

        Map<Long, ProjectArchive> archiveById = archives.stream()
                .collect(toMap(ProjectArchive::getId, a -> a, (a, b) -> a));
        List<ArchiveFile> files = fileRepository.findByArchiveIdInOrderByCreatedAtDesc(archiveById.keySet().stream().toList());

        Stream<ArchiveFile> stream = files.stream()
                .filter(f -> archiveById.containsKey(f.getArchiveId()));

        stream = applyFileLevelFilters(stream, query);

        // 先收集过滤后的文件，再 prefetch 仅需要的 archive（避免对已被过滤掉的 archive 做无用查询）
        List<ArchiveFile> filteredFiles = stream.toList();
        Set<Long> neededArchiveIds = filteredFiles.stream()
                .map(ArchiveFile::getArchiveId).collect(toSet());
        List<ProjectArchive> neededArchives = archives.stream()
                .filter(a -> neededArchiveIds.contains(a.getId())).toList();
        EnrichedFields prefetch = prefetchFields(neededArchives);

        List<ArchiveFileListItem> items = filteredFiles.stream()
                .map(f -> toItem(f, archiveById.get(f.getArchiveId()), prefetch))
                .sorted(Comparator.comparing(ArchiveFileListItem::uploadedAt).reversed())
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), items.size());
        if (start >= items.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, items.size());
        }
        return new PageImpl<>(items.subList(start, end), pageable, items.size());
    }

    private Stream<ArchiveFile> applyFileLevelFilters(Stream<ArchiveFile> stream, ProjectArchiveQuery query) {
        if (query.getDocumentCategories() != null && !query.getDocumentCategories().isEmpty()) {
            Set<String> categories = Set.copyOf(query.getDocumentCategories());
            stream = stream.filter(f -> categories.contains(f.getDocumentCategory()));
        }
        if (query.getUploadTimeStart() != null && !query.getUploadTimeStart().isBlank()) {
            LocalDateTime start = LocalDate.parse(query.getUploadTimeStart(), DATE_FORMATTER).atStartOfDay();
            stream = stream.filter(f -> f.getCreatedAt() != null && !f.getCreatedAt().isBefore(start));
        }
        if (query.getUploadTimeEnd() != null && !query.getUploadTimeEnd().isBlank()) {
            LocalDateTime end = LocalDate.parse(query.getUploadTimeEnd(), DATE_FORMATTER).atTime(23, 59, 59);
            stream = stream.filter(f -> f.getCreatedAt() != null && !f.getCreatedAt().isAfter(end));
        }
        return stream;
    }

    private EnrichedFields prefetchFields(List<ProjectArchive> archives) {
        List<Long> projectIds = archives.stream()
                .map(ProjectArchive::getProjectId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (projectIds.isEmpty()) {
            return EnrichedFields.empty();
        }

        Map<Long, Project> projectMap = projectRepository.findAllById(projectIds).stream()
                .filter(p -> p.getId() != null)
                .collect(toMap(Project::getId, p -> p, (a, b) -> a));

        List<Long> tenderIds = projectMap.values().stream()
                .map(Project::getTenderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Tender> tenderMap = tenderIds.isEmpty()
                ? Collections.emptyMap()
                : tenderRepository.findAllById(tenderIds).stream()
                        .collect(toMap(Tender::getId, t -> t, (a, b) -> a));

        Map<Long, String> bidManagerNameByProjectId = initiationDetailsRepository
                .findByProjectIdIn(projectIds).stream()
                .filter(d -> d.getBiddingLeaderName() != null && !d.getBiddingLeaderName().isBlank())
                .collect(toMap(ProjectInitiationDetails::getProjectId,
                        ProjectInitiationDetails::getBiddingLeaderName, (a, b) -> a));

        return new EnrichedFields(projectMap, tenderMap, bidManagerNameByProjectId);
    }

    private ArchiveFileListItem toItem(ArchiveFile file, ProjectArchive archive, EnrichedFields prefetch) {
        Project project = archive.getProjectId() != null ? prefetch.projectMap.get(archive.getProjectId()) : null;
        Tender tender = project != null ? prefetch.tenderMap.get(project.getTenderId()) : null;
        String projectStatus = project != null ? project.getStatus().name() : "PENDING_INITIATION";
        String projectType = tender != null && tender.getProjectType() != null ? tender.getProjectType() : "综合";
        String projectManager = tender != null && tender.getProjectManagerName() != null ? tender.getProjectManagerName() : "-";
        String bidManager = project != null && prefetch.bidManagerNameByProjectId.get(project.getId()) != null
                ? prefetch.bidManagerNameByProjectId.get(project.getId()) : "-";

        return new ArchiveFileListItem(
                file.getId(),
                archive.getProjectId(),
                archive.getProjectName(),
                projectType,
                projectStatus,
                file.getFileName(),
                file.getDocumentCategory(),
                projectManager,
                bidManager,
                file.getUploadUserName(),
                file.getFileSize(),
                file.getCreatedAt()
        );
    }

    private record EnrichedFields(
            Map<Long, Project> projectMap,
            Map<Long, Tender> tenderMap,
            Map<Long, String> bidManagerNameByProjectId
    ) {
        static EnrichedFields empty() {
            return new EnrichedFields(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }
    }
}
