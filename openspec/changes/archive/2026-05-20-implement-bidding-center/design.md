# Design: Bidding Center Implementation

## Backend Changes

### 1. Flyway Migration (V1.2)
新增 Tender 表字段:
- `project_type` VARCHAR(20) — 项目类型枚举
- `contact_tel` VARCHAR(50) — 联系人1座机
- `contact_mail` VARCHAR(100) — 联系人1邮箱
- `contact_name2` VARCHAR(100) — 联系人2姓名
- `contact_phone2` VARCHAR(50) — 联系人2手机
- `contact_tel2` VARCHAR(50) — 联系人2座机
- `contact_mail2` VARCHAR(100) — 联系人2邮箱
- `project_manager_id` BIGINT — 项目负责人ID
- `project_manager_name` VARCHAR(100) — 项目负责人姓名
- `bidding_person_id` BIGINT — 投标负责人ID
- `bidding_person_name` VARCHAR(100) — 投标负责人姓名
- `department` VARCHAR(100) — 项目部门
- `distributor_id` BIGINT — 分配人ID
- `distributor_name` VARCHAR(100) — 分配人姓名
- `creator_id` BIGINT — 创建人ID
- `creator_name` VARCHAR(100) — 创建人姓名
- `bid_notice` TEXT — 标讯信息原文
- `bid_notice_file_url` VARCHAR(1000) — 标讯文件URL

### 2. Tender Entity
- 添加上述所有新字段的 JPA 映射
- 添加 `projectType` 枚举: INDUSTRIAL_ECOMMERCE, OFFICE, COMPREHENSIVE, CENTRALIZED, OTHER
- 保持 `searchTextNormalized` 自动更新逻辑

### 3. Deduplication Policy (Pure Core)
- `com.xiyu.bid.tender.domain.TenderDeduplicationPolicy`
- 输入: Tender 四字段 (title, purchaserName, registrationDeadline, bidOpeningTime)
- 输出: Optional<Long> (duplicate tender ID)
- 纯函数, 不依赖任何外部资源

### 4. Audit Log Service
- `com.xiyu.bid.tender.service.TenderAuditService`
- 在 TenderCommandService 和 TenderEvaluationSubmissionService 中织入审计点
- 使用现有 AuditLog 实体

### 5. Export Enhancement
- 扩展 `TenderExportService` 包含全部 17 列蓝图字段
- 导出文件命名: 投标项目列表_YYYYMMDD_HHmmss.xlsx

## Frontend Changes

### 6. TenderTable (列表列扩展)
- 从 5 列扩展至 18 列
- 项目名称列保留为可点击链接
- 新增列: 序号, 来源平台, 总部所在地, 业主单位, 项目类型, 客户类型, 营业收入, 报名截止, 开标时间, 项目负责人, 项目部门, 投标负责人, 优先级, 创建人
- 使用 el-table 原生列，删除内嵌复合列

### 7. TenderSearchCard (筛选扩展)
- 新增: 项目负责人下拉、投标负责人下拉、创建人下拉
- 回调后端支持的筛选参数

### 8. DetailPage (Tab 重构)
- 替换 el-descriptions 为 el-tabs (3 Tab)
- Tab1: 基本信息 — 27 字段完整展示
- Tab2: 项目评估表 — 复用现有 TenderEvaluationForm
- Tab3: 操作日志 — 新增 Timeline 组件
- 底部操作栏固定 (position: sticky)

### 9. ManualTenderDialog → Step Flow
- 新增 `ManualTenderCreate.vue` 分步录入组件
- 步骤一: 基本信息表单 (现有字段 + 新增字段)
- 步骤二: 评估表表单
- 底部 [上一步] / [下一步] / [保存] 按钮
- 保留 ManualTenderDialog 作为快速入口(复指向新组件)

### 10. Deduplication UI
- 在 TenderController.create 中调用去重策略
- 前端: 去重弹窗展示覆盖/新建
- 第三方同步: 静默跳过

### 11. OperationLogTimeline 组件
- 新增 `src/views/Bidding/detail/components/OperationLogTimeline.vue`
- el-timeline 时间轴
- 从 `/api/tenders/{id}/audit-logs` 获取

### 12. SourceConfigDialog 扩展
- 新增: 业务单位下拉、自动匹配后入库复选框、自动去重复选框
