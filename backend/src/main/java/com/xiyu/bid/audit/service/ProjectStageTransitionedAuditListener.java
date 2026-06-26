package com.xiyu.bid.audit.service;

import com.xiyu.bid.project.domain.ProjectStageTransitionedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 监听 {@link ProjectStageTransitionedEvent}，记录「从 XX 推进至 YY」的项目动态操作日志。
 *
 * <p>CO-324：用事件解耦替代 service 直接注入 {@code IAuditLogService}
 *（ArchitectureTest RULE 12：Service 不得直接注入 IAuditLogService/AuditLogRepository，
 * 例外为 audit 模块与 aspect 模块 —— 本 listener 位于 audit 模块，合规）。
 *
 * <p>修正点（对比 CO-324 早期手动记录版）：
 * <ul>
 *   <li>操作人取当前登录用户（SecurityContext），不再误用项目经理 id / 项目名；</li>
 *   <li>description 含源 + 目标阶段中文名，如「从 评标 推进至 结果确认」。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectStageTransitionedAuditListener {

    private final IAuditLogService auditLogService;

    @EventListener
    public void onStageTransitioned(ProjectStageTransitionedEvent event) {
        String operator = currentOperator();
        String description = "从 " + event.fromStage().getDisplayName()
                + " 推进至 " + event.toStage().getDisplayName();
        try {
            auditLogService.log(AuditLogService.AuditLogEntry.builder()
                    .userId(operator)
                    .username(operator)
                    .action("PROJECT_STAGE_TRANSITIONED")
                    .entityType("Project")
                    .entityId(String.valueOf(event.projectId()))
                    .projectId(event.projectId())
                    .description(description)
                    .success(true)
                    .build());
        } catch (RuntimeException e) {
            // 审计记录失败不应阻断主流程（阶段推进已完成）
            log.warn("Failed to record stage-transition audit: projectId={}, {}→{}",
                    event.projectId(), event.fromStage(), event.toStage(), e);
        }
    }

    private static String currentOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "system";
    }
}
