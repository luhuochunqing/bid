# 差距分析 - §3.3.1.3 评标中

**分析时间**: 2026-06-05
**分析范围**: §3.3.1.3 评标中
**分析方法**: 字段级代码验证（后端 API + 前端 UI + 数据库 + 业务流程）

---

## 蓝图要求清单

| # | 功能点 | 类型 | 蓝图描述 |
|---|--------|------|---------|
| 1 | 评标状态选择 | 前端UI | 支持选择评标状态（子阶段），投标团队可实时更新 |
| 2 | 评标情况说明 | 前端UI | 填写评标情况说明（文本区域） |
| 3 | 评标文件上传 | 前端UI | 上传评标文件（PDF/图片） |
| 4 | 项目信息自动带出 | 前端UI | 项目数据由标书制作页提交投标后带入，不可编辑 |
| 5 | 提交投标后自动转入 | 业务流程 | 标书制作页→提交投标→自动转入评标中页 |
| 6 | 评标结果确认后跳转 | 业务流程 | 评标结果确认后→跳转至结果确认页 |
| 7 | 管理员全局监督 | 后端API+权限 | 管理员可全局监督各项目评标状态 |

---

## 逐项验证结果

### #1 评标状态选择

**蓝图要求**: 支持选择评标状态，投标团队可实时更新评标动态

**验证过程**:
- 后端：`EvaluationSubStage` 枚举 4 种状态 `IN_PROGRESS`/`AWAITING_BOARD`/`RESULT_OUT`/`ANNOUNCED` ✅
- 后端：`ProjectEvaluationController.transitionSubStage()` PATCH /sub-stage ✅
- 前端：`EvaluationStatusPanel.vue` 4 个可点击状态标签 ✅
- 权限：`@PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")` ✅

**结论**: 已完整实现 ✅

### #2 评标情况说明

**蓝图要求**: 填写评标情况说明

**验证过程**:
- 后端：`notes` 字段，非空校验 ✅
- 前端：`EvaluationStage.vue` 评标情况说明 textarea，2000字限制 ✅
- 数据库：`project_evaluation.notes TEXT` ✅

**结论**: 已完整实现 ✅

### #3 评标文件上传

**蓝图要求**: 上传评标文件

**验证过程**:
- 后端：`POST /evidence` 关联证据文档 API ✅
- 后端：H6 范式校验（批量收集+校验归属后写入）✅
- 前端：`EvaluationEvidenceUpload.vue` 支持 PDF/JPG/PNG，最多 5 个 10MB ✅
- 数据库：`evaluation_files_json JSON` 列存在（通过 ProjectDocument 关联机制使用）✅

**结论**: 已完整实现 ✅

### #4 项目信息自动带出（不可编辑）

**蓝图要求**: 项目数据由标书制作页提交后自动带入，不可编辑

**验证过程**:
- 前端：`EvaluationSummaryBar.vue` 显示 5 列摘要（项目名称/招标主体/项目负责人/投标负责人/开标时间）✅
- 前端：只读展示，无可编辑字段 ✅
- 数据来源：`loadInitiation()` 从立项信息加载 ✅

**结论**: 已完整实现 ✅

### #5 提交投标后自动转入评标中页

**蓝图要求**: 标书制作页→提交投标→自动转入评标中页

**验证过程**:
- 后端：`ProjectDraftingService.submitBid()` — AllTasksCompletedPolicy 闸门检查 → requestTransition(EVALUATING) ✅
- 前端：`DraftingBidPanel.vue` "提交投标（推进至评标）" 按钮 ✅
- 后端：`ProjectStageService` 首次进入 EVALUATING 时设置 evaluatingAt ✅
- 前端：提交成功后自动导航到评标 tab ✅

**结论**: 已完整实现 ✅

### #6 评标结果确认后跳转至结果确认页

**蓝图要求**: 评标结果确认后→跳转至结果确认页

**验证过程**:
- 后端：子阶段达到 ANNOUNCED 时自动 advanceProjectStageToResultPending() ✅
- 后端：ProjectStageTransitionPolicy 定义 EVALUATING → RESULT_PENDING ✅
- 前端：ProjectDetailMainColumn.vue 存在"结果确认"(RESULT_PENDING) tab，渲染 ResultConfirmStage.vue ✅
- 后端：弃标路径（/abandon）同样推进到 RESULT_PENDING ✅

**结论**: 已完整实现 ✅

### #7 管理员全局监督

**蓝图要求**: 管理员可全局监督各项目评标状态

**验证过程**:
- 前端：项目列表 List.vue 显示 evaluationSubStage 列 ✅
- 前端：搜索筛选包含 EVALUATING 选项 ✅
- 权限：evaluation.update 分配给 manager/bid_admin/bid_lead/bid_specialist/admin_staff ✅
- API 权限：ADMIN/MANAGER/STAFF 均可读取 ✅

**结论**: 已完整实现 ✅

---

## 汇总

| # | 功能点 | 验证结论 | 备注 |
|---|--------|---------|------|
| 1 | 评标状态选择 | ✅ 已完整实现 | 4 子阶段，可点击切换 |
| 2 | 评标情况说明 | ✅ 已完整实现 | 非空校验，2000 字限制 |
| 3 | 评标文件上传 | ✅ 已完整实现 | 5 文件，10MB，PDF/JPG/PNG |
| 4 | 项目信息自动带出 | ✅ 已完整实现 | 5 列只读摘要 |
| 5 | DRAFTING→EVALUATING | ✅ 已完整实现 | AllTasksCompletedPolicy 闸门 |
| 6 | EVALUATING→RESULT_PENDING | ✅ 已完整实现 | ANNOUNCED 自动推进 |
| 7 | 管理员监督 | ✅ 已完整实现 | 列表列 + 权限分配 |

**本次做**: 无 — 所有蓝图功能点均已完整实现 ✅

---

## 观察项（非差距）

1. **子阶段转换策略宽松** — EvaluationStateTransitionPolicy 允许任意跳转（如直接从 IN_PROGRESS 跳到 ANNOUNCED），而非线性推进。但 ProjectStageTransitionPolicy.decideEvaluationSub() 包含线性逻辑未使用。为设计选择，非缺陷。

2. **evaluation_files_json 列未在实体中使用** — V128 迁移添加了此列，但证据关联通过 ProjectDocument.linkedEntity 机制实现，更灵活。当前实现满足功能需求。

3. **状态日志只显示当前状态** — 没有历史转换记录。评估页面单次加载构建的单条目日志。如需完整审计轨迹需额外开发，但目前蓝图未要求。
