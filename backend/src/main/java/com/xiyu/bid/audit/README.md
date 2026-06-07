> 一旦我所属的文件夹有所变化，请更新我。

# 审计/操作日志模块

该模块负责关键操作记录的写入与查询，并区分两个读取视角：
`/api/audit` 是全量审计日志，仅供管理员和审计员查看；
`/api/audit/my` 是个人操作日志，供每个已登录用户查看自己的记录。
底层仍沿用历史 `audit_logs` 表、`/api/audit` 路径前缀与 `audit` 包名，避免引入迁移风险。
纯核心负责判断哪些 action 属于关键操作；应用服务负责上下文采集、异步写入、查询编排和 DTO 转换。
第一版只记录新增、修改、删除和状态流转类关键动作，查询、浏览、列表、搜索等动作在写入源头跳过。

| 文件 | 地位 | 功能 |
|------|------|------|
| `core/` | 子目录 | 纯核心策略 |
| `core/AuditActionPolicy.java` | Core | 判断操作是否需要记录 |
| `service/` | 子目录 | 审计/操作日志应用服务 |
| `service/AuditLogService.java` | Service | 日志 facade，保留原有契约 |
| `service/AuditLogWriter.java` | Service | 日志写入编排 |
| `service/AuditLogQueryService.java` | Service | 全量审计与个人操作日志查询编排 |
| `service/AuditLogItemMapper.java` | Mapper | Entity/User 到列表 DTO 转换 |
| `service/AuditLogMapper.java` | Mapper | 事件命令到实体转换 |
| `service/AuditRequestContextProvider.java` | Adapter | 请求 IP 与 User-Agent 采集 |
| `dto/` | 子目录 | 操作日志 DTO 边界 |
| `dto/AuditLogItemDTO.java` | DTO | 操作日志明细项 |
| `dto/AuditLogQueryResponse.java` | DTO | 操作日志查询响应 |
| `dto/AuditLogSummaryDTO.java` | DTO | 操作日志汇总统计 |
