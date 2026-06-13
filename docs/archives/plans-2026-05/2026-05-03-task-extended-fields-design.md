# 任务扩展字段 + 管理页 设计稿

- 日期：2026-05-03
- 范围：N4。第一期（PR #149）设计稿第 5.2 节预留的扩展字段层终于落地
- 背景：系统字段（name/content/owner/deadline/priority/status）只能满足 90% 的任务表达；企业场景里经常需要"招标文件章节号"、"技术分权重"、"关联资质证书"这类**按业务类型配置**的字段——admin 维护一份 schema，TaskForm 自动渲染

---

## 1. 目标

让 ADMIN 可以：
1. 看见一张扩展字段列表（key / label / type / required / 排序）
2. 新增一个字段（例如 key=`tender_chapter`, label=`招标文件章节号`, type=`text`, required=false）
3. 修改 label / 排序 / required 标记
4. 停用字段（软删；task.extended_fields_json 里的历史值保留）
5. 支持字段类型：**text / textarea / number / date / select**（5 种，MVP 够用）

让普通用户：
- TaskForm 抽屉在系统字段下方自动出现"扩展字段"区域
- 扩展字段值持久化到 Task 并随 reload 保留

---

## 2. 核心决策

### 决策 1：schema 全平台共享（方案 ①，同 N3 状态字典）

不做 per-project 或 per-template schema。理由：
- 业务侧当前没有"不同项目用不同字段"的硬需求
- 全平台一份简单直观，改一次所有人看到
- 二期如果需要可以基于 category 做分组

### 决策 2：task 扩展字段存 JSON

task 表新增 `extended_fields_json TEXT NULL` 列。里面存 `{"tender_chapter": "第3章", "tech_weight": 40}` 这种键值对。

**为什么不用纵表**（`task_extended_field_value` 每条一行）：
- 查询场景只有"读整条 task"，没有"按某扩展字段筛选 task"的需求
- JSON 方案代码最简，无需 N+1 查询
- 未来真需要索引某个字段可以加生成列

**为什么不复用 content TEXT**：
- content 是 Markdown 富文本，结构完全不同
- 混在一起会让"富文本编辑器 ↔ 结构化字段 KV"的边界消失

### 决策 3：字段类型 5 种，MVP 够用

| type | 渲染 | 存储形态 |
|---|---|---|
| text | el-input | string |
| textarea | el-input type=textarea rows=3 | string |
| number | el-input-number | number |
| date | el-date-picker value-format=YYYY-MM-DD | string (ISO date) |
| select | el-select with options | string (option value) |

**不做的**：attachment / person / qualification / project（这些在 workflow-form 里复杂度极高，MVP 跳过；需要时再迭代）。

复用 PR #149 的 `DynamicFormRenderer`——它已经支持这 5 种 type。

### 决策 4：字段层级不嵌套

schema 是**平的 key/value 列表**，没有分组/折叠/条件可见性。需求侧当前没有这些诉求，YAGNI。

### 决策 5：key 一旦落库**不允许修改**

只允许改 label / type / required / sort_order / options（for select）/ enabled。

为什么：
- 已有 task 的 `extended_fields_json` 用 key 作为键，rename key 会让历史数据悬空
- 真需要改可以"停用旧字段 + 新建"

### 决策 6：不做版本化 / 发布流

对比 workflow-form-designer 的 draft/version 模式：过度设计。任务扩展字段是内部配置，admin 改完立刻生效，没有"草稿态 → 发布态"的需要。**一张表一行直接改**。

---

## 3. 数据库 schema

### V103（下一个可用版本号）

```sql
-- 任务扩展字段定义（全平台共享的 schema）
CREATE TABLE task_extended_field (
    `key` VARCHAR(64) NOT NULL PRIMARY KEY,
    label VARCHAR(128) NOT NULL,
    field_type VARCHAR(32) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    placeholder VARCHAR(255),
    options_json TEXT,                   -- select 类型时存 [{label,value},…]
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT ck_task_extended_field_type
        CHECK (field_type IN ('text','textarea','number','date','select'))
);

CREATE INDEX idx_task_extended_field_enabled_sort
    ON task_extended_field (enabled, sort_order);

-- task 表加 JSON 列存扩展值
ALTER TABLE tasks
    ADD COLUMN extended_fields_json TEXT NULL
    COMMENT '扩展字段键值对 JSON，schema 在 task_extended_field 表';
```

注意 `key` 是 MySQL 保留字，列名要反引号包。

---

## 4. 后端

### 4.1 Endpoints

**公开读**（authenticated）:
| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/task-extended-fields` | 返回 enabled=true 的字段 schema（task form 渲染用） |

**admin CRUD**（同 N3 模式）:
| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/admin/task-extended-fields` | 列出全部含 disabled |
| POST | `/api/admin/task-extended-fields` | 新增 |
| PUT | `/api/admin/task-extended-fields/{key}` | 更新（key 不可改） |
| PATCH | `/api/admin/task-extended-fields/{key}/disable` | 停用 |
| PATCH | `/api/admin/task-extended-fields/{key}/enable` | 启用 |
| PATCH | `/api/admin/task-extended-fields/reorder` | 批量重排 |

### 4.2 Service invariants

```
create(req):
  1. key 必须 snake_case 数字下划线（regex `^[a-z][a-z0-9_]*$`），长度 ≤ 64
  2. key 在表中不存在
  3. field_type 必须是 5 种之一
  4. 如果 type=select，options 不能空
  5. required=true 时，系统不强制回写历史 task（历史 task 的值仍可为空）
  6. sort_order 不填自动取 max+10

update(key, req):
  1. key 必须存在
  2. field_type 可改，但如果改了类型且 task 表里已有该 key 的历史值：
     - text/textarea/number/date/select 之间互转，做宽容解析（失败丢弃值）
     - 不做"改了会清空历史值"的硬拦截（改 type 本来就低频，历史数据允许有脏值）
  3. options_json 可改（select 类型），减少 option 时**不**清理 task 中的历史值（保留脏值）

disable(key):
  - 没有复杂守卫（不像状态字典有"最后一个 initial/terminal"），直接设 enabled=false

reorder(items):
  - 和状态字典 reorder 一致：batch update，事务原子
```

### 4.3 Task 实体改动

```java
// Task.java
@Column(columnDefinition = "TEXT")
private String extendedFieldsJson;
```

`TaskDTO` 加 `Map<String, Object> extendedFields`（前端更方便）+ service 层做 JSON ↔ Map 序列化。

`TaskService.updateTask` 增加 `if (dto.getExtendedFields() != null) task.setExtendedFieldsJson(toJson(dto.getExtendedFields()))`。

### 4.4 不做的

- 字段级 ACL（某字段只有 ADMIN 能改）— 下期如果需要再加
- 字段级校验规则（正则、min/max）— 前端可以加轻量 `required`，后端不做强校验（毕竟是自定义字段）

---

## 5. 前端

### 5.1 公开 API client

`src/api/modules/taskExtendedField.js`（读）+ `taskExtendedFieldAdmin.js`（写）——分两个模块，权限边界清晰。

### 5.2 Pinia store

加到 `projectStore`（和 task status dict 放一起，都是任务相关主数据）：

```js
state: () => ({
  taskExtendedFields: [],
  taskExtendedFieldsLoaded: false,
  // ...existing
})

actions: {
  async loadTaskExtendedFields() { /* 同 loadTaskStatuses 风格 */ }
  invalidateTaskExtendedFields() { /* admin 改完调，同 invalidateTaskStatuses */ }
}
```

### 5.3 TaskForm 改动

在 system 字段区下方追加扩展字段区。使用 **DynamicFormRenderer**（复用）：

```vue
<template>
  <!-- 系统字段 el-form-item × 6 -->
  <!-- ...不变... -->

  <!-- 扩展字段 -->
  <el-divider v-if="extendedFields.length > 0">扩展字段</el-divider>
  <DynamicFormRenderer
    v-if="extendedFields.length > 0"
    ref="extFormRef"
    :fields="extendedFields"
    v-model="localValue.extendedFields"
  />
</template>
```

TaskForm `submit()` 要把 system 字段 + `extendedFields` map 合并：

```js
function submit() {
  // 原来的校验
  const sysValid = /* ... */
  if (!sysValid) return { valid: false, message }
  
  // 扩展字段校验（DynamicFormRenderer.submit() 会 validate required）
  const extRes = extFormRef.value?.submit()
  if (extRes && extRes.valid === false) {
    return extRes  // 把扩展字段的错误消息透传出去
  }
  
  return { valid: true, data: { ...localValue } }
}
```

数据流：
1. TaskForm mounted → `projectStore.loadTaskExtendedFields()` 和 `loadTaskStatuses()` 并行触发
2. 渲染时 `extendedFields` = `projectStore.taskExtendedFields`（仅 enabled）
3. `localValue.extendedFields` 初始值 = `modelValue.extendedFields ?? {}`

### 5.4 Task 看板卡片

扩展字段**不**在看板卡片上显示（YAGNI；看板位置稀缺，保留给 name/owner/deadline 等 signal）。只有打开抽屉时看得到。

### 5.5 admin 管理面板

`src/views/System/settings/TaskExtendedFieldPanel.vue`，挂 `Settings.vue` 新 tab（和 N3 任务状态字典并列）。

结构和 `TaskStatusDictPanel` 几乎一致：
- el-table 列出全部（含 disabled，灰色）
- 新增/编辑弹窗用 DynamicFormRenderer 渲染字段定义表单
- 上下按钮重排（vuedraggable 依旧 el-table 不好接，保持降级）
- 每次改动后 `projectStore.invalidateTaskExtendedFields()`

### 5.6 编辑表单的"嵌套表单"挑战

admin 新增一个字段时的表单字段（"字段的字段"）：
- key（text）
- label（text）
- field_type（select: text/textarea/number/date/select）
- required（select: 是/否）
- placeholder（text）
- options_json（textarea，仅当 field_type=select 时显示）

**条件可见性**：`options_json` 应该只在选了 select 时才显示。DynamicFormRenderer 当前**不支持条件字段**。

两个选项：
- **A**（MVP）：options 始终显示，admin 知道只有 select 时才填。label 写"仅 select 类型需要，JSON 格式 [{label,value}]"
- **B**：给 DynamicFormRenderer 加 `visibleIf` 字段支持（小改）

**走 A**，admin 改字段是罕见操作，多一个字段不构成体验负担。

---

## 6. 数据迁移 / 兼容性

- 新 column `extended_fields_json` 允许 NULL。历史 task 表里的行全部是 NULL。
- V103 migration 种子数据为空（没有预置扩展字段）。admin 首次进入管理页看到空表，开始自己配置。
- 如果将来要退役某字段：admin 停用即可；task.extended_fields_json 里的历史值保留但不在 UI 显示。

---

## 7. 测试层

| 层 | 内容 |
|---|---|
| 后端 service unit | 12+ case（create/update/disable/reorder invariants，类似 TaskStatusDictAdminServiceTest） |
| 后端 controller unit | 7 case（admin CRUD + public read） |
| 后端 Task.extendedFields 持久化 | 1 case 验证 JSON round-trip |
| 前端 api client spec | 2 文件（reader + admin），共 8 case |
| 前端 store | `loadTaskExtendedFields` + `invalidate` |
| 前端 TaskForm spec | 扩展字段渲染 / 值随 modelValue 回写 / submit 合并 |
| 前端 TaskExtendedFieldPanel spec | 7 case（同 TaskStatusDictPanel 模板） |
| E2E | admin 新增字段 → 切换普通用户 → TaskForm 看到字段并可填/保存/reload 保留 |

---

## 8. 工作量

| Phase | 内容 | 估时 |
|---|---|---|
| A | 后端 V103 migration + entity + DTO + admin/reader controller+service + 测试 | 6h |
| B | 前端 2 个 api client + store ext fields 扩展 + 测试 | 1.5h |
| C | TaskForm 集成扩展字段区（含 submit 合并、测试） | 2h |
| D | TaskExtendedFieldPanel admin UI + mount to Settings + 测试 | 3.5h |
| E | E2E 1 case + 全量门禁 + PR | 1.5h |
| **合计** | | **~14.5h ≈ 2 工作日** |

---

## 9. 不在本期

- 条件可见性（visibleIf）
- attachment / person / qualification / project 字段类型
- 字段级 ACL
- 历史值脏数据的管理页面
- 字段分组 / 折叠 / Tab 分区
- 字段模板库（复制/导入）

---

## 10. 风险

1. **TEXT vs JSON**：用 TEXT 存 JSON 字符串，不用 MySQL JSON 类型 —— 一致性：V101/V102 约定 TEXT 存 64KB，足够扩展字段（key-value 对不超过 30 个的常规场景）。避免 JSON 类型的 MySQL-specific 查询语法。
2. **字段类型改动的脏值**：admin 把 `text` 改成 `number`，历史 task 里该键的值可能是字符串"abc"，不能转 number。前端渲染时用 `?? ''` 兜底，存储原封不动保留。向用户明确"修改字段类型可能导致历史值无法正常显示"。
3. **TaskForm submit 顺序**：要先跑 system 字段校验再跑扩展字段（避免扩展字段校验消息覆盖 name required 消息）—— 按 5.3 的写法即可。

---

## 11. 决策点（需你确认）

- [ ] `extended_fields_json` 用 TEXT（推荐）vs JSON 列 — 推荐 TEXT，避免 MySQL JSON 语法绑定
- [ ] 编辑字段弹窗是否做条件可见性（select 类型才显示 options）vs 始终显示 — 推荐**始终显示 + 提示文案**
- [ ] 字段类型 MVP 5 种（推荐）vs 加 attachment — 推荐**5 种**，attachment 留二期
- [ ] 停用字段时是否清理 task 里的历史值 — 推荐**保留**，停用不破坏历史

"全按推荐" 我直接进实施计划。
