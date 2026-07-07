package com.xiyu.bid.audit.event;

/**
 * 实体更新领域事件（纯 POJO，不依赖 Spring —— domain purity）。
 *
 * <p>CO-515：由各模块 Service 发布，audit 模块监听并记录操作日志。
 * 事件携带已计算好的变更摘要（summary），避免 audit 模块反向依赖业务模块的类型。
 *
 * <p>Service 不直接注入 {@code IAuditLogService}（ArchitectureTest RULE 12），
 * 改用事件解耦。Spring {@code ApplicationEventPublisher.publishEvent(Object)} 自 4.2 起
 * 接受任意对象，事件无需继承 {@code ApplicationEvent}，故用纯 record 保持 domain 纯净。
 *
 * @param entityId    实体 ID。
 * @param entityType  实体类型（如 "CaCertificate"）。
 * @param action      操作动作（如 "UPDATE"）。
 * @param summary     变更摘要（如 "CA类型：ENTITY_CA -> ELECTRONIC_CA；颁发机构：- -> 某机构"）。
 */
public record EntityUpdatedEvent(
        Long entityId,
        String entityType,
        String action,
        String summary
) { }
