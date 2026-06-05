# 任务看板可定制化 + 任务表单重构 设计稿

- 日期：2026-05-01
- 作者：协作记录（用户 + Claude）
- 背景来源：项目详情"任务看板"状态固定、任务内容过薄无法编辑；需要借鉴流程表单（`DynamicWorkflowForm`）的动态表单能力。
- 关联现状：
  - `src/components/common/TaskBoard.vue:171-176` 硬编码 4 列状态。
  - `src/components/common/DynamicWorkflowForm.vue` 已基于 schema 驱动，配合后端 `workflowform` 模块的版本化模板管理。

---

## 1. 结论速览

1. **不共用**流程表单的数据/提交通路；**只复用**字段渲染层（抽一个 `DynamicFormRenderer`）。
2. 状态字典按 **方案 ①：全平台统一主数据** 建模；业务代码通过 `category` 大类判断，绝不直接比对 `code`。
3. 任务字段分"系统字段（固定）"和"扩展字段（schema）"两层；MVP 先只落系统字段 + 富文本描述。

---

## 2. 为什么不共用流程表单

| 维度 | `workflowForm` | Task |
|---|---|---|
| 生命周期 | 一次性提交 → OA 审批 → 落档 | 长周期，频繁状态流转，可反复编辑 |
| 主体 | 申请单 snapshot | 可变工件 |
| 数据 | 提交后基本不改 | 名称/描述/负责人/截止/交付物/评论都会改 |
| 关系 | 挂在"业务类型"上，和项目弱耦合 | 必须挂在项目下，有看板排序、进度聚合 |
| 后端 | `workflow-forms/instances` + OA 绑定 | 项目任务 + 交付物 + 状态历史 |

强行共用会把任务塞进"一次性申请单"的壳里，丢掉状态流转、进度聚合、交付物绑定等能力；或反过来把 workflowForm 撑胖成通用工作项模型，破坏其现有 OA 对齐语义。

**做法**：抽公共渲染组件 `DynamicFormRenderer`，`DynamicWorkflowForm` 与新建的 `TaskForm` 都基于它组合；附件上传函数通过 prop 注入，不再硬编码走 `workflowFormApi`。

---

## 3. 状态字典建模（方案 ①：全平台统一）

### 3.1 数据模型

新增表 `task_status_dict`（独立于 task 表）：

| 列 | 类型 | 说明 |
|---|---|---|
| `code` | VARCHAR(32) PK | 稳定标识（`TODO / DOING / REVIEW / DONE / REJECTED …`） |
| `name` | VARCHAR(32) | 显示名，管理员可改 |
| `category` | ENUM | `OPEN / IN_PROGRESS / REVIEW / CLOSED` —— **业务语义锚点** |
| `color` | VARCHAR(16) | 看板列配色 |
| `sort_order` | INT | 看板列顺序 |
| `is_initial` | BOOLEAN | 新建任务默认落到哪一列（全表仅 1 条 true） |
| `is_terminal` | BOOLEAN | 是否算"已完成"（影响进度、提交门禁） |
| `enabled` | BOOLEAN | 软删：停用但历史数据保留 |
| audit 字段 | — | created_at / updated_at / updated_by |

### 3.2 `category` 的设计价值（关键）

业务里大量"状态相关的判断"：
- 进度百分比 = 已完成任务数 / 总数
- "提交至标书编写"门禁 = 所有任务处于终态
- 看板列的样式色调（待办灰 / 进行中蓝 / 待审核黄 / 已完成绿）

如果前后端代码里到处 `status === 'done'`、`status === 'review'`，管理员一加新状态，这些地方就全线崩溃。`category` 让业务代码**永远只判断大类**（`category === 'CLOSED'` 代替 `status === 'done'`）。名称和 code 可以自由编辑、停用、新增，不会影响任何业务逻辑。

### 3.3 迁移路径

1. 建表，预置 4 条记录：`TODO/DOING/REVIEW/DONE`，分别绑定四大 category（`OPEN / IN_PROGRESS / REVIEW / CLOSED`）。
2. 任务表 `status` 字段从 ENUM 改成 `VARCHAR(32)` + 外键到 `task_status_dict.code`；老数据按 `todo→TODO / doing→DOING / review→REVIEW / done→DONE` 回填。
3. 前端 `TaskBoard.vue` 的硬编码 `columns` 改为从 `/api/task-status-dict` 拉取。
4. 所有 `status === 'done'` 改为 `statusCategory === 'CLOSED'`（提交门禁、进度百分比、列样式）。

### 3.4 管理员入口（MVP 不含）

MVP 阶段**不做**状态字典管理界面；通过后端迁移脚本维护。后续迭代挂在"系统设置 → 主数据"下，与流程模板管理同级。

---

## 4. 任务表单重构

### 4.1 字段分层

| 层 | 内容 | 是否可自定义 |
|---|---|---|
| **系统字段** | 名称、描述（富文本/长文本）、负责人、截止日期、优先级、状态、交付物 | ❌ 固定，不能删 |
| **扩展字段** | 按业务需要配置，如"招标文件章节号"、"技术分权重"、"关联资质" | ✅ 管理员维护（MVP 不含） |

**为什么不把系统字段也做成 schema**：它们有强业务语义（看板筛选、进度聚合、交付物绑定等），schema 化会让每段业务代码都要先查 schema 找 key，极度脆弱。原则：**可配置不等于全部可配置**。

### 4.2 组件结构

```
       ┌──── DynamicFormRenderer.vue ────┐
       │ (纯渲染: fields[] → Element UI) │
       │  text/textarea/date/number/     │
       │  select/attachment/person/…     │
       │ 附件上传函数通过 prop 注入      │
       └──────────────┬──────────────────┘
                      │ 组合
       ┌──────────────┴──────────────────┐
       │                                 │
  DynamicWorkflowForm.vue          TaskForm.vue (新)
  (走 /api/workflow-forms/…)       (走 /api/projects/{id}/tasks/…)
```

- `DynamicFormRenderer` 从现有 `DynamicWorkflowForm` 抽出字段渲染 + 校验逻辑；附件 upload 通过 `uploadFn` prop 注入。
- `TaskForm.vue`：顶部写死的系统字段区 + 底部 `<DynamicFormRenderer :fields="extendedFields" />`。
- 创建、编辑、查看复用同一组件，`mode="create|edit|view"` 切换只读/可编辑。
- 点任务卡片的交互：从当前"只 emit 事件"改为**打开 TaskForm 抽屉（drawer）**——这是解决"不能写具体内容、不能改"的根本入口。

### 4.3 扩展字段 schema 来源

MVP 阶段**不做任务级 schema 管理页面**。后端提供只读接口 `/api/task-form/schema`，先返回空数组（或硬编码默认 schema）。任务表单先用"系统字段 + 描述富文本"已覆盖绝大多数"能写内容、能改"的诉求。第二迭代再做 schema 管理页。

---

## 5. MVP 最小闭环（范围锁定）

### 后端
1. 新增 `task_status_dict` 表 + 4 条种子数据。
2. 新增 `GET /api/task-status-dict`（列出启用项，按 `sort_order` 排序）。
3. `task` 表新增 `content`（富文本/长文本）字段；`status` 字段类型迁移 + 数据回填。
4. 任务 CRUD 接口支持新字段（`content`、`status` 用字典 code）。
5. 数据库迁移脚本 + `ArchitectureTest` 通过。

### 前端
1. 抽 `DynamicFormRenderer.vue`；原 `DynamicWorkflowForm` 切换到底层组件（保持行为不变，加回归测试）。
2. 新建 `TaskForm.vue`（系统字段 + 富文本描述，暂不含扩展字段）。
3. `TaskBoard.vue`：列从 `/api/task-status-dict` 拉取；下拉菜单的状态切换项按字典动态生成；列样式按 `category` 着色。
4. `ProjectTaskBoardCard.vue` / `useProjectDetailTaskActions.js`：点卡片 = 打开 TaskForm 抽屉；新增/编辑走同一抽屉。
5. 业务判断全部迁移：`status === 'done'` → `statusCategory === 'CLOSED'`。
6. 单测、E2E（创建 → 编辑 → 状态流转 → 进度聚合 → 提交至标书编写）。

### 不在 MVP 范围（下一期）
- 状态字典管理界面
- 任务扩展字段 schema 模型 + 管理页
- 任务评论、历史记录
- 拖拽改状态 / 跨列排序

---

## 6. 风险 & 工程化注意点

1. **数据迁移** `status` ENUM → VARCHAR 必须在一次 Flyway 迁移里完成回填；上线前在测试环境做全量演练，保留回滚脚本。
2. **旧代码扫尾** 全仓库搜索 `status === 'done' / 'review' / 'doing' / 'todo'`；不改干净会出现"显示顺序对了但进度不对"的幽灵 bug。建议加一个 `scripts/check-task-status-literal.js` 治理脚本长期防漏。
3. **`DynamicWorkflowForm` 的附件上传通路** 当前硬编码调 `workflowFormApi.uploadWorkflowFormAttachment`；抽组件时要改成 prop 注入，同时要写回归测试保证流程表单行为不变。
4. **权限** 看板列字典读接口对所有登录用户开放；后续加管理端写接口时必须绑定 `ADMIN` 角色。
5. **富文本存储** `content` 字段建议走 Markdown 或受限 HTML（白名单 sanitize），避免 XSS；与文档编辑器的策略对齐。

---

## 7. 产品化工程化自检

- ✅ **最小代价解决核心诉求**：不动流程表单数据通路，只抽渲染层。
- ✅ **未来可扩展**：`category` 让新增状态零改业务代码；扩展字段层预留。
- ✅ **避免过度设计**：MVP 不做字典管理页、不做任务 schema 管理页（YAGNI）。
- ✅ **迁移路径明确**：种子数据 + 回填脚本 + 治理脚本闭环。
- ✅ **测试覆盖**：`ArchitectureTest` + 单测 + E2E 核心链路。
