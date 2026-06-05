> 一旦我所属的文件夹有所变化，请更新我。

# Approval 模块

审批模块负责审批请求、审批动作和审批统计，是投标流程中的统一审批域。
该目录聚焦审批流编排与状态流转，不直接承担页面展示逻辑。
对外提供审批查询、提交、决策和统计接口。

| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/` | 子目录 | 审批 API 边界 |
| `controller/ApprovalController.java` | Controller | 审批详情、提交、决策和统计接口 |
| `service/` | 子目录 | 审批应用服务边界 |
| `service/ApprovalWorkflowService.java` | Service | 兼容现有接口的 facade |
| `service/ApprovalCommandService.java` | Service | 审批命令编排与状态写入 |
| `service/ApprovalQueryService.java` | Service | 审批查询与读模型组装 |
| `service/ApprovalActionRecorder.java` | Service | 审批动作持久化收口 |
| `service/ApprovalDetailAssembler.java` | Service | 审批详情 DTO 组装 |
| `core/` | 子目录 | 审批纯规则内核 |
| `core/ApprovalDecisionPolicy.java` | Core | 审批状态流转判定 |
| `core/ApprovalPermissionPolicy.java` | Core | 审批权限边界判定 |
| `entity/` | 子目录 | 审批领域实体边界 |
| `entity/ApprovalRequest.java` | Entity | 审批请求聚合实体 |
| `entity/ApprovalAction.java` | Entity | 审批动作记录实体 |
| `repository/` | 子目录 | 审批数据访问边界 |
| `repository/ApprovalRequestRepository.java` | Repository | 审批请求数据访问 |
| `repository/ApprovalActionRepository.java` | Repository | 审批动作数据访问 |
