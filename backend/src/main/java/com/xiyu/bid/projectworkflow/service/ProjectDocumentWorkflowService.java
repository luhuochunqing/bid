package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.common.domain.AuthorizationDecision;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.project.core.BidReviewStatus;
import com.xiyu.bid.projectworkflow.core.DocumentCategoryNormalizer;
import com.xiyu.bid.projectworkflow.core.ProjectDocumentWorkflowPolicy;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.project.notification.DocumentChangeNotificationService;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class ProjectDocumentWorkflowService {

    private final ProjectWorkflowGuardService guardService;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final UserRepository userRepository;
    private final ProjectDocumentViewAssembler projectDocumentViewAssembler;
    private final ProjectDocumentBindingGateway projectDocumentBindingGateway;
    private final CurrentUserResolver currentUserResolver;
    private final DocumentChangeNotificationService documentChangeNotificationService;
    // CO-558: 读取标书审核状态，用于 BID 类文档删除前的"审核中/已通过不可删除"守卫
    private final BidDocumentReviewRepository bidDocumentReviewRepository;

    List<ProjectDocumentDTO> getProjectDocuments(Long projectId) {
        return getProjectDocuments(projectId, null, null, null);
    }

    List<ProjectDocumentDTO> getProjectDocuments(
            Long projectId,
            String documentCategory,
            String linkedEntityType,
            Long linkedEntityId
    ) {
        guardService.requireProject(projectId);
        // CO-481: 移除 CO-474 引入的第二层 view 闸门，恢复 605ace4a5 设计。
        // 项目文档访问权由 ProjectAccessScopeService（guardService.requireProject）统一负责，不再二次校验 role 白名单。
        return projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                        projectId,
                        trimToNull(documentCategory),
                        trimToNull(linkedEntityType),
                        linkedEntityId
                ).stream()
                .map(projectDocumentViewAssembler::toDto)
                .toList();
    }

    ProjectDocumentDTO createProjectDocument(Long projectId, ProjectDocumentCreateRequest request) {
        guardService.requireProject(projectId);
        assertCanUploadProjectDocument();
        Long uploaderId = request.getUploaderId();
        String uploaderName = request.getUploaderName();
        if (uploaderId == null && (uploaderName == null || uploaderName.isBlank())) {
            var currentUser = currentUserResolver.getCurrentUser();
            if (currentUser != null) {
                uploaderId = currentUser.getId();
                uploaderName = currentUser.getFullName();
            }
        } else {
            uploaderName = resolveDisplayName(uploaderId, uploaderName);
        }
        ProjectDocument document = ProjectDocument.builder()
                .projectId(projectId)
                .name(request.getName().trim())
                .size(defaultString(request.getSize(), "1MB"))
                .fileType(trimToNull(request.getFileType()))
                // CO-420: 归一化 documentCategory 到标准枚举名（TENDER/BID/OPEN_LIST/WIN_NOTICE/DEPOSIT_RECEIPT/OTHER）
                .documentCategory(DocumentCategoryNormalizer.normalize(request.getDocumentCategory()))
                .linkedEntityType(trimToNull(request.getLinkedEntityType()))
                .linkedEntityId(request.getLinkedEntityId())
                .fileUrl(trimToNull(request.getFileUrl()))
                .uploaderId(uploaderId)
                .uploaderName(uploaderName)
                .build();
        ProjectDocument savedDocument = projectDocumentRepository.save(document);
        projectDocumentBindingGateway.onDocumentCreated(savedDocument);
        // 蓝图 §消息中心-系统通知 序号 5：文档上传通知项目团队成员（排除上传者自己）
        documentChangeNotificationService.notifyDocumentChanged(
                projectId,
                savedDocument.getId(),
                savedDocument.getName(),
                uploaderName != null ? uploaderName : "未分配",
                "上传",
                uploaderId
        );
        return projectDocumentViewAssembler.toDto(savedDocument);
    }

    void deleteProjectDocument(Long projectId, Long documentId) {
        // CO-487: 结项状态下删除附件报错信息优化——先检查项目状态，给出更友好的提示
        // 必须抛 BusinessException（业务异常），否则会被 GlobalExceptionHandler 当作系统缺陷
        // 吞掉 message，统一返回"系统状态冲突，请刷新后重试"。
        try {
            guardService.requireWorkflowMutationProject(projectId);
        } catch (IllegalStateException e) {
            throw new BusinessException(409, "项目已结项，不可删除文件");
        }

        ProjectDocument document = guardService.requireDocument(projectId, documentId);
        var currentUser = currentUserResolver.requireCurrentUser();
        String roleCode = currentUserResolver.resolveEffectiveRoleCode(currentUser);
        // CO-383: 上传者本人可删除自己上传的文件（未提交前可重传）
        AuthorizationDecision decision = ProjectDocumentWorkflowPolicy.canDeleteProjectDocument(
                roleCode, currentUser.getId(), document.getUploaderId());
        if (!decision.allowed()) {
            throw new org.springframework.security.access.AccessDeniedException(decision.reason());
        }

        // CO-558: 投标文件审核中/已通过后不可删除（真权限闸门，防绕过前端直接调 API）。
        // 仅对 documentCategory=BID 生效；TENDER/TASK_ATTACHMENT 等其他文档不受影响。
        // REJECTED/无审核记录不拦截（允许驳回后修改重传）。放在角色检查之后，
        // 让 sales/非上传者先被 403 精准挡住，不会先撞审核状态守卫。
        assertBidDocumentNotUnderReview(projectId, document);

        // 蓝图 §消息中心-系统通知 序号 5：文档删除通知项目团队成员（排除删除者自己）
        // 放在 repository.delete 之前——delete 前实体信息（name/id）完整可用
        documentChangeNotificationService.notifyDocumentChanged(
                projectId,
                document.getId(),
                document.getName(),
                currentUser.getFullName(),
                "删除",
                currentUser.getId()
        );

        projectDocumentRepository.delete(document);
        projectDocumentBindingGateway.onDocumentDeleted(document);
    }

    private void assertCanUploadProjectDocument() {
        String roleCode = currentUserResolver.getCurrentRoleCode();
        AuthorizationDecision decision = ProjectDocumentWorkflowPolicy.canUploadProjectDocument(roleCode);
        if (!decision.allowed()) {
            throw new org.springframework.security.access.AccessDeniedException(decision.reason());
        }
    }

    /**
     * CO-558: 若被删文档是投标文件（documentCategory 归一化为 BID）且该项目标书审核处于
     * REVIEWING/APPROVED 状态，则拒绝删除。REJECTED 或无审核记录时不拦截。
     * <p>状态值与 {@link com.xiyu.bid.project.core.BidReviewStatus} 对齐（REVIEWING/APPROVED/REJECTED）。</p>
     */
    private void assertBidDocumentNotUnderReview(Long projectId, ProjectDocument document) {
        String category = DocumentCategoryNormalizer.normalize(document.getDocumentCategory());
        if (!"BID".equals(category)) {
            return;
        }
        var review = bidDocumentReviewRepository.findByProjectId(projectId).orElse(null);
        if (review == null) {
            return;
        }
        String status = review.getStatus();
        // 复用 BidReviewStatus 枚举常量（单一真相源），避免硬编码字符串；Entity 字段为 String 故用 name() 比较
        boolean underReview = BidReviewStatus.REVIEWING.name().equalsIgnoreCase(status)
                || BidReviewStatus.APPROVED.name().equalsIgnoreCase(status);
        if (underReview) {
            String label = BidReviewStatus.REVIEWING.name().equalsIgnoreCase(status) ? "审核中" : "已通过审核";
            throw new BusinessException(409, "投标文件" + label + "，不可删除");
        }
    }

    private String resolveDisplayName(Long userId, String fallback) {
        if (userId != null) {
            var user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getFullName() != null && !user.getFullName().isBlank()) {
                // CO-488: 返回"姓名（工号）"格式，工号取 employeeNumber
                String empNo = user.getEmployeeNumber();
                if (empNo != null && !empNo.isBlank()) {
                    return user.getFullName() + "（" + empNo.trim() + "）";
                }
                return user.getFullName();
            }
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return "未分配";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultString(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized != null ? normalized : fallback;
    }

}
