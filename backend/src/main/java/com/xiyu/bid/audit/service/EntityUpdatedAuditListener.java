package com.xiyu.bid.audit.service;

import com.xiyu.bid.audit.event.EntityUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 监听 {@link EntityUpdatedEvent}，记录实体编辑操作日志。
 *
 * <p>CO-515：用事件解耦替代 service 直接注入 {@code IAuditLogService}
 *（ArchitectureTest RULE 12：Service 不得直接注入 IAuditLogService/AuditLogRepository，
 * 例外为 audit 模块与 aspect 模块 —— 本 listener 位于 audit 模块，合规）。
 *
 * <p>事件携带已计算好的 summary（由业务模块用 diff 计算器生成），audit 模块不反向依赖业务类型，
 * 避免循环依赖（如 audit → resources → alerts → businessqualification → audit）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EntityUpdatedAuditListener {

    /** 审计日志服务。 */
    private final IAuditLogService auditLogService;

    /**
     * 处理实体更新事件，写入审计日志。
     *
     * @param event 实体更新事件。
     */
    @EventListener
    public void onEntityUpdated(final EntityUpdatedEvent event) {
        if (event.summary() == null || event.summary().isBlank()) {
            return;
        }
        String operator = currentOperator();
        try {
            auditLogService.log(AuditLogService.AuditLogEntry.builder()
                    .userId(operator)
                    .username(operator)
                    .action(event.action())
                    .entityType(event.entityType())
                    .entityId(String.valueOf(event.entityId()))
                    .description(event.summary())
                    .success(true)
                    .build());
        } catch (RuntimeException e) {
            // 审计记录失败不应阻断主流程（实体编辑已完成）
            log.warn("Failed to record entity update audit: type={}, id={}",
                    event.entityType(), event.entityId(), e);
        }
    }

    /**
     * 从 SecurityContext 解析当前操作人。
     *
     * @return 当前登录用户名，无认证信息时返回 "system"。
     */
    private static String currentOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "system";
    }
}
