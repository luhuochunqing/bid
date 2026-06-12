package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.dto.ProjectArchiveDetailResponse;
import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.ArchiveLog;
import com.xiyu.bid.casework.infrastructure.ArchiveLogRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.casework.infrastructure.ProjectArchiveRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectArchiveDetailService {

    private final ProjectArchiveRepository archiveRepository;
    private final ArchiveFileRepository fileRepository;
    private final ArchiveLogRepository logRepository;
    private final ProjectRepository projectRepository;
    private final TenderRepository tenderRepository;

    public ProjectArchiveDetailResponse getArchiveDetail(Long archiveId) {
        ProjectArchive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new IllegalArgumentException("Archive not found: " + archiveId));

        String projectType = "综合";
        String projectStatus = "PENDING_INITIATION";
        String bidResult = "OTHER";
        String projectManager = "未知";
        String bidManager = "未知";
        String tenderAgency = null;
        LocalDateTime initiatedAt = null;
        LocalDateTime bidSubmissionAt = null;
        LocalDateTime bidOpeningAt = null;
        LocalDateTime closedAt = null;

        Optional<Project> projectOpt = projectRepository.findById(archive.getProjectId());
        if (projectOpt.isPresent()) {
            Project p = projectOpt.get();
            projectStatus = p.getStatus().name();
            // bidResult 从 Project.Status 终态推导
            bidResult = switch (p.getStatus()) {
                case WON -> "WON";
                case LOST -> "LOST";
                case FAILED -> "FAILED";
                case ABANDONED -> "ABANDONED";
                default -> "IN_PROGRESS";
            };
            Optional<Tender> tenderOpt = tenderRepository.findById(p.getTenderId());
            if (tenderOpt.isPresent()) {
                Tender tender = tenderOpt.get();
                projectType = tender.getProjectType();
                projectManager = tender.getProjectManagerName();
                bidManager = tender.getBiddingPersonName();
                tenderAgency = tender.getTenderAgency();
                bidOpeningAt = tender.getBidOpeningTime();
            }
            // 档案 4.1.1.1.1：时间戳从 Project 实体读取（替代 Tender 降级）
            // initiatedAt 降级为 Project.createdAt（无 Tender 时仍有近似值）
            initiatedAt = p.getInitiatedAt() != null ? p.getInitiatedAt() : p.getCreatedAt();
            bidSubmissionAt = p.getEvaluatingAt();
            closedAt = p.getClosedAt();
        }

        List<ArchiveFile> files = fileRepository.findByArchiveIdOrderByCreatedAtDesc(archiveId);
        List<ProjectArchiveDetailResponse.ArchiveFileDTO> fileDTOs = files.stream()
                .map(f -> new ProjectArchiveDetailResponse.ArchiveFileDTO(
                        f.getId(),
                        f.getFileName(),
                        f.getDocumentCategory(),
                        f.getUploadUserName(),
                        f.getCreatedAt(),
                        f.getFileSize()
                ))
                .toList();

        List<ArchiveLog> logs = logRepository.findByArchiveIdOrderByCreatedAtDesc(archiveId);
        List<ProjectArchiveDetailResponse.ArchiveLogDTO> logDTOs = logs.stream()
                .map(l -> new ProjectArchiveDetailResponse.ArchiveLogDTO(
                        l.getId(),
                        l.getCreatedAt(),
                        l.getOperatorName(),
                        l.getActionType(),
                        l.getActionContent()
                ))
                .toList();

        return new ProjectArchiveDetailResponse(
                archive.getId(),
                archive.getProjectId(),
                archive.getProjectName(),
                projectType,
                projectStatus,
                bidResult,
                tenderAgency,
                initiatedAt,
                bidSubmissionAt,
                bidOpeningAt,
                closedAt,
                projectManager,
                bidManager,
                fileDTOs,
                logDTOs
        );
    }
}
