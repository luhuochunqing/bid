package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.dto.ProjectArchiveResponse;
import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

@Component
class ProjectArchiveResponseMapper {

    private static final List<String> CATEGORY_KEYS = List.of(
            "TENDER", "BID", "OPEN_LIST", "WIN_NOTICE", "DEPOSIT_RECEIPT", "OTHER");

    private final ProjectRepository projectRepository;
    private final TenderRepository tenderRepository;
    private final ArchiveFileRepository fileRepository;
    private final ProjectInitiationDetailsRepository initiationDetailsRepository;

    ProjectArchiveResponseMapper(ProjectRepository projectRepository,
                                 TenderRepository tenderRepository,
                                 ArchiveFileRepository fileRepository,
                                 ProjectInitiationDetailsRepository initiationDetailsRepository) {
        this.projectRepository = projectRepository;
        this.tenderRepository = tenderRepository;
        this.fileRepository = fileRepository;
        this.initiationDetailsRepository = initiationDetailsRepository;
    }

    List<ProjectArchiveResponse> toResponseList(List<ProjectArchive> archives) {
        if (archives.isEmpty()) return List.of();

        List<Long> projectIds = archives.stream().map(ProjectArchive::getProjectId).distinct().toList();
        Map<Long, Project> projectMap = projectRepository.findAllById(projectIds).stream()
                .collect(toMap(Project::getId, p -> p,
                        (a, b) -> a)); // CO-027: merge function 防止 Duplicate key 异常

        List<Long> tenderIds = projectMap.values().stream()
                .map(Project::getTenderId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Tender> tenderMap = tenderRepository.findAllById(tenderIds).stream()
                .collect(toMap(Tender::getId, t -> t,
                        (a, b) -> a)); // CO-027: merge function 防止 Duplicate key 异常

        // CO-421: 投标负责人姓名直接读 ProjectInitiationDetails.biddingLeaderName
        // （立项审核通过时已同步，详见 ProjectInitiationApprovalService.approve）
        Map<Long, String> bidManagerNameByProjectId = initiationDetailsRepository
                .findByProjectIdIn(projectIds).stream()
                .filter(d -> d.getBiddingLeaderName() != null && !d.getBiddingLeaderName().isBlank())
                .collect(toMap(ProjectInitiationDetails::getProjectId,
                        ProjectInitiationDetails::getBiddingLeaderName, (a, b) -> a));

        List<Long> archiveIds = archives.stream().map(ProjectArchive::getId).toList();
        Map<Long, List<ArchiveFile>> filesByArchive = fileRepository
                .findByArchiveIdInOrderByCreatedAtDesc(archiveIds)
                .stream().collect(groupingBy(ArchiveFile::getArchiveId));

        return archives.stream()
                .map(a -> toResponse(a, projectMap, tenderMap, filesByArchive, bidManagerNameByProjectId))
                .toList();
    }

    private ProjectArchiveResponse toResponse(ProjectArchive archive,
                                               Map<Long, Project> projectMap,
                                               Map<Long, Tender> tenderMap,
                                               Map<Long, List<ArchiveFile>> filesByArchive,
                                               Map<Long, String> bidManagerNameByProjectId) {
        Project project = projectMap.get(archive.getProjectId());
        Tender tender = project != null ? tenderMap.get(project.getTenderId()) : null;

        String projectStatus = project != null ? project.getStatus().name() : "PENDING_INITIATION";
        String projectType = tender != null && tender.getProjectType() != null ? tender.getProjectType() : "综合";
        String purchaserName = tender != null ? tender.getPurchaserName() : null;
        String projectManager = tender != null ? tender.getProjectManagerName() : null;
        // CO-421: 投标负责人取自 ProjectInitiationDetails.biddingLeaderName，而非 Tender.biddingPersonName
        String bidManager = bidManagerNameByProjectId.get(archive.getProjectId());

        String bidResult = "OTHER";
        if (project != null) {
            Project.Status ps = project.getStatus();
            if (ps == Project.Status.WON || ps == Project.Status.LOST
                    || ps == Project.Status.FAILED || ps == Project.Status.ABANDONED) {
                bidResult = ps.name();
            }
        }

        List<ArchiveFile> files = filesByArchive.getOrDefault(archive.getId(), List.of());
        Map<String, Long> categoryDetails = new HashMap<>();
        for (String cat : CATEGORY_KEYS) categoryDetails.put(cat, 0L);
        files.forEach(f -> categoryDetails.merge(f.getDocumentCategory(), 1L, Long::sum));

        LocalDateTime lastUploadedAt = files.stream()
                .map(ArchiveFile::getCreatedAt)
                .max(Comparator.naturalOrder())
                .orElse(archive.getCreatedAt());

        return new ProjectArchiveResponse(
                archive.getId(), archive.getProjectId(), archive.getProjectName(),
                projectType, projectStatus, bidResult, purchaserName,
                files.size(), categoryDetails, lastUploadedAt,
                projectManager, bidManager);
    }

    @Deprecated
    ProjectArchiveResponse toResponse(ProjectArchive archive) {
        return toResponseList(List.of(archive)).getFirst();
    }

    List<String> collectProjectManagers(List<ProjectArchive> archives) {
        return archives.stream()
                .flatMap(a -> {
                    Tender t = resolveTender(a);
                    return t != null && t.getProjectManagerName() != null
                            ? Stream.of(t.getProjectManagerName()) : Stream.empty();
                }).distinct().sorted().toList();
    }

    List<String> collectBidManagers(List<ProjectArchive> archives) {
        if (archives.isEmpty()) return List.of();
        List<Long> projectIds = archives.stream().map(ProjectArchive::getProjectId).distinct().toList();
        // CO-421: 投标负责人姓名读 ProjectInitiationDetails.biddingLeaderName（批量查询）
        return initiationDetailsRepository.findByProjectIdIn(projectIds).stream()
                .map(ProjectInitiationDetails::getBiddingLeaderName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private Tender resolveTender(ProjectArchive archive) {
        return projectRepository.findById(archive.getProjectId())
                .map(Project::getTenderId)
                .flatMap(tenderRepository::findById)
                .orElse(null);
    }
}
