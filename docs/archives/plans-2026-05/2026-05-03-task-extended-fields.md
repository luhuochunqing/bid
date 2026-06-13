# 任务扩展字段 + 管理页 实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans / subagent-driven-development to execute this plan task-by-task.

**Goal:** 管理员可维护任务的"扩展字段"（key/label/type），TaskForm 自动在系统字段下方渲染 + 持久化。

**Architecture:**
后端新增 `task_extended_field` 主数据表 + `tasks.extended_fields_json TEXT` 列；admin controller 走同 N3 模式 `/api/admin/task-extended-fields`；公开 reader `/api/task-extended-fields` 给 TaskForm 渲染。前端：`TaskExtendedFieldPanel` 挂 Settings（复用 N3 模式）；`TaskForm` 在系统字段下方插一个 `<DynamicFormRenderer>` 区域，`submit()` 合并 system + extended 两段。

**Tech Stack:**
- 后端：Spring Boot / JPA / Flyway (V103) / Jackson
- 前端：Vue 3 / Pinia / Element Plus / Vitest
- 设计稿：`docs/plans/2026-05-03-task-extended-fields-design.md`

**Pre-check:**
1. `pwd` = `/Users/user/xiyu/worktrees/claude`，分支 `agent/claude-init`
2. 同步：`git fetch origin && git rebase origin/main`
3. 冲突检测：`./scripts/who-touches.sh "backend/src/main/java/com/xiyu/bid/task backend/src/main/resources/db/migration-mysql src/stores/project.js src/components/project/TaskForm.vue src/views/System"`
4. 基线：`npm run test:unit && cd backend && mvn -q test -Dtest=ArchitectureTest`

---

## Phase A — 后端

### Task A1：V103 迁移

**Files:**
- Create: `backend/src/main/resources/db/migration-mysql/V103__task_extended_fields.sql`

**Content:**

```sql
-- V103: 任务扩展字段 schema 表 + Task 加扩展字段 JSON 列
-- 设计说明：
--   1) 全平台共享 schema（不做 per-project / per-template）
--   2) task.extended_fields_json 存 {"key":"value", ...}
--   3) key 一旦落库不可改；改 type/label/required/options 都允许
CREATE TABLE task_extended_field (
    `key`         VARCHAR(64)  NOT NULL PRIMARY KEY,
    label         VARCHAR(128) NOT NULL,
    field_type    VARCHAR(32)  NOT NULL,
    required      BOOLEAN      NOT NULL DEFAULT FALSE,
    placeholder   VARCHAR(255),
    options_json  TEXT,
    sort_order    INT          NOT NULL DEFAULT 0,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT ck_task_extended_field_type
        CHECK (field_type IN ('text','textarea','number','date','select'))
);

CREATE INDEX idx_task_extended_field_enabled_sort
    ON task_extended_field (enabled, sort_order);

ALTER TABLE tasks
    ADD COLUMN extended_fields_json TEXT NULL
    COMMENT '扩展字段键值对 JSON, schema 在 task_extended_field 表';
```

**Verify:** 文件唯一（`ls V103*.sql | wc -l == 1`）；启动后端不做（无 MySQL 假设）。

**Commit:**
```
feat(task): V103 task_extended_field table + tasks.extended_fields_json

Schema for admin-maintained extended fields (key/label/type/required/
options/enabled). Values stored as JSON on the tasks row so
round-trip stays single-query.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

### Task A2：Entity + Repository

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/task/entity/TaskExtendedField.java`
- Create: `backend/src/main/java/com/xiyu/bid/task/entity/TaskExtendedFieldType.java` (enum: TEXT, TEXTAREA, NUMBER, DATE, SELECT)
- Create: `backend/src/main/java/com/xiyu/bid/task/repository/TaskExtendedFieldRepository.java`
- Test: `backend/src/test/java/com/xiyu/bid/task/repository/TaskExtendedFieldRepositoryTest.java`
- Modify: `backend/src/main/java/com/xiyu/bid/entity/Task.java`（加 `extendedFieldsJson` 字段）

**TDD:**
- Repo 测试：`findByEnabledTrueOrderBySortOrderAsc()` 返回 enabled 按序
- Entity test：round-trip key（注意 `key` 是 Java 保留字，字段名用 `fieldKey` + `@Column(name = "\`key\`")`）

**Notes:**
- Enum 值用大写 `TEXT/TEXTAREA/NUMBER/DATE/SELECT`，数据库存小写（CHECK 约束是小写） — 所以 `@Enumerated(EnumType.STRING)` 搭配自定义 converter 把 enum name 映射到小写，或者直接把 enum 值写成小写（Java 允许）。**决定：enum 值大写，entity 字段 `fieldType` 设 `@Column`，用 `@Converter` 做小写/大写转换**。或更简单：enum 直接定义为 `text, textarea, number, date, select` 全小写；虽然违反 Java 命名常规，但避免 converter 复杂度。

**Commit:**
```
feat(task): add TaskExtendedField entity + repository

Enum TaskExtendedFieldType with 5 MVP types (text/textarea/number/
date/select) stored as lowercase strings to match the V103 CHECK.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

### Task A3：DTO + public reader service/controller

**Files:**
- Create: `TaskExtendedFieldDTO.java` (record, 公开字段)
- Create: `TaskExtendedFieldService.java` — `listEnabled()`
- Create: `TaskExtendedFieldController.java` — `GET /api/task-extended-fields`（所有登录用户可读）
- Test: `TaskExtendedFieldControllerTest.java`

参考 `TaskStatusDictController` 模式（PR #149）。权限 `hasAnyRole('ADMIN','MANAGER','STAFF')`。

**Commit:**
```
feat(task): expose GET /api/task-extended-fields

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

### Task A4：Admin DTO + service + invariants

**Files:**
- Create: `TaskExtendedFieldAdminDTO.java`
- Create: `TaskExtendedFieldUpsertRequest.java`（`@NotBlank key` `@Pattern(regexp="^[a-z][a-z0-9_]*$")`, `@NotBlank label`, `@NotNull fieldType`）
- Create: `TaskExtendedFieldReorderRequest.java`
- Create: `TaskExtendedFieldAdminService.java`
- Test: `TaskExtendedFieldAdminServiceTest.java`

**Invariants（service 强制）:**
- create: key 不重复，regex 合法，select type 必须有 options，sort_order 不填则 max+10
- update: key 不可改（路径取），type 可改，options 可改
- disable: 无守卫，直接 enabled=false
- reorder: batch 事务

**12 case 测试**（参考 `TaskStatusDictAdminServiceTest`）

**Commit:**
```
feat(task): TaskExtendedFieldAdminService with invariants

- key regex validation, duplicate rejection
- select type requires options
- reorder batch in single transaction
- 12 unit tests

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

### Task A5：Admin controller + ProjectAccessGuard baseline

**Files:**
- Create: `TaskExtendedFieldAdminController.java`
- Test: `TaskExtendedFieldAdminControllerTest.java`
- Check: `backend/src/test/resources/project-access-guard-baseline.txt`（需要加入吗？— M-A3 经验：baseline 只扫描"引用 projectId 的类"；admin 类不包含 projectId，通常不需要加。运行 `ProjectAccessGuardCoverageTest` 看结果）

6 个端点（GET/POST/PUT + 3 个 PATCH 参考 N3）。

**Commit:**
```
feat(task): /api/admin/task-extended-fields CRUD + reorder

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

### Task A6：Task 实体 + service 接扩展字段

**Files:**
- Modify: `Task.java`（加 `extendedFieldsJson` String 字段）
- Modify: `TaskDTO.java`（加 `Map<String, Object> extendedFields`）
- Modify: `TaskService.java`（`updateTask` 处理扩展字段：JSON ↔ Map 序列化用 Jackson `ObjectMapper`）
- Modify: `TaskController.sanitizeTaskDTO`（不处理 extendedFields，保持原值）
- Test: `TaskExtendedFieldsPersistenceTest.java`（round-trip map via updateTask / getTaskById）

**Notes:**
- Jackson `ObjectMapper` 作为 bean 注入 service
- extendedFields 为 null 时 entity 存 null（不存 "{}"）
- 历史 task 没设过扩展字段 → service 返回 empty map 而非 null，前端更好处理

**Commit:**
```
feat(task): Task.extendedFields persisted as JSON

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

## Phase B — 前端基建

### Task B1：两个 API client

**Files:**
- Create: `src/api/modules/taskExtendedField.js`（public: list）
- Create: `src/api/modules/taskExtendedFieldAdmin.js`（CRUD + reorder，6 方法）
- Create: 两个 `.spec.js`
- Modify: `src/api/index.js`

和 `taskStatusDictAdmin.js`（PR #155 M-B1）模式一致。

**Commit:** `feat(api): taskExtendedField reader + admin clients`

---

### Task B2：Store 加 extended fields

**Files:**
- Modify: `src/stores/project.js` — 加 state `taskExtendedFields`/`taskExtendedFieldsLoaded`，action `loadTaskExtendedFields()` / `invalidateTaskExtendedFields()`

模式复刻 `loadTaskStatuses`（PR #149）。

**Commit:** `feat(store): taskExtendedFields state + load/invalidate actions`

---

## Phase C — TaskForm 集成

### Task C1：TaskForm 渲染扩展字段

**Files:**
- Modify: `src/components/project/TaskForm.vue`
- Modify: `src/components/project/TaskForm.spec.js`

**Steps:**
1. TaskForm script setup 加：
   ```js
   const extendedFieldSchema = computed(() =>
     projectStore.taskExtendedFields.filter(f => f.enabled !== false).map(f => ({
       key: f.key,
       label: f.label,
       type: f.fieldType,  // text/textarea/number/date/select
       required: f.required,
       placeholder: f.placeholder,
       options: f.options,  // already parsed JSON from backend
     }))
   )
   const localValue = reactive({ ...props.modelValue })
   if (!localValue.extendedFields) localValue.extendedFields = {}
   ```

2. onMounted 追加：`projectStore.loadTaskExtendedFields()`（不 await，和 status dict 并行）

3. template 追加（system 字段 el-form 之后）:
   ```vue
   <template v-if="extendedFieldSchema.length > 0">
     <el-divider>扩展字段</el-divider>
     <DynamicFormRenderer
       ref="extFormRef"
       :fields="extendedFieldSchema"
       v-model="localValue.extendedFields"
       :disabled="readonly"
     />
   </template>
   ```

4. `submit()` 合并两段校验：
   ```js
   function submit() {
     if (!localValue.name?.trim()) return { valid: false, message: '请填写任务名称' }
     // 系统字段其他校验（如有）
     const extRes = extFormRef.value?.submit?.()
     if (extRes && extRes.valid === false) return extRes
     return { valid: true, data: { ...localValue } }
   }
   ```

5. 测试加 3 case：
   - 没有扩展字段时不渲染 divider
   - 有扩展字段时渲染，填值回显到 `localValue.extendedFields`
   - submit 合并：扩展字段必填未填时 submit 返回 `{valid:false, message: '请填写{label}'}`

**Commit:** `feat(task): TaskForm renders extended fields below system fields`

---

## Phase D — Admin 面板

### Task D1：TaskExtendedFieldPanel.vue + spec

**Files:**
- Create: `src/views/System/settings/TaskExtendedFieldPanel.vue`
- Create: `src/views/System/settings/TaskExtendedFieldPanel.spec.js`

和 `TaskStatusDictPanel.vue`（PR #155 M-C2）模式一致。编辑弹窗表单字段：

```js
const formFields = computed(() => [
  { key: 'key', label: 'Key', type: 'text', required: true,
    placeholder: 'snake_case，例如 tender_chapter', readonly: editingKey.value !== null },
  { key: 'label', label: '显示名', type: 'text', required: true },
  { key: 'fieldType', label: '字段类型', type: 'select', required: true,
    options: [
      { label: '单行文本（text）', value: 'text' },
      { label: '多行文本（textarea）', value: 'textarea' },
      { label: '数字（number）', value: 'number' },
      { label: '日期（date）', value: 'date' },
      { label: '下拉选择（select）', value: 'select' },
    ]},
  { key: 'required', label: '必填', type: 'select',
    options: [{ label: '否', value: false }, { label: '是', value: true }]},
  { key: 'placeholder', label: '占位提示', type: 'text' },
  { key: 'optionsJson', label: 'Options JSON（仅 select 类型）', type: 'textarea',
    placeholder: '[{"label":"高","value":"high"},{"label":"低","value":"low"}]' },
])
```

编辑时：`editingForm.optionsJson = row.options ? JSON.stringify(row.options) : ''`，保存前 `JSON.parse` 回 array。

7 case 测试（复刻 TaskStatusDictPanel.spec.js 结构）。

**Commit:** `feat(admin): TaskExtendedFieldPanel for managing extended fields`

---

### Task D2：挂到 Settings.vue

**Files:**
- Modify: `src/views/System/Settings.vue`
- Modify: `src/views/System/Settings.spec.js`

加 tab `label="任务扩展字段"` name="task-extended-fields"，`v-if="isAdmin"`。

**Commit:** `feat(admin): mount TaskExtendedFieldPanel in System Settings`

---

## Phase E — E2E + 门禁 + PR

### Task E1：E2E case

**Files:**
- Modify: `e2e/task-board-customization.spec.js`

Case: admin 登录 → /settings?tab=task-extended-fields → 新增 `tender_chapter` / "招标文件章节号" / text → 保存 → 切换到 TaskForm 看到字段 → 填值 → 保存 → reload → 值仍在

**Commit:** `test(e2e): admin defines extended field → TaskForm persists it`

---

### Task E2：门禁 + PR

**Run:**
- 前端 4 check + test:unit + build
- 后端 ArchitectureTest + TaskExtendedField* 全套
- push origin agent/claude-init
- gh pr create

---

## 工作量

| Task | 估时 |
|---|---|
| A1 V103 migration | 30m |
| A2 Entity + Repo + Task entity | 1h |
| A3 public reader | 1h |
| A4 admin service + 12 test | 2.5h |
| A5 admin controller + test | 1h |
| A6 Task.extendedFields 持久化 | 1h |
| B1 2 个 api client | 1h |
| B2 store | 30m |
| C1 TaskForm 集成 | 2h |
| D1 admin panel | 3h |
| D2 mount | 30m |
| E1 E2E | 1h |
| E2 门禁 + PR | 1h |
| **合计** | **~15h ≈ 2 工作日** |

**并行可能**：
- Wave 1: A1 / B1 / B2（完全独立）
- Wave 2: A2（依赖 A1）/ 同时 D1 可以起（但需要 B1/B2 完成）
- Wave 3: A3 依赖 A2；A4 依赖 A2；A6 依赖 A2
- Wave 4: A5 依赖 A4；C1 依赖 B1+B2+A3
- Wave 5: D2 依赖 D1+B1；E1 依赖 A5+C1+D2

## 风险

1. **`key` 列是 MySQL 保留字**：V103 中用反引号包裹；entity 用 `@Column(name = "\`key\`")`；Java 字段名改叫 `fieldKey` 避免和关键字冲突
2. **JSON 序列化时机**：用 Jackson `ObjectMapper` bean；service 层 round-trip；前端 DTO 用 `Map<String, Object>`，自动序列化
3. **DynamicFormRenderer 嵌套**：TaskForm 内嵌 DynamicFormRenderer（PR #149 架构允许）；测试 mock 好 DynamicFormRenderer stub
4. **options_json 编辑体验**：textarea 存 JSON 字符串，admin 可能填错格式；保存前 JSON.parse 失败则弹 toast 错误，不保存
