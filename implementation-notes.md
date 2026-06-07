# Implementation Notes - §3.3.1.3 评标中

## Blueprint Analysis

**Section**: §3.3.1.3 评标中
**Analysis Date**: 2026-06-05
**Status**: ✅ 已完整实现

### Feature Positioning
本页面用于记录投标提交后的评标进展，支持选择评标状态、填写评标情况说明、上传评标文件。投标团队可实时更新评标动态，管理员可全局监督各项目评标状态。

### Data Flow
- **数据来源**: 项目数据由标书制作页提交投标后带入，项目信息为系统自动带出不可编辑
- **上游入口**: 标书制作页 → 提交投标 → 自动转入评标中页
- **下游去向**: 评标结果确认后 → 跳转至结果确认页

### Architecture
评标中功能作为项目生命周期 FSM 的第三个阶段 (EVALUATING) 实现，包含 4 个子阶段和 7 个表单字段。详见 gap-analysis.md。

### Key Components
- **Backend**: ProjectEvaluationController, ProjectEvaluationService, EvaluationSubStage
- **Frontend**: EvaluationStage.vue, EvaluationStatusPanel, EvaluationForm, EvaluationEvidenceUpload, EvaluationSummaryBar
- **DB**: project_evaluation table (4 migrations)

### Key Design Decisions
1. 证据文档通过 ProjectDocument.linkedEntity 机制关联，而非直接使用 evaluation_files_json 列
2. 子阶段支持任意跳转（非强制线性推进）
3. ANNOUNCED 子阶段达到时自动推进到 RESULT_PENDING 阶段
4. 弃标路径同样触发 ANNOUNCED→RESULT_PENDING 推进

---

## Fixes Applied (2026-06-05)

### 1. 开标一览表 — 扩充支持格式
**问题**: 仅支持 PDF/JPG/PNG，缺少蓝图要求的 Word(.doc/.docx) 和 Excel(.xls/.xlsx)
**改动**: `EvaluationEvidenceUpload.vue` — acceptedTypes 增加 `.doc,.docx,.xls,.xlsx`；ALLOWED_MIMES 增加 Word/Excel 的 MIME 类型；提示文字更新
**参考**: 项目内其他阶段（DraftingStage, RetrospectiveStage）已有相同模式

### 2. 评标情况说明 — 替换为富文本编辑器
**问题**: 使用普通 textarea，蓝图要求为"富文本"
**改动**: `EvaluationStage.vue` — 引入 wangEditor (@wangeditor/editor + @wangeditor/editor-for-vue 5.1.12)，替换 el-input textarea
- 工具栏: 精简模式，排除图片/视频/链接/全屏/代码块等不相关功能
- 权限控制: 非经理角色自动禁用编辑器（readOnly + disable/enable 切换）
- 生命周期: mount 时创建、unmount 时 destroy、isManager 变化时切换 enable/disable
- 提交校验: 富文本 HTML 内容先 strip 标签再判空

---

# Implementation Notes - §5. 资源管理

## Blueprint Analysis

**Section**: §5. 资源管理
**Analysis Date**: 2026-06-06
**Status**: ✅ 已完整实现

### Sub-sections
- **5.1 保证金管理** — 已完成（含统计卡片、筛选、完整表格、导出、状态标签）
- **5.2 招标平台账号管理** — 已完成（含 CRUD、借阅审批流、密码加密/审计）
- **5.3 CA 信息管理** — 已完成（含 CA CRUD、借阅工作流、到期提醒定时任务、审计日志）

### 本次实现内容

#### 新增文件
1. `CaExpiryScanService.java` — CA 证书到期及借用逾期扫描服务
   - 纯核心：扫描证书到期情况（即将到期/已过期），借用记录逾期情况（即将到期归还/已逾期）
   - 副作用：通过 AlertHistoryService 写入告警历史
2. `CaExpiryScanTask.java` — CA 扫描定时任务
   - 每天 09:00 执行 CA 证书到期提醒扫描
   - 每天 09:05 执行 CA 借用逾期提醒扫描
3. `resource-ca-full-workflow.spec.js` — CA 管理全流程 E2E 测试
   - CRUD + 借阅/审批/归还 + 驳回 + 筛选 + 权限验证
4. `resource-platform-account-flow.spec.js` — 平台账号管理全流程 E2E 测试
   - CRUD + 借阅/归还 + 密码审计 + 权限验证

#### 修改文件
5. `AlertRule.java` — 新增 `CA_EXPIRY` 和 `CA_BORROW_OVERDUE` 两种告警类型

### Design Decisions
- CA 到期提醒规则与现有的 qualification/performance 到期提醒模式一致，通过 AlertRule + AlertHistory 机制实现
- 定时任务使用 `@Scheduled(cron)` 注解，与项目现有定时任务风格一致
- E2E 测试使用 API 直接验证（无需 UI 交互），确保测试稳定性和可靠性

### Remaining Work
- 企业微信推送集成（依赖外部系统，后续小节覆盖）
- CA 到期提醒规则的 UI 配置页面（可通过"告警规则设置"页面配置）
