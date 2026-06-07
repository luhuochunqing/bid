# Task List: 标书审核流程

**Feature**: 017-document-review-flow
**Branch**: `agent/codex/bidding-document-review-flow`

## Task Dependency Graph

Task 1 (Flyway V migration) → Task 2 (Entity) → Task 3 (Repository) → Task 4 (Pure core policy) → Task 5 (Service implementation) → Task 6 (Notification dispatch) → Task 7 (Frontend API integration) → Task 8 (Flyway U script + verification)

## Tasks

### Task 1: Flyway 正向迁移 V__create_bid_document_review.sql
- **文件**: `backend/src/main/resources/db/migration/V1048__add_bid_document_review.sql`
- **描述**: 创建 `bid_document_review` 表
- **验收**: 表结构包含 project_id(UK)、reviewer_id、submitted_by、status、reject_reason、reviewed_at、created_at、updated_at

### Task 2: 实体 BidDocumentReviewEntity
- **文件**: `backend/src/main/java/com/xiyu/bid/project/entity/BidDocumentReviewEntity.java`
- **描述**: JPA 实体，映射 bid_document_review 表
- **验收**: 使用 `@Table(name = "bid_document_review")`，含 @PrePersist/@PreUpdate 时间戳

### Task 3: Repository BidDocumentReviewRepository
- **文件**: `backend/src/main/java/com/xiyu/bid/project/repository/BidDocumentReviewRepository.java`
- **描述**: JPA Repository，支持 findByProjectId
- **验收**: 返回 Optional<BidDocumentReviewEntity> by projectId

### Task 4: 纯核心 BidReviewPolicy
- **包**: `com.xiyu.bid.project.core`
- **文件**: `BidReviewPolicy.java`
- **描述**: 纯函数策略，校验审核状态流转合法性
- **验收**: 输入当前状态+目标状态→返回 Decision(allowed/reason)；不依赖Spring/JPA；受 FPJavaArchitectureTest 保护

### Task 5: 审核状态枚举（纯核心）
- **包**: `com.xiyu.bid.project.core`
- **文件**: `BidReviewStatus.java`
- **描述**: REVIEWING / APPROVED / REJECTED 枚举
- **验收**: 纯 enum，不依赖任何框架

### Task 6: ProjectDraftingService 审核流程实现
- **文件**: `backend/src/main/java/com/xiyu/bid/project/service/ProjectDraftingService.java`
- **描述**: 实现 submitForReview()、完善 approveBid() 和 rejectBid()、get() 返回审核状态
- **验收**: submitForReview 创建审核记录+发通知；approveBid 更新状态+记录审核时间；rejectBid 更新状态+记录驳回原因

### Task 7: 通知类型 BID_REVIEW
- **文件**: `backend/src/main/java/com/xiyu/bid/notification/core/NotificationType.java`
- **描述**: 新增 `BID_REVIEW` 枚举值
- **验收**: 编译通过

### Task 8: 通知发送（提交审核时发送代办）
- **文件**: `ProjectDraftingService.java` 内 submitForReview 方法
- **描述**: 注入 NotificationApplicationService，提交审核时发送代办给审核人
- **验收**: 代办含项目名称、招标主体、开标时间、负责人信息、投标文件附件链接

### Task 9: ProjectDraftingViewDto 扩展
- **文件**: `backend/src/main/java/com/xiyu/bid/project/dto/ProjectDraftingViewDto.java`
- **描述**: 新增 reviewStatus、reviewerId、reviewerName、rejectReason 字段
- **验收**: 前端可获取审核状态

### Task 10: 前端 DraftingStage.vue 对接真实API
- **文件**: `src/views/Project/stages/DraftingStage.vue`
- **描述**: load() 从后端获取真实审核状态和审核人信息；提交审核/通过/驳回调用真实 API；审核通过后显示"已完成投标"区域
- **验收**: 按钮状态与后端同步；审核流程端到端可跑通

### Task 11: Flyway U 脚本
- **文件**: `backend/src/main/resources/db/migration/U1048__add_bid_document_review.sql`
- **描述**: DROP TABLE IF EXISTS bid_document_review
- **验收**: 含 IF EXISTS

### Task 12: 验证
- **描述**: mvn test (FPJavaArchitectureTest + 相关测试) + npm run build
- **验收**: 编译、架构门禁、相关测试通过
