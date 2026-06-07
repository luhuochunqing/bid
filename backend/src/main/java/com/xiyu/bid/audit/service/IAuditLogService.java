// Input: AuditLog 实体、查询条件和事件上下文
// Output: 操作日志记录、全量审计查询与个人操作查询结果
// Pos: Service/业务编排层
// 维护声明: 仅维护日志服务契约；实现变更请同步 Controller 与 Aspect.
package com.xiyu.bid.audit.service;

import com.xiyu.bid.audit.dto.AuditLogQueryResponse;
import com.xiyu.bid.entity.AuditLog;

import java.time.LocalDateTime;

/**
 * 审计/操作日志服务接口
 * 用于支持单元测试中的 Mock
 */
public interface IAuditLogService {

    /**
     * 记录关键操作日志
     * @param entry 日志条目
     */
    void log(AuditLogService.AuditLogEntry entry);

    AuditLog logSync(AuditLogService.AuditLogEntry entry);

    AuditLogQueryResponse queryLogs(String keyword,
                                    String action,
                                    String module,
                                    String operator,
                                    LocalDateTime start,
                                    LocalDateTime end,
                                    Boolean success);

    AuditLogQueryResponse queryMyOperationLogs(String username,
                                               String keyword,
                                               String action,
                                               String module,
                                               LocalDateTime start,
                                               LocalDateTime end,
                                               Boolean success);
}
