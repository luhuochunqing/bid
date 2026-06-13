package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.dto.ProjectArchiveResponse;
import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
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

    ProjectArchiveResponseMapper(ProjectRepository projectRepository,
                                 TenderRepository tenderRepository,
                                 ArchiveFileRepository fileRepository) {
        this.projectRepository = projectRepository;
        this.tenderRepository = tenderRepository;
        this.fileRepository = fileRepository;
    }

    List<ProjectArchiveResponse> toResponseList(List<ProjectArchive> archives) {
        if (archives.isEmpty()) return List.of();

        List<Long> projectIds = archives.stream().map(ProjectArchive::getProjectId).distinct().toList();
        Map<Long, Project> projectMap = projectRepository.findAllById(projectIds).stream()
                .collect(toMap(Project::getId, p -> p));

        List<Long> tenderIds = projectMap.values().stream()
                .map(Project::getTenderId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Tender> tenderMap = tenderRepository.findAllById(tenderIds).stream()
                .collect(toMap(Tender::getId, t -> t));

        List<Long> archiveIds = archives.stream().map(ProjectArchive::getId).toList();
        Map<Long, List<ArchiveFile>> filesByArchive = fileRepository
                .findByArchiveIdInOrderByCreatedAtDesc(archiveIds)
                .stream().collect(groupingBy(ArchiveFile::getArchiveId));

        return archives.stream().map(a -> toResponse(a, projectMap, tenderMap, filesByArchive)).toList();
    }

    private ProjectArchiveResponse toResponse(ProjectArchive archive,
                                               Map<Long, Project> projectMap,
                                               Map<Long, Tender> tenderMap,
                                               Map<Long, List<ArchiveFile>> filesByArchive) {
        Project project = projectMap.get(archive.getProjectId());
        Tender tender = project != null ? tenderMap.get(project.getTenderId()) : null;

        String projectStatus = project != null ? project.getStatus().name() : "PENDING_INITIATION";
        String projectType = tender != null && tender.getProjectType() != null ? tender.getProjectType() : "综合";
        String purchaserName = tender != null ? tender.getPurchaserName() : null;
        String projectManager = tender != null ? tender.getProjectManagerName() : null;
        String bidManager = tender != null ? tender.getBiddingPersonName() : null;

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
        return archives.stream()
                .flatMap(a -> {
                    Tender t = resolveTender(a);
                    return t != null && t.getBiddingPersonName() != null
                            ? Stream.of(t.getBiddingPersonName()) : Stream.empty();
                }).distinct().sorted().toList();
    }

    private Tender resolveTender(ProjectArchive archive) {
        return projectRepository.findById(archive.getProjectId())
                .map(Project::getTenderId)
                .flatMap(tenderRepository::findById)
                .orElse(null);
    }
}
