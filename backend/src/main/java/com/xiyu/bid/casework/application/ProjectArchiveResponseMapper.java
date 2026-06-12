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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 4.1.1 档案台账列表响应装配：从 ProjectArchive + Project + Tender + 关联文件 计算
 * projectType / bidResult / 负责人 / 分类文件数 / lastUploadedAt 等聚合字段。
 * <p>
 * 从 ProjectArchiveWorkflowService 拆出，避免后者单文件 300 行硬上限突破。
 */
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

    ProjectArchiveResponse toResponse(ProjectArchive archive) {
        String projectType = "综合";
        String bidResult = "OTHER";
        String projectManager = null;
        String bidManager = null;
        String projectStatus = "PENDING_INITIATION";

        Optional<Project> projectOpt = projectRepository.findById(archive.getProjectId());
        Optional<Tender> tenderOpt = projectOpt
                .map(Project::getTenderId)
                .flatMap(tenderRepository::findById);
        if (projectOpt.isPresent()) {
            Project p = projectOpt.get();
            projectStatus = p.getStatus().name();
            // bidResult 从 Project.Status 终态推导（产品蓝图 §4.3）
            bidResult = switch (p.getStatus()) {
                case WON -> "WON";
                case LOST -> "LOST";
                case FAILED -> "FAILED";
                case ABANDONED -> "ABANDONED";
                default -> "IN_PROGRESS";
            };
        }
        if (tenderOpt.isPresent()) {
            Tender tender = tenderOpt.get();
            projectType = tender.getProjectType();
            projectManager = tender.getProjectManagerName();
            bidManager = tender.getBiddingPersonName();
        }

        List<ArchiveFile> files = fileRepository.findByArchiveIdOrderByCreatedAtDesc(archive.getId());
        Map<String, Long> categoryDetails = new HashMap<>();
        for (String cat : CATEGORY_KEYS) {
            categoryDetails.put(cat, 0L);
        }
        files.forEach(f -> {
            String cat = f.getDocumentCategory();
            categoryDetails.put(cat, categoryDetails.getOrDefault(cat, 0L) + 1);
        });

        LocalDateTime lastUploadedAt = files.stream()
                .map(ArchiveFile::getCreatedAt)
                .max(Comparator.naturalOrder())
                .orElse(archive.getCreatedAt());

        return new ProjectArchiveResponse(
                archive.getId(),
                archive.getProjectId(),
                archive.getProjectName(),
                projectType,
                projectStatus,
                bidResult,
                files.size(),
                categoryDetails,
                lastUploadedAt,
                projectManager,
                bidManager
        );
    }

    List<String> collectProjectManagers(List<ProjectArchive> archives) {
        return archives.stream()
                .flatMap(a -> {
                    Tender t = resolveTender(a);
                    return t != null && t.getProjectManagerName() != null
                            ? Stream.of(t.getProjectManagerName()) : Stream.empty();
                })
                .distinct()
                .sorted()
                .toList();
    }

    List<String> collectBidManagers(List<ProjectArchive> archives) {
        return archives.stream()
                .flatMap(a -> {
                    Tender t = resolveTender(a);
                    return t != null && t.getBiddingPersonName() != null
                            ? Stream.of(t.getBiddingPersonName()) : Stream.empty();
                })
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
