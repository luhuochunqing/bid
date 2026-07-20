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
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectArchiveDetailService {

    private final ProjectArchiveRepository archiveRepository;
    private final ArchiveFileRepository fileRepository;
    private final ArchiveLogRepository logRepository;
    private final ProjectRepository projectRepository;
    private final TenderRepository tenderRepository;
    private final ProjectInitiationDetailsRepository initiationDetailsRepository;
    // 档案修复：操作日志 operator 字段拼"姓名（工号）"，需要反查 User 表
    private final UserRepository userRepository;

    public ProjectArchiveDetailResponse getArchiveDetail(Long archiveId) {
        ProjectArchive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new IllegalArgumentException("Archive not found: " + archiveId));

        String projectType = "综合";
        String projectStatus = "PENDING_INITIATION";
        String bidResult = "OTHER";
        String projectManager = "未知";
        String bidManager = null;
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
                tenderAgency = tender.getPurchaserName();
                bidOpeningAt = tender.getBidOpeningTime();
            }
            // 档案 4.1.1.1.1：时间戳从 Project 实体读取（替代 Tender 降级）
            // initiatedAt 降级为 Project.createdAt（无 Tender 时仍有近似值）
            initiatedAt = p.getInitiatedAt() != null ? p.getInitiatedAt() : p.getCreatedAt();
            bidSubmissionAt = p.getEvaluatingAt();
            closedAt = p.getClosedAt();
        }

        // CO-421: 投标负责人姓名读 ProjectInitiationDetails.biddingLeaderName
        // （立项审核通过时已同步，详见 ProjectInitiationApprovalService.approve）
        bidManager = initiationDetailsRepository.findByProjectId(archive.getProjectId())
                .map(ProjectInitiationDetails::getBiddingLeaderName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(null);

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
        // 档案修复：操作日志 operator 当前存的是 User.username（OSS 用户 username=工号），
        // 读取时批量反查 User 表，拼成"姓名（工号）"格式（如"郑蓉蓉（06234）"），与项目文档上传人展示一致。
        // ArchiveLog.operatorName 存的就是 username，按 username 批量查即可。
        Set<String> operatorUsernames = logs.stream()
                .map(ArchiveLog::getOperatorName)
                .filter(Objects::nonNull)
                .filter(n -> !n.isBlank())
                .collect(Collectors.toSet());
        Map<String, User> userByUsername = operatorUsernames.isEmpty()
                ? Map.of()
                : userRepository.findAllByUsernameIn(operatorUsernames).stream()
                .collect(Collectors.toMap(User::getUsername, Function.identity(), (a, b) -> a));
        List<ProjectArchiveDetailResponse.ArchiveLogDTO> logDTOs = logs.stream()
                .map(l -> new ProjectArchiveDetailResponse.ArchiveLogDTO(
                        l.getId(),
                        l.getCreatedAt(),
                        resolveOperatorDisplay(l.getOperatorName(), userByUsername),
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

    /**
     * 档案修复：把 ArchiveLog.operatorName（= User.username）解析为"姓名（工号）"格式。
     * <p>逻辑：
     * <ul>
     *   <li>能反查到 User 且 fullName 非空：返回"姓名（工号）"，工号取自 getDisplayEmployeeNumber()（自带兜底回退到 username）</li>
     *   <li>能反查到 User 但 fullName 为空：返回 username（保持原值）</li>
     *   <li>反查不到（历史数据/系统账号/"系统"字符串）：返回原始 operatorName，避免历史日志变空白</li>
     * </ul>
     */
    private String resolveOperatorDisplay(String operatorName, Map<String, User> userByUsername) {
        if (operatorName == null || operatorName.isBlank()) return "系统";
        User user = userByUsername.get(operatorName);
        if (user == null) return operatorName;
        String fullName = user.getFullName();
        if (fullName == null || fullName.isBlank()) return operatorName;
        String empNo = user.getDisplayEmployeeNumber();
        return (empNo != null && !empNo.isBlank())
                ? fullName + "（" + empNo + "）"
                : fullName;
    }
}
