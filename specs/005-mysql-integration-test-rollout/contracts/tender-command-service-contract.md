# Contract: TenderCommandService

**Feature**: 005-mysql-integration-test-rollout
**Date**: 2026-06-30
**Source**: [backend/src/main/java/com/xiyu/bid/tender/service/TenderCommandService.java](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/tender/service/TenderCommandService.java)

## 类级契约

- `@Service` + `@Transactional`（类级事务，所有 public 方法默认事务）
- `@RequiredArgsConstructor`（Lombok 构造器注入，15 个依赖）
- `@Slf4j`（Lombok 日志）
- 多个方法标 `@Auditable`（CREATE / UPDATE / DELETE / LINK_CRM）—— ArchitectureTest RULE 17 白名单成员（已知 `@Transactional` + `@Auditable` 债务）

## 公共方法契约（集成测试覆盖范围）

### `createTender(TenderDTO tenderDTO, Long userId) -> TenderDTO`

**注解**: `@Auditable(action = "CREATE", entityType = "TENDER", description = "录入标讯")`

**输入**: `TenderDTO`（含基本信息 + 附件列表）+ `userId`（创建人 ID，可为 null）
**输出**: 持久化后的 `TenderDTO`（含生成的 `id`）

**前置校验**:
1. `TenderBasicInfoValidator.validateBasicInfo` → 失败抛 `IllegalArgumentException`
2. `validateAttachmentFileUrls` → 附件有 fileName 无 fileUrl 时抛 `BusinessException(400)`

**核心流程**:
1. `resolveCreator` → 回填 `creatorId` / `creatorName`
2. `tenderMapper.toEntity` + `withCommandDefaults`（默认 status=PENDING_ASSIGNMENT、sourceType=MANUAL_SINGLE、publishDate=today、purchaserHash）
3. `tenderDeduplicationService.findDuplicates` → 非空抛 `TenderDuplicateException`（CO-265）
4. `tenderRepository.save(tender)` → DB 落库
5. `saveAttachments` → 附件落库（先删后插）
6. `tryAutoAssign(savedTender)` → 尝试自动分配（失败不影响主流程）
7. `tenderAuditService.logCreate` → 审计日志

**集成测试验证点（FR-008）**:
- `purchaser_hash` UNIQUE 约束：相同 hash 第二次 `createTender` 应抛 `TenderDuplicateException`（CO-265）

---

### `linkCrmOpportunity(Long id, String crmOpportunityId, String crmOpportunityName, TenderEvaluationSubmitRequest evaluationPayload, Long userId) -> TenderDTO`

**注解**: `@Auditable(action = "LINK_CRM", entityType = "TENDER", description = "关联商机")`

**输入**: `id`（标讯 ID）+ `crmOpportunityId` + `crmOpportunityName` + `evaluationPayload`（可选，CO-310 评估表回填）+ `userId`
**输出**: 更新后的 `TenderDTO`

**前置校验**:
1. `tenderRepository.findById` → 不存在抛 `BusinessException(409, "标讯已被删除")`
2. `commandAccessGuard.assertCanUpdateTender` → 权限校验
3. `assertCrmLinkAllowed(status)` → `BIDDING`/`WON`/`LOST`/`ABANDONED` 状态抛 `BusinessException(409)`
4. `crmOccupancyChecker.assertCrmOpportunityNotOccupied(id, crmOpportunityId)` → 应用层占用检查（CO-297 第一层）

**核心流程**:
1. 设置 `crmOpportunityId` / `crmOpportunityName` / `evaluationSource=BID_SYSTEM_LINK`
2. `tenderRepository.save` → 捕获 `DataIntegrityViolationException`，转 409（CO-297 第二层 DB UNIQUE）
3. `assignOnCrmLink(id, userId)` → 写 `DISPATCH` 分配记录（CO-310 两步流程）
4. 可选 `evaluationBackfillService.backfillFromCrmLink`（失败不阻塞）
5. `tenderAuditService.logLinkCrm` → 审计日志

**集成测试验证点（FR-004）**:
- `crm_opportunity_id` 已被其他 Tender 占用 → 抛 409，原 Tender `crm_opportunity_id` 未被覆盖（CO-297 双层防御）

---

### `tryAutoAssign(Tender tender) -> boolean`（private，通过 createTender 间接测试）

**行为**:
1. `autoAssignmentService.autoAssignIfPossible(tender)` → 返回 `AssignmentResult`
2. matched=true：
   - `applyAssignmentResult`（写 projectManagerName / department）
   - `TenderStatusTransitionPolicy.assertTransition(PENDING_ASSIGNMENT, TRACKING)`
   - `tender.setStatus(TRACKING)`
   - `eventPublisher.publishEvent(TenderStatusChangedEvent)`
   - `tenderRepository.save`
   - `tenderAuditService.logAssign`
   - `assignmentNotifier.notifyAutoAssigned`
   - 返回 true
3. matched=false 或抛 RuntimeException：返回 false（catch 不重抛），Tender 保持 `PENDING_ASSIGNMENT`

**集成测试验证点（FR-006）**:
- `autoAssignmentService.autoAssignIfPossible` 抛 RuntimeException → Tender 状态**未**变为 TRACKING，assignee 记录**未**落库

> **注意**: `tryAutoAssign` 是 private 方法，catch 异常后**不重抛**。但 `tryAutoAssign` 在 `createTender` 的 `tenderRepository.save(savedTender)` 之后调用，此时 Tender 已落库（status=PENDING_ASSIGNMENT）。所以"事务回滚"验证的是：`tryAutoAssign` 内部如果触发了 `tenderRepository.save(tender)` 失败（如状态转换断言失败），Tender 状态应保持 `PENDING_ASSIGNMENT`（已落库的版本）。
>
> **修正测试场景**: `tryAutoAssign` 失败时，主流程 `createTender` 不抛异常（catch 吞掉），Tender 已落库为 `PENDING_ASSIGNMENT`，无 DISPATCH 记录。验证点是 Tender 落库 + 状态正确 + 无 assignmentRecord，而非事务回滚。

---

### `assignOnCrmLink(Long tenderId, Long assigneeId)`（private，通过 linkCrmOpportunity 间接测试）

**行为**:
1. `userRepository.findById(assigneeId)` → 不存在抛 `ResourceNotFoundException`
2. 构造 `TenderAssignmentRecord`（type=DISPATCH，assignee=assignee，assignedBy=assignee，remark="CRM商机关联，自动接手评估"）
3. `assignmentRecordRepository.save(record)` → DB 落库

**集成测试验证点（FR-005）**:
- `linkCrmOpportunity` 成功后，`tender_assignment_records` 表有对应 DISPATCH 记录，`assignee_id` / `assignee_name` / `type` 真实落库（跨表事务一致性）

---

### `deleteTender(Long id, Long userId)`

**注解**: `@Auditable(action = "DELETE", entityType = "TENDER", description = "删除标讯")`

**行为**:
1. `tenderRepository.findById` → 不存在抛 `ResourceNotFoundException`
2. `commandAccessGuard.assertCanDeleteTender` → 权限校验
3. `tenderRepository.delete(tender)` → DB 删除（同事务）
4. `tenderAuditService.logDelete` → 审计日志

**集成测试验证点（FR-007）**:
- `deleteTender` 后，`tenders` 和 `tender_attachments` 表均无对应记录（级联事务一致性）
- 注意：`tender_attachments` 的级联删除依赖 JPA entity 关系映射或 DB FK ON DELETE CASCADE，需查 Tender entity 确认

---

### `updateTender(Long id, TenderDTO tenderDTO, Long userId) -> TenderDTO`

**注解**: `@Auditable(action = "UPDATE", entityType = "TENDER", description = "编辑标讯")`

**集成测试不重点覆盖**（既有 Mock 测试已充分），仅在跨表场景需要时补充。

---

### `updateStatus(Long id, Tender.Status targetStatus) -> TenderDTO`

**集成测试不覆盖**（纯状态转换，无跨表事务，Mock 测试已充分）。

## 依赖契约（15 个）

| 依赖 | 类型 | 集成测试处理 | 理由 |
|---|---|---|---|
| `tenderDeduplicationService` | Service | `@Autowired` 真实 | CO-265 重复检测 |
| `tenderRepository` | Repository | `@Autowired` 真实 | 核心 DB 操作 |
| `projectRepository` | Repository | `@Autowired` 真实 | 部分 RPC 路径 |
| `tenderMapper` | Mapper | `@Autowired` 真实 | DTO↔Entity 转换 |
| `accessGuard` | Guard | `@Autowired` 真实 | 权限守卫 |
| `commandAccessGuard` | Guard | `@Autowired` 真实 | 权限守卫 |
| `autoAssignmentService` | Service | `@MockBean` | `tryAutoAssign` 成功/失败场景控制 |
| `eventPublisher` | Spring | `@Autowired` 真实 | 同步事件发布 |
| `userRepository` | Repository | `@Autowired` 真实 | `assignOnCrmLink` 查 User |
| `assignmentNotifier` | Notifier | `@MockBean` | 通知发送非测试关注点 |
| `attachmentRepository` | Repository | `@Autowired` 真实 | `deleteTender` 级联附件 |
| `crmOccupancyChecker` | Checker | `@Autowired` 真实 | CO-297 双层防御核心 |
| `evaluationBackfillService` | Service | `@MockBean` | CO-310 评估表回填非关注点 |
| `projectManagerIdResolver` | Resolver | `@MockBean` | 外部集成 RPC |
| `assignmentRecordRepository` | Repository | `@Autowired` 真实 | `assignOnCrmLink` 跨表写入 |
| `tenderAuditService` | Service | `@Autowired` 真实 | 审计日志写入 |

## 异常契约

| 方法 | 异常类型 | HTTP 状态码 | 触发条件 |
|---|---|---|---|
| `createTender` | `IllegalArgumentException` | 400 | 基本信息校验失败 |
| `createTender` | `BusinessException` | 400 | 附件有 fileName 无 fileUrl |
| `createTender` | `TenderDuplicateException` | 409 | `purchaser_hash` 重复（CO-265） |
| `linkCrmOpportunity` | `BusinessException` | 409 | 标讯不存在 |
| `linkCrmOpportunity` | `BusinessException` | 409 | 状态禁止更换 CRM（BIDDING 等） |
| `linkCrmOpportunity` | `BusinessException` | 409 | CRM 商机被占用（CO-297，应用层 + DB UNIQUE） |
| `linkCrmOpportunity` | `ResourceNotFoundException` | 404 | `assigneeId` 不存在（assignOnCrmLink） |
| `deleteTender` | `ResourceNotFoundException` | 404 | 标讯不存在 |
| `updateStatus` | `IllegalStateTransitionException` | 409 | 状态转换不合法 |

## 事件契约

| 方法 | 事件 | 触发时机 |
|---|---|---|
| `tryAutoAssign` 成功 | `TenderStatusChangedEvent(PENDING_ASSIGNMENT → TRACKING)` | `tenderRepository.save` 后 |
| `updateStatus` | `TenderStatusChangedEvent(previous → target)` | `tenderRepository.save` 后 |

集成测试不验证事件订阅者（`@Autowired` 真实 `eventPublisher` 但不验证下游 listener），仅确保事件发布不抛异常。
