# 任务状态字典管理页 实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans / subagent-driven-development to execute this plan task-by-task.

**Goal:** ADMIN 在系统设置里能新增/编辑/停用/拖拽排序任务状态字典，前端看板/表单立即看到新数据。

**Architecture:**
后端新增 `TaskStatusDictAdminController` (`/api/admin/task-status-dict`) + `TaskStatusDictAdminService`，service 层强制 `is_initial` 唯一性、`category` 枚举、不允许停用最后一个 initial / 最后一个 terminal。前端新增 `TaskStatusDictPanel.vue` 挂在 `Settings.vue` 的新 tab 里，用 vuedraggable 拖拽排序，编辑表单复用 `DynamicFormRenderer`，admin 操作完成后调 `projectStore.invalidateTaskStatuses()`，下次进入页面重新拉。

**Tech Stack:**
- 后端：Spring Boot / JPA / Spring Security `@PreAuthorize`
- 前端：Vue 3 / Pinia / Element Plus / vuedraggable@4 / Vitest
- E2E：Playwright
- 设计稿：`docs/plans/2026-05-02-task-status-dict-admin-design.md`

**Pre-check:**
1. `pwd` = `/Users/user/xiyu/worktrees/claude`，分支 `agent/claude-init`
2. `git fetch origin && git rebase origin/main` 已完成（早操 SOP）
3. `./scripts/who-touches.sh "backend/src/main/java/com/xiyu/bid/task src/views/System src/stores/project.js"` 检查冲突
4. 基线绿：`npm run test:unit && cd backend && mvn -q test -Dtest=ArchitectureTest`

---

## Phase A — 后端 admin endpoints + service

### Task A1：新增 admin DTO（request + response）

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/task/dto/TaskStatusDictAdminDTO.java`
- Create: `backend/src/main/java/com/xiyu/bid/task/dto/TaskStatusDictUpsertRequest.java`
- Create: `backend/src/main/java/com/xiyu/bid/task/dto/TaskStatusDictReorderRequest.java`

**Step 1：DTO record / class**

```java
// TaskStatusDictAdminDTO — admin 接口返回，含审计字段
public record TaskStatusDictAdminDTO(
    String code, String name, String category, String color,
    int sortOrder, boolean isInitial, boolean isTerminal, boolean enabled,
    LocalDateTime createdAt, LocalDateTime updatedAt
) {}

// TaskStatusDictUpsertRequest — POST/PUT body
public class TaskStatusDictUpsertRequest {
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]*$")
    private String code;  // PUT 时来自路径参数；POST 时校验格式
    @NotBlank private String name;
    @NotNull private TaskStatusCategory category;
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$") private String color;  // 默认值 #909399
    private Integer sortOrder;  // null = 自动用 max+10
    private Boolean isInitial;
    private Boolean isTerminal;
    // getters/setters
}

// TaskStatusDictReorderRequest
public class TaskStatusDictReorderRequest {
    @NotEmpty private List<Item> items;
    public record Item(@NotBlank String code, int sortOrder) {}
    // getters/setters
}
```

参考 `TaskStatusDictDTO`（PR #149）的 record 风格；validation 注解参考 sibling DTOs。

**Step 2：commit**

```
feat(task): add admin DTOs for task status dictionary management

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

### Task A2：admin service + invariants

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/task/service/TaskStatusDictAdminService.java`
- Test: `backend/src/test/java/com/xiyu/bid/task/service/TaskStatusDictAdminServiceTest.java`

**Step 1：写失败测试** — 11 个 case 覆盖 invariants：

```java
@DataJpaTest
@Import(TaskStatusDictAdminService.class)
class TaskStatusDictAdminServiceTest {
    @Autowired private TaskStatusDictAdminService service;
    @Autowired private TaskStatusDictRepository repo;

    @BeforeEach
    void seed() {
        // 4 条种子已由 V101 + 测试 fixture 提供（同 PR #149 风格）
    }

    @Test void create_setsAutoSortOrder() { /* 不传 sortOrder，用 max+10 */ }
    @Test void create_rejectsLowercaseCode() { /* code='todo' → 抛 IllegalArgumentException */ }
    @Test void create_rejectsDuplicateCode() { /* code='TODO' 已存在 → 抛 */ }
    @Test void create_settingInitialClearsOtherInitial() {
        // 当前 TODO 是 initial；create 一个新的 initial=true 的字典项；TODO 应自动设为 initial=false
    }
    @Test void update_changesNameAndColor() { /* PUT TODO，修改 name/color，OK */ }
    @Test void update_rejectsCategoryNotInEnum() { /* category='WHATEVER' → 抛 */ }
    @Test void update_settingInitialClearsOtherInitial() { /* 同 create */ }
    @Test void disable_rejectsLastInitial() { /* TODO 是唯一 initial → disable 抛 */ }
    @Test void disable_rejectsLastTerminal() { /* COMPLETED 是唯一 terminal → disable 抛 */ }
    @Test void disable_succeedsWhenAnotherTerminalExists() {
        // 先 create 一个 ARCHIVED（terminal=true），再 disable COMPLETED → 成功
    }
    @Test void reorder_batchUpdatesSortOrder() { /* 传 4 个项目的新 sortOrder，全部生效 */ }
    @Test void reorder_rejectsUnknownCode() { /* code='UNKNOWN_X' → 抛 */ }
}
```

**Step 2：实现 service**

```java
@Service
@RequiredArgsConstructor
@Transactional
public class TaskStatusDictAdminService {
    private final TaskStatusDictRepository repo;

    @Transactional(readOnly = true)
    public List<TaskStatusDictAdminDTO> listAll() {
        return repo.findAll(Sort.by("sortOrder").ascending()).stream()
            .map(this::toDto).toList();
    }

    public TaskStatusDictAdminDTO create(TaskStatusDictUpsertRequest req) {
        validateCode(req.getCode());
        if (repo.existsById(req.getCode())) {
            throw new IllegalArgumentException("Code 已存在: " + req.getCode());
        }
        if (Boolean.TRUE.equals(req.getIsInitial())) {
            clearOtherInitials(req.getCode());
        }
        TaskStatusDict entity = new TaskStatusDict();
        entity.setCode(req.getCode());
        applyFields(entity, req, /*creating=*/ true);
        if (req.getSortOrder() == null) {
            int max = repo.findAll().stream()
                .mapToInt(TaskStatusDict::getSortOrder).max().orElse(0);
            entity.setSortOrder(max + 10);
        }
        entity.setEnabled(true);
        return toDto(repo.save(entity));
    }

    public TaskStatusDictAdminDTO update(String code, TaskStatusDictUpsertRequest req) {
        TaskStatusDict entity = repo.findById(code)
            .orElseThrow(() -> new IllegalArgumentException("字典项不存在: " + code));
        if (Boolean.TRUE.equals(req.getIsInitial()) && !Boolean.TRUE.equals(entity.getIsInitial())) {
            clearOtherInitials(code);
        }
        applyFields(entity, req, /*creating=*/ false);
        return toDto(repo.save(entity));
    }

    public TaskStatusDictAdminDTO disable(String code) {
        TaskStatusDict entity = repo.findById(code)
            .orElseThrow(() -> new IllegalArgumentException("字典项不存在: " + code));
        if (Boolean.TRUE.equals(entity.getIsInitial())) {
            throw new IllegalStateException(
                "不能停用初始状态。请先把另一个字典项设为初始再停用此项。");
        }
        if (Boolean.TRUE.equals(entity.getIsTerminal())) {
            long enabledTerminalsExcludingMe = repo.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getEnabled()))
                .filter(s -> Boolean.TRUE.equals(s.getIsTerminal()))
                .filter(s -> !s.getCode().equals(code))
                .count();
            if (enabledTerminalsExcludingMe == 0) {
                throw new IllegalStateException(
                    "不能停用唯一的终态。请先新增另一个终态字典项。");
            }
        }
        entity.setEnabled(false);
        return toDto(repo.save(entity));
    }

    public TaskStatusDictAdminDTO enable(String code) {
        TaskStatusDict entity = repo.findById(code)
            .orElseThrow(() -> new IllegalArgumentException("字典项不存在: " + code));
        entity.setEnabled(true);
        return toDto(repo.save(entity));
    }

    public void reorder(TaskStatusDictReorderRequest req) {
        Map<String, TaskStatusDict> byCode = repo.findAllById(
            req.getItems().stream().map(TaskStatusDictReorderRequest.Item::code).toList()
        ).stream().collect(Collectors.toMap(TaskStatusDict::getCode, Function.identity()));
        for (var item : req.getItems()) {
            TaskStatusDict entity = byCode.get(item.code());
            if (entity == null) {
                throw new IllegalArgumentException("字典项不存在: " + item.code());
            }
            entity.setSortOrder(item.sortOrder());
        }
        repo.saveAll(byCode.values());
    }

    private void clearOtherInitials(String exceptCode) {
        repo.findAll().stream()
            .filter(s -> Boolean.TRUE.equals(s.getIsInitial()))
            .filter(s -> !s.getCode().equals(exceptCode))
            .forEach(s -> { s.setIsInitial(false); repo.save(s); });
    }

    private void validateCode(String code) {
        if (code == null || !code.matches("^[A-Z][A-Z0-9_]*$")) {
            throw new IllegalArgumentException("Code 必须大写字母开头，仅含大写字母/数字/下划线: " + code);
        }
    }

    private void applyFields(TaskStatusDict entity, TaskStatusDictUpsertRequest req, boolean creating) {
        if (req.getName() != null) entity.setName(req.getName());
        if (req.getCategory() != null) entity.setCategory(req.getCategory());
        if (req.getColor() != null) entity.setColor(req.getColor());
        if (req.getSortOrder() != null) entity.setSortOrder(req.getSortOrder());
        if (req.getIsInitial() != null) entity.setIsInitial(req.getIsInitial());
        if (req.getIsTerminal() != null) entity.setIsTerminal(req.getIsTerminal());
        // enabled 字段在 update 中忽略（用 disable/enable 子路径）
    }

    private TaskStatusDictAdminDTO toDto(TaskStatusDict s) {
        return new TaskStatusDictAdminDTO(
            s.getCode(), s.getName(), s.getCategory().name(), s.getColor(),
            s.getSortOrder(), Boolean.TRUE.equals(s.getIsInitial()),
            Boolean.TRUE.equals(s.getIsTerminal()), Boolean.TRUE.equals(s.getEnabled()),
            s.getCreatedAt(), s.getUpdatedAt()
        );
    }
}
```

**Step 3：跑测试 + ArchitectureTest，commit**

```
feat(task): TaskStatusDictAdminService with invariants

- code regex 校验、唯一性、is_initial 自动清空其他、disable 守卫最后一个 initial/terminal
- reorder 批量事务

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

### Task A3：admin controller + ProjectAccessGuard baseline

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/task/controller/TaskStatusDictAdminController.java`
- Test: `backend/src/test/java/com/xiyu/bid/task/controller/TaskStatusDictAdminControllerTest.java`
- Modify: `backend/src/test/resources/project-access-guard-baseline.txt`

**Step 1：写 controller test**

参照 `TaskStatusDictControllerTest` 的 `excludeFilters` 模式（PR #149 A3 已建立）。验证 6 个端点：
- GET 列出含 disabled
- POST 新建（admin 可，staff 拒）
- PUT 更新
- PATCH /disable
- PATCH /enable
- PATCH /reorder

**Step 2：实现 controller**

```java
@RestController
@RequestMapping("/api/admin/task-status-dict")
@RequiredArgsConstructor
public class TaskStatusDictAdminController {
    private final TaskStatusDictAdminService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "READ", entityType = "TaskStatusDict", description = "列出全部任务状态字典")
    public ResponseEntity<ApiResponse<List<TaskStatusDictAdminDTO>>> list() {
        return ResponseEntity.ok(ApiResponse.success("ok", service.listAll()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "CREATE", entityType = "TaskStatusDict", description = "新增任务状态字典")
    public ResponseEntity<ApiResponse<TaskStatusDictAdminDTO>> create(@Valid @RequestBody TaskStatusDictUpsertRequest req) {
        return ResponseEntity.ok(ApiResponse.success("created", service.create(req)));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "UPDATE", entityType = "TaskStatusDict")
    public ResponseEntity<ApiResponse<TaskStatusDictAdminDTO>> update(
        @PathVariable String code, @Valid @RequestBody TaskStatusDictUpsertRequest req
    ) {
        req.setCode(code);  // 路径优先
        return ResponseEntity.ok(ApiResponse.success("updated", service.update(code, req)));
    }

    @PatchMapping("/{code}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "DISABLE", entityType = "TaskStatusDict")
    public ResponseEntity<ApiResponse<TaskStatusDictAdminDTO>> disable(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success("disabled", service.disable(code)));
    }

    @PatchMapping("/{code}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "ENABLE", entityType = "TaskStatusDict")
    public ResponseEntity<ApiResponse<TaskStatusDictAdminDTO>> enable(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success("enabled", service.enable(code)));
    }

    @PatchMapping("/reorder")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "REORDER", entityType = "TaskStatusDict")
    public ResponseEntity<ApiResponse<Void>> reorder(@Valid @RequestBody TaskStatusDictReorderRequest req) {
        service.reorder(req);
        return ResponseEntity.ok(ApiResponse.success("reordered", null));
    }
}
```

**Step 3：把 controller 加入 ProjectAccessGuard baseline**（admin 类不属于项目作用域）

修改 `backend/src/test/resources/project-access-guard-baseline.txt`，参考 PR #149 J1 加 `TaskStatusDictController` 的方式，追加一行 `TaskStatusDictAdminController`。

**Step 4：跑测试 + commit**

```
feat(task): /api/admin/task-status-dict CRUD + reorder

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

## Phase B — 前端 admin api client + store invalidate

### Task B1：admin api 客户端

**Files:**
- Create: `src/api/modules/taskStatusDictAdmin.js`
- Create: `src/api/modules/taskStatusDictAdmin.spec.js`
- Modify: `src/api/index.js`

**Step 1：6 个方法 + spec**

```js
// taskStatusDictAdmin.js
import httpClient from '../client.js'
const BASE = '/api/admin/task-status-dict'
async function list() { return httpClient.get(BASE) }
async function create(dto) { return httpClient.post(BASE, dto) }
async function update(code, dto) { return httpClient.put(`${BASE}/${code}`, dto) }
async function disable(code) { return httpClient.patch(`${BASE}/${code}/disable`) }
async function enable(code) { return httpClient.patch(`${BASE}/${code}/enable`) }
async function reorder(items) { return httpClient.patch(`${BASE}/reorder`, { items }) }
export const taskStatusDictAdminApi = { list, create, update, disable, enable, reorder }
```

**Step 2：注册到 index.js + commit**

```
feat(api): taskStatusDictAdminApi client

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

### Task B2：projectStore.invalidateTaskStatuses

**Files:**
- Modify: `src/stores/project.js`

**Step 1：加 action**

```js
invalidateTaskStatuses() {
  this.taskStatuses = []
  this.taskStatusesLoaded = false
}
```

**Step 2：commit**

```
feat(store): invalidateTaskStatuses for admin-changed dict

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

## Phase C — 管理面板 UI

### Task C1：安装 vuedraggable@4

**Step:**
```bash
npm install vuedraggable@4
```

确认 package.json 加了一条 dep；package-lock.json 同步更新。

**Commit:**
```
chore(deps): add vuedraggable@4 for sortable rows

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

### Task C2：TaskStatusDictPanel.vue + spec

**Files:**
- Create: `src/views/System/settings/TaskStatusDictPanel.vue`
- Create: `src/views/System/settings/TaskStatusDictPanel.spec.js`

**Step 1：写 spec**（5 case）

- 加载列表（含 disabled 项灰色显示）
- 点"新增"打开弹窗，填表保存调 admin api
- 点"停用"调 disable api，UI 立即更新
- 点"启用"调 enable api
- save 成功后调 `projectStore.invalidateTaskStatuses()`
- 拖拽 / 编辑测试只测 emit + handler，不测真拖

**Step 2：实现组件**

外层结构：

```vue
<template>
  <div class="task-status-dict-panel">
    <div class="panel-header">
      <span>任务状态字典</span>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增状态</el-button>
    </div>
    <el-alert title="拖拽行可改顺序；停用不删除，历史任务保留" type="info" :closable="false" />
    
    <el-table :data="rows" v-loading="loading" row-key="code">
      <el-table-column type="index" label="排序" width="80">
        <template #default="{ $index }">
          <span class="drag-handle">⇅ {{ $index + 1 }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="code" label="Code" width="160" />
      <el-table-column prop="name" label="名称" width="140" />
      <el-table-column prop="category" label="类别" width="140" />
      <el-table-column label="颜色" width="100">
        <template #default="{ row }">
          <span class="color-chip" :style="{ background: row.color }" />
          {{ row.color }}
        </template>
      </el-table-column>
      <el-table-column label="初始" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.isInitial" type="success" size="small">是</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="终态" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.isTerminal" type="warning" size="small">是</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '启用' : '已停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.enabled" link type="danger" @click="onDisable(row)">停用</el-button>
          <el-button v-else link type="success" @click="onEnable(row)">启用</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="540px">
      <DynamicFormRenderer ref="formRef" :fields="formFields" v-model="form" />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
```

`<script setup>` 实现：
- onMounted: `taskStatusDictAdminApi.list()` → rows
- formFields = computed (上面设计稿 4.4 的 schema)
- onSave: 调 create / update → invalidateTaskStatuses → 重新 list → 关弹窗
- onDisable / onEnable: 调对应 api → invalidateTaskStatuses → 更新 row
- 拖拽：用 vuedraggable 包 tbody（el-table 的 tbody slot 不直接接受），**先做最简单版**：在 el-table 外用 vuedraggable 包一层 div + 自渲染表格行。这一版**不在 el-table 上拖**，避免兼容问题；如果用户反馈丑再迭代。

> **方案修正**：`el-table` + vuedraggable 集成确实 trick。实施时**首先尝试** sortablejs 直接绑 el-table 的 `.el-table__body tbody`，如果 ok 就走 sortablejs；不 ok 就降级为上下移动按钮。**预算 30min 试拖拽，超时即降级**。

**Step 3：spec 跑通 + commit**

```
feat(admin): TaskStatusDictPanel for managing task status dictionary

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

### Task C3：挂到 Settings.vue

**Files:**
- Modify: `src/views/System/Settings.vue`

**Step 1：加 tab**

```vue
<el-tab-pane label="任务状态字典" name="task-status-dict" v-if="isAdmin">
  <TaskStatusDictPanel />
</el-tab-pane>
```

`isAdmin` 来自 `useUserStore().userRole === 'ADMIN'`。

**Step 2：commit**

```
feat(admin): expose TaskStatusDictPanel under System Settings tabs

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

## Phase D — E2E 验证

### Task D1：E2E 1 case

**Files:**
- Modify: `e2e/task-board-customization.spec.js`（追加 case）

**Step 1：admin 加 ARCHIVED 状态 → 普通用户视角能选**

```js
test.describe('admin manages task status dict', () => {
  test('admin adds ARCHIVED → staff sees it in TaskForm dropdown', async ({ page }) => {
    // admin 登录
    // ...goto /system/settings, click "任务状态字典" tab
    // ...click 新增, fill code=ARCHIVED, name=已归档, category=CLOSED, color=#c0c4cc
    // ...保存, see new row
    
    // 切换到普通用户登录
    // 进项目详情，点添加任务，drawer 打开，状态下拉应包含"已归档"
  })
})
```

**Step 2：起本地栈跑通 → commit**

如果本地栈起不来（端口占用同上次），承认 spec 已 push、CI 验证。

```
test(e2e): admin add status dict propagates to TaskForm dropdown

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

---

## Phase E — 全量门禁 + PR

### Task E1：门禁 + PR

**Step 1：前端**
```
npm run check:task-status-literal
npm run check:front-data-boundaries
npm run check:doc-governance
npm run check:line-budgets
npm run test:unit
npm run build
```

**Step 2：后端**
```
cd backend && mvn -q test -Dtest=ArchitectureTest,TaskStatusDictAdminServiceTest,TaskStatusDictAdminControllerTest,TaskStatusDictControllerTest,TaskStatusDictRepositoryTest
```

**Step 3：push + PR**

```bash
git push origin HEAD:agent/claude-init
gh pr create --base main --head agent/claude-init \
  --title "feat(task): admin 任务状态字典管理页（新增/编辑/停用/排序）" \
  --body "..."
```

---

## 工作量

| Task | 预估 |
|---|---|
| A1 DTO | 30 分钟 |
| A2 service + 12 case 测试 | 3 小时 |
| A3 controller + baseline | 1.5 小时 |
| B1 admin api client | 30 分钟 |
| B2 store invalidate | 15 分钟 |
| C1 vuedraggable 安装 | 5 分钟 |
| C2 panel + spec | 4 小时（含拖拽实验 0.5h） |
| C3 挂到 Settings | 30 分钟 |
| D1 E2E | 1.5 小时 |
| E1 门禁 + PR | 1 小时 |
| **合计** | **~12.5 小时（约 2 工作日）** |

A1 / B1 / B2 / C1 互相无依赖可并行 Wave 1。A2 依赖 A1。A3 依赖 A2。C2 依赖 B1/B2/C1。C3 依赖 C2。D1 依赖 C3。E1 依赖所有。

---

## 风险

1. **el-table + vuedraggable 兼容**：el-table 的 tbody 是虚拟渲染，sortablejs 绑不上去。预算 30min 试，超时降级为上下移动按钮（plan 设计稿已说明）。
2. **`is_initial` 切换并发**：同时两个 admin 切 initial，最后只剩一个为 true（DB 行级锁保证）— 通过事务 + service 内显式 clear 其他实现。
3. **vuedraggable 是 ES module**：需要确认 vite 配置兼容；现有 sibling deps（element-plus 等）走的就是 ESM，应该 OK。
4. **ProjectAccessGuard baseline 文件**：PR #149 J1 加过类似条目；找一下文件确认实际格式（行级 / 类名）。
