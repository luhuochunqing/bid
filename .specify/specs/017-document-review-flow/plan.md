# Implementation Plan: 标书审核流程

**Feature**: 017-document-review-flow
**Branch**: `agent/codex/bidding-document-review-flow`

## Technical Context

### Stack
- **后端**: Java 21 + Spring Boot 3.3 + JPA (Hibernate) + Flyway + MySQL 8.0
- **前端**: Vue 3 + Element Plus + Pinia + Axios
- **通知**: 已有 `NotificationApplicationService` + NotificationType 枚举

### Existing Code
- `ProjectDraftingController` 已有 `submit-review`, `approve`, `reject` 端点映射
- `ProjectDraftingService` 的 `submitForReview()` 未实现，`approveBid`/`rejectBid` 只有骨架
- 前端 `DraftingStage.vue` 已有完整UI（审核人搜索、状态切换、驳回对话框、完成投标复选框）
- API 层 `projectLifecycleApi` 已有 `submitBidForReview`, `approveBid`, `rejectBid` 方法

### Gaps (NEEDS CLARIFICATION → explored)
- 审核状态存储：新表 `bid_document_review`（projectId + reviewerId + status + rejectReason + timestamps）
- 通知类型：新增 `NotificationType.BID_REVIEW`（或复用 `APPROVAL`）
- 审核状态流转：null → REVIEWING → APPROVED / REJECTED → (重新提交) → REVIEWING → ...
- 前端 `bidFiles` 和 `reviewState` 目前只在前端管理，需要对接后端真实数据

### Dependencies
- 通知服务 `NotificationApplicationService.createNotification()` 方法需确认签名
- 用户搜索 API `usersApi.search()` 已存在

## Constitution Check

### Architecture Rules
- ✅ FP-Java Profile: 审核状态计算放入纯核心 domain/core 包
- ✅ Split-First Rule: Application Service 只做编排，Domain Policy 做规则校验
- ✅ 新增 `BidReviewPolicy` 纯核心类，不依赖 Spring/JPA
- ✅ 通知派发放在 Application Service 层（side-effect shell）
- ✅ 数据库迁移必须附带 U 脚本（回滚）

### Field Lock
- ✅ CLOSED 阶段拒绝任何审核操作（复用 `ProjectFieldLockPolicy`）

## Data Model

### bid_document_review (新表)
```sql
CREATE TABLE bid_document_review (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  reviewer_id BIGINT NOT NULL,
  submitted_by BIGINT NOT NULL,         -- 提交审核的用户
  status VARCHAR(20) NOT NULL DEFAULT 'REVIEWING',  -- REVIEWING / APPROVED / REJECTED
  reject_reason VARCHAR(1000),
  reviewed_at DATETIME,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_project (project_id)
);
```

### ProjectDraftingViewDto 新增字段
- reviewStatus: String (null / REVIEWING / APPROVED / REJECTED)
- reviewerId: Long
- reviewerName: String
- rejectReason: String

## Phase Plan

### Phase 1: Backend Core + Entities + Service
1. 新增纯核心 `BidReviewPolicy.java`（验证状态流转合法性）
2. 新增实体 `BidDocumentReviewEntity.java`
3. 新增 Repository
4. 实现 `ProjectDraftingService.submitForReview()` / `approveBid()` / `rejectBid()`
5. 新增通知类型 `BID_REVIEW` + 发送代办通知
6. Flyway V + U 脚本

### Phase 2: Frontend API + State Integration
1. 对接 `getDrafting` 返回真实审核状态
2. 对接 `submitBidForReview` / `approveBid` / `rejectBid`
3. 审核通过后显示"已完成投标"区域

### Verification
- 后端: `mvn test -Dtest=FPJavaArchitectureTest`
- 后端: `mvn test` (相关测试)
- 前端: `npm run build`
