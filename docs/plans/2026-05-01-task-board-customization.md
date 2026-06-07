# 任务看板可定制化 + 任务表单重构 实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 让任务状态通过全平台统一字典可维护（category 大类解耦业务判断），任务内容可编辑（富文本描述），任务表单与流程表单复用同一套字段渲染层但走独立数据通路。

**Architecture:**
后端新增 `task_status_dict` 主数据表 + category 枚举（OPEN/IN_PROGRESS/REVIEW/CLOSED），Task.status 由 ENUM 迁为 VARCHAR(32) 外键；Task 新增 `content` 富文本字段。前端从 `DynamicWorkflowForm` 抽出 `DynamicFormRenderer`（附件上传函数通过 prop 注入），新建 `TaskForm.vue` 复用渲染层但走 `/api/tasks` 通路。所有 `status === 'done'` 等硬编码判断迁移到 `statusCategory === 'CLOSED'`。

**Tech Stack:**
- 后端：Spring Boot / JPA / Flyway / JUnit / Spring Security
- 前端：Vue 3 Composition API / Element Plus / Pinia / Vitest
- E2E：Playwright
- 设计稿：`docs/plans/2026-05-01-task-board-customization-design.md`

**Pre-check before starting:**
1. 当前工作目录必须是 `/Users/user/xiyu/worktrees/claude`，分支 `agent/claude-init`。
2. 执行 `git fetch origin && git rebase origin/main` 同步基线。
3. 执行 `./scripts/who-touches.sh "src/components/common/TaskBoard.vue src/components/common/DynamicWorkflowForm.vue backend/src/main/java/com/xiyu/bid/task backend/src/main/java/com/xiyu/bid/entity/Task.java backend/src/main/resources/db/migration"`，如有其他 agent 在动这些路径，协调后再开工。
4. 预检：`npm run test:unit`、`cd backend && mvn test -Dtest=ArchitectureTest` 当前应全绿（作为对照基线）。

---

## Phase A — 后端：状态字典 (task_status_dict)

### Task A1：Flyway 迁移 — 建表 + 种子

**Files:**
- Create: `backend/src/main/resources/db/migration-mysql/V101__task_status_dict.sql`

**Step 1：编写迁移 SQL**

```sql
-- V101: 任务状态字典（全平台统一主数据），解耦任务状态的显示与业务判断
-- NOTE: is_initial 的"全表至多一条 true"唯一性由 service 层在写入时校验，
--       因为 MySQL 8 不支持 WHERE 子句的 partial index；生成列 + 唯一索引的
--       变通方案留待"状态字典管理页"上线时再评估，避免现在引入额外复杂度。
CREATE TABLE task_status_dict (
    code         VARCHAR(32)  NOT NULL PRIMARY KEY,
    name         VARCHAR(64)  NOT NULL,
    category     VARCHAR(16)  NOT NULL,
    color        VARCHAR(16)  NOT NULL DEFAULT '#909399',
    sort_order   INT          NOT NULL DEFAULT 0,
    is_initial   BOOLEAN      NOT NULL DEFAULT FALSE,
    is_terminal  BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   BIGINT       NULL,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by   BIGINT       NULL,
    CONSTRAINT ck_task_status_dict_category CHECK (category IN ('OPEN','IN_PROGRESS','REVIEW','CLOSED'))
);

CREATE INDEX idx_task_status_dict_enabled_sort ON task_status_dict (enabled, sort_order);

-- 种子：保持与历史 ENUM 等价的 4 条记录，确保业务语义一致
INSERT INTO task_status_dict (code, name, category, color, sort_order, is_initial, is_terminal, enabled)
VALUES
    ('TODO',        '待办',   'OPEN',        '#909399', 10, TRUE,  FALSE, TRUE),
    ('IN_PROGRESS', '进行中', 'IN_PROGRESS', '#409eff', 20, FALSE, FALSE, TRUE),
    ('REVIEW',      '待审核', 'REVIEW',      '#e6a23c', 30, FALSE, FALSE, TRUE),
    ('COMPLETED',   '已完成', 'CLOSED',      '#67c23a', 40, FALSE, TRUE,  TRUE);
```

> MySQL 8 不支持 WHERE 子句的 partial index；`is_initial` 的"全表至多一条 true"唯一性通过 service 层写入时校验（本 MVP 的既定策略，已在 SQL 头部 NOTE 中说明）。

**Step 2：运行迁移验证**

Run: `cd backend && ./start.sh`（后台启动，看日志出现 `Successfully applied 1 migration to schema`）
停止后台进程。

**Step 3：Commit**

```bash
git add backend/src/main/resources/db/migration-mysql/V101__task_status_dict.sql
git commit -m "feat(task): V101 add task_status_dict table with seed data"
```

---

### Task A2：Entity + Repository

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/task/entity/TaskStatusDict.java`
- Create: `backend/src/main/java/com/xiyu/bid/task/entity/TaskStatusCategory.java` (enum)
- Create: `backend/src/main/java/com/xiyu/bid/task/repository/TaskStatusDictRepository.java`

**Step 1：先写 Repository 的失败测试**

Create `backend/src/test/java/com/xiyu/bid/task/repository/TaskStatusDictRepositoryTest.java`：

```java
@DataJpaTest
@ActiveProfiles("test")
class TaskStatusDictRepositoryTest {
    @Autowired private TaskStatusDictRepository repo;

    @Test
    void findsAllEnabledOrderedBySortOrder() {
        List<TaskStatusDict> list = repo.findByEnabledTrueOrderBySortOrderAsc();
        assertThat(list).extracting(TaskStatusDict::getCode)
            .containsExactly("TODO","IN_PROGRESS","REVIEW","COMPLETED");
    }

    @Test
    void findsInitialStatus() {
        Optional<TaskStatusDict> initial = repo.findByIsInitialTrue();
        assertThat(initial).isPresent();
        assertThat(initial.get().getCode()).isEqualTo("TODO");
    }
}
```

**Step 2：运行测试，确认失败**

Run: `cd backend && mvn test -Dtest=TaskStatusDictRepositoryTest`
Expected: FAIL — 类未定义。

**Step 3：编写 enum**

```java
// backend/src/main/java/com/xiyu/bid/task/entity/TaskStatusCategory.java
package com.xiyu.bid.task.entity;

public enum TaskStatusCategory {
    OPEN, IN_PROGRESS, REVIEW, CLOSED
}
```

**Step 4：编写 Entity**

```java
// backend/src/main/java/com/xiyu/bid/task/entity/TaskStatusDict.java
package com.xiyu.bid.task.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_status_dict")
public class TaskStatusDict {
    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskStatusCategory category;

    @Column(nullable = false, length = 16)
    private String color;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_initial", nullable = false)
    private boolean initial;

    @Column(name = "is_terminal", nullable = false)
    private boolean terminal;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    // getters/setters ...
}
```

（生成所有 getter/setter；IDE 一键生成即可。）

**Step 5：编写 Repository**

```java
// backend/src/main/java/com/xiyu/bid/task/repository/TaskStatusDictRepository.java
package com.xiyu.bid.task.repository;

import com.xiyu.bid.task.entity.TaskStatusDict;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TaskStatusDictRepository extends JpaRepository<TaskStatusDict, String> {
    List<TaskStatusDict> findByEnabledTrueOrderBySortOrderAsc();
    Optional<TaskStatusDict> findByIsInitialTrue();
}
```

**Step 6：再跑测试，确认通过**

Run: `cd backend && mvn test -Dtest=TaskStatusDictRepositoryTest`
Expected: PASS。

**Step 7：跑 ArchitectureTest（新增类必须通过边界规则）**

Run: `cd backend && mvn test -Dtest=ArchitectureTest`
Expected: PASS。

**Step 8：Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/task/entity/TaskStatusDict.java \
        backend/src/main/java/com/xiyu/bid/task/entity/TaskStatusCategory.java \
        backend/src/main/java/com/xiyu/bid/task/repository/TaskStatusDictRepository.java \
        backend/src/test/java/com/xiyu/bid/task/repository/TaskStatusDictRepositoryTest.java
git commit -m "feat(task): add TaskStatusDict entity and repository"
```

---

### Task A3：Service + Controller + DTO

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/task/dto/TaskStatusDictDTO.java`
- Create: `backend/src/main/java/com/xiyu/bid/task/service/TaskStatusDictService.java`
- Create: `backend/src/main/java/com/xiyu/bid/task/controller/TaskStatusDictController.java`
- Test: `backend/src/test/java/com/xiyu/bid/task/controller/TaskStatusDictControllerTest.java`

**Step 1：写失败的 Controller 测试**

```java
@WebMvcTest(TaskStatusDictController.class)
@AutoConfigureMockMvc(addFilters = false)
class TaskStatusDictControllerTest {
    @Autowired MockMvc mvc;
    @MockBean TaskStatusDictService service;

    @Test
    @WithMockUser(roles = "STAFF")
    void listsEnabledStatuses() throws Exception {
        when(service.listEnabled()).thenReturn(List.of(
            new TaskStatusDictDTO("TODO","待办","OPEN","#909399",10,true,false)
        ));
        mvc.perform(get("/api/task-status-dict"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data[0].code").value("TODO"))
           .andExpect(jsonPath("$.data[0].category").value("OPEN"));
    }
}
```

**Step 2：运行并确认失败**

Run: `cd backend && mvn test -Dtest=TaskStatusDictControllerTest`
Expected: FAIL — 类未定义。

**Step 3：写 DTO、Service、Controller（最小实现）**

```java
// TaskStatusDictDTO.java
public record TaskStatusDictDTO(
    String code, String name, String category,
    String color, int sortOrder, boolean initial, boolean terminal
) {}
```

```java
// TaskStatusDictService.java
@Service
public class TaskStatusDictService {
    private final TaskStatusDictRepository repo;
    public TaskStatusDictService(TaskStatusDictRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public List<TaskStatusDictDTO> listEnabled() {
        return repo.findByEnabledTrueOrderBySortOrderAsc().stream()
            .map(s -> new TaskStatusDictDTO(
                s.getCode(), s.getName(), s.getCategory().name(),
                s.getColor(), s.getSortOrder(), s.isInitial(), s.isTerminal()))
            .toList();
    }
}
```

```java
// TaskStatusDictController.java
@RestController
@RequestMapping("/api/task-status-dict")
public class TaskStatusDictController {
    private final TaskStatusDictService service;
    public TaskStatusDictController(TaskStatusDictService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public ResponseEntity<ApiResponse<List<TaskStatusDictDTO>>> list() {
        return ResponseEntity.ok(ApiResponse.success("ok", service.listEnabled()));
    }
}
```

**Step 4：跑测试**

Run: `cd backend && mvn test -Dtest=TaskStatusDictControllerTest`
Expected: PASS。

**Step 5：跑 ArchitectureTest**

Run: `cd backend && mvn test -Dtest=ArchitectureTest`
Expected: PASS。

**Step 6：Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/task/{dto,service,controller}/TaskStatusDict*.java \
        backend/src/test/java/com/xiyu/bid/task/controller/TaskStatusDictControllerTest.java
git commit -m "feat(task): expose GET /api/task-status-dict"
```

---

## Phase B — Task.content 字段 + status 列类型迁移

### Task B1：Flyway 迁移 V102

**Files:**
- Create: `backend/src/main/resources/db/migration-mysql/V102__task_content_and_status_fk.sql`

**Step 1：写迁移 SQL**

```sql
-- V102: Task 新增 content 富文本字段；status 列由 ENUM 扩展为 VARCHAR(32)，回填历史数据
ALTER TABLE task ADD COLUMN content MEDIUMTEXT NULL COMMENT '任务详细描述（富文本/markdown）';

-- 注意：原 task.status 为 VARCHAR（存 ENUM name）或 ENUM，两种都兼容。
-- 这里只保证类型不小于 VARCHAR(32) 即可；若已是兼容类型此语句为 no-op。
ALTER TABLE task MODIFY COLUMN status VARCHAR(32) NOT NULL;

-- 历史数据无需回填：TODO/IN_PROGRESS/REVIEW/COMPLETED 已与字典 code 一致；
-- 但以防个别环境存在 CANCELLED，保留字段可为该值（不加字典外键硬约束，走应用层校验）。
```

**Step 2：运行迁移验证**

Run: 启动后端看日志 `Successfully applied 1 migration to schema`。

**Step 3：Commit**

```bash
git add backend/src/main/resources/db/migration-mysql/V102__task_content_and_status_fk.sql
git commit -m "feat(task): V102 add Task.content and widen status column"
```

---

### Task B2：Task 实体/DTO 加 content 字段

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/entity/Task.java`
- Modify: `backend/src/main/java/com/xiyu/bid/task/dto/TaskDTO.java`
- Modify: `backend/src/main/java/com/xiyu/bid/task/service/TaskService.java`（根据实际类位置）

**Step 1：写失败测试**

```java
// backend/src/test/java/com/xiyu/bid/task/service/TaskContentPersistenceTest.java
@SpringBootTest @ActiveProfiles("test") @Transactional
class TaskContentPersistenceTest {
    @Autowired TaskService service;
    @Autowired TaskRepository repo;

    @Test
    void savesAndReadsRichTextContent() {
        TaskDTO dto = newValidTask();
        dto.setContent("# 任务详情\n- 步骤 1\n- 步骤 2");
        TaskDTO created = service.createTask(dto);
        TaskDTO loaded = service.getTaskById(created.getId());
        assertThat(loaded.getContent()).isEqualTo("# 任务详情\n- 步骤 1\n- 步骤 2");
    }
}
```

`newValidTask()` 参考现有 Task 测试的 fixture（可能是 `TaskServiceTest` 或同包其他测试类）。

**Step 2：运行确认失败**

Run: `cd backend && mvn test -Dtest=TaskContentPersistenceTest`
Expected: FAIL。

**Step 3：改 Entity**

在 `Task.java` 加字段：

```java
@Column(columnDefinition = "MEDIUMTEXT")
private String content;
// + getter/setter
```

**Step 4：改 DTO**

在 `TaskDTO.java` 加 `private String content;` + getter/setter；如果 DTO 在 controller 做 `sanitizeTaskDTO` 需要对富文本做白名单 sanitize（防 XSS）：

```java
private void sanitizeTaskDTO(TaskDTO dto) {
    // 已有字段保持原样...
    if (dto.getContent() != null) {
        dto.setContent(TextSanitizer.sanitizeHtml(dto.getContent()));
    }
}
```

若仓库没有 `TextSanitizer`，新建一个简单包装，内部用 Jsoup 的 `Safelist.relaxed()`（需引入依赖）；或**先用 Markdown 字符串（前端渲染），不做 sanitize**（更安全）。**推荐走 Markdown 路线**，前端用 `marked + DOMPurify` 或 `v-html + sanitize-html` 渲染。

**Step 5：Service / Mapper 映射新字段**

找到 Task ↔ TaskDTO 的 mapper（可能在 `TaskAssembler` 或 service 内 `toDto / fromDto` 方法），加 `content` 的双向映射。

**Step 6：跑测试**

Run: `cd backend && mvn test -Dtest=TaskContentPersistenceTest`
Expected: PASS。

**Step 7：跑受影响测试 + 架构测试**

Run: `cd backend && mvn test -Dtest=TaskServiceTest,TaskControllerTest,TaskContentPersistenceTest,ArchitectureTest`
Expected: PASS。

**Step 8：Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/entity/Task.java \
        backend/src/main/java/com/xiyu/bid/task/dto/TaskDTO.java \
        backend/src/main/java/com/xiyu/bid/task/service/TaskService.java \
        backend/src/test/java/com/xiyu/bid/task/service/TaskContentPersistenceTest.java
git commit -m "feat(task): add content field to Task entity/DTO/service"
```

---

## Phase C — 前端：API client

### Task C1：taskStatusDictApi 客户端

**Files:**
- Create: `src/api/modules/taskStatusDict.js`
- Test: `src/api/modules/taskStatusDict.spec.js`

**Step 1：写失败的测试**

```js
// src/api/modules/taskStatusDict.spec.js
import { describe, it, expect, vi, beforeEach } from 'vitest'
import httpClient from '../client.js'
import { taskStatusDictApi } from './taskStatusDict.js'

vi.mock('../client.js', () => ({ default: { get: vi.fn() } }))

describe('taskStatusDictApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('lists enabled statuses via GET /api/task-status-dict', async () => {
    httpClient.get.mockResolvedValue({ success: true, data: [{ code: 'TODO' }] })
    const result = await taskStatusDictApi.list()
    expect(httpClient.get).toHaveBeenCalledWith('/api/task-status-dict')
    expect(result.data[0].code).toBe('TODO')
  })
})
```

**Step 2：运行确认失败**

Run: `npm run test:unit -- src/api/modules/taskStatusDict.spec.js`
Expected: FAIL — 模块未定义。

**Step 3：最小实现**

```js
// src/api/modules/taskStatusDict.js
// Input: httpClient for task status dictionary requests
// Output: taskStatusDictApi - list enabled task statuses
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

async function list() {
  return httpClient.get('/api/task-status-dict')
}

export const taskStatusDictApi = { list }
```

**Step 4：跑测试**

Run: `npm run test:unit -- src/api/modules/taskStatusDict.spec.js`
Expected: PASS。

**Step 5：注册到 `src/api/index.js`**

```js
// src/api/index.js 找到其他 *Api 导出聚合处，新增
export { taskStatusDictApi } from './modules/taskStatusDict.js'
```

**Step 6：运行数据边界治理脚本**

Run: `npm run check:front-data-boundaries`
Expected: PASS。

**Step 7：Commit**

```bash
git add src/api/modules/taskStatusDict.js src/api/modules/taskStatusDict.spec.js src/api/index.js
git commit -m "feat(api): add taskStatusDictApi client"
```

---

## Phase D — 抽 `DynamicFormRenderer`（保持流程表单行为不变）

### Task D1：写流程表单回归基线测试

**Files:**
- Create: `src/components/common/DynamicWorkflowForm.regression.spec.js`

**Step 1：写基线测试**

覆盖三个关键路径（确保重构不回归）：
1. 给一个 fields schema，应渲染对应输入控件；
2. 填写 required 字段后 `submit()` 返回 `{ valid: true, data }`；
3. 附件上传调用 `workflowFormApi.uploadWorkflowFormAttachment`。

```js
import { mount } from '@vue/test-utils'
import { describe, it, expect, vi } from 'vitest'
import DynamicWorkflowForm from './DynamicWorkflowForm.vue'

vi.mock('@/api/modules/workflowForm.js', () => ({
  workflowFormApi: { uploadWorkflowFormAttachment: vi.fn().mockResolvedValue({ data: { fileName: 'a.pdf', fileUrl: '/x' } }) }
}))

describe('DynamicWorkflowForm regression', () => {
  const schema = { templateCode: 'QUALIFICATION_BORROW', fields: [
    { key: 'name', label: '名称', type: 'text', required: true },
    { key: 'file', label: '附件', type: 'attachment' },
  ]}

  it('renders fields from schema', () => {
    const wrapper = mount(DynamicWorkflowForm, { props: { schema, modelValue: {} } })
    expect(wrapper.find('input').exists()).toBe(true)
  })

  it('submit returns invalid when required missing', () => {
    const wrapper = mount(DynamicWorkflowForm, { props: { schema, modelValue: {} } })
    const { valid, message } = wrapper.vm.submit()
    expect(valid).toBe(false)
    expect(message).toContain('名称')
  })

  it('submit returns data when required filled', async () => {
    const wrapper = mount(DynamicWorkflowForm, { props: { schema, modelValue: { name: 'x' } } })
    const { valid, data } = wrapper.vm.submit()
    expect(valid).toBe(true)
    expect(data.name).toBe('x')
  })
})
```

**Step 2：跑基线，确认当前通过**

Run: `npm run test:unit -- src/components/common/DynamicWorkflowForm.regression.spec.js`
Expected: PASS（重构前先锁基线）。

**Step 3：Commit 基线**

```bash
git add src/components/common/DynamicWorkflowForm.regression.spec.js
git commit -m "test(workflow-form): add regression baseline before extraction"
```

---

### Task D2：创建 DynamicFormRenderer

**Files:**
- Create: `src/components/common/DynamicFormRenderer.vue`
- Test: `src/components/common/DynamicFormRenderer.spec.js`

**Step 1：写失败的测试**

```js
import { mount } from '@vue/test-utils'
import { describe, it, expect, vi } from 'vitest'
import DynamicFormRenderer from './DynamicFormRenderer.vue'

describe('DynamicFormRenderer', () => {
  it('renders all supported field types', () => {
    const fields = [
      { key: 't', label: 'T', type: 'text' },
      { key: 'd', label: 'D', type: 'date' },
      { key: 'n', label: 'N', type: 'number' },
      { key: 's', label: 'S', type: 'select', options: [{ label: 'A', value: 'a' }] },
    ]
    const wrapper = mount(DynamicFormRenderer, { props: { fields, modelValue: {} } })
    expect(wrapper.findAll('.el-form-item').length).toBe(4)
  })

  it('calls injected uploadFn for attachment fields', async () => {
    const uploadFn = vi.fn().mockResolvedValue({ fileName: 'a', fileUrl: '/x' })
    const fields = [{ key: 'f', label: 'F', type: 'attachment' }]
    const wrapper = mount(DynamicFormRenderer, { props: { fields, modelValue: {}, uploadFn } })
    await wrapper.vm.uploadAttachment(fields[0], { file: new File([''], 'a.pdf') })
    expect(uploadFn).toHaveBeenCalled()
  })

  it('validate returns message for missing required field', () => {
    const fields = [{ key: 'a', label: 'A', type: 'text', required: true }]
    const wrapper = mount(DynamicFormRenderer, { props: { fields, modelValue: {} } })
    expect(wrapper.vm.validate()).toContain('A')
  })
})
```

**Step 2：运行确认失败**

Run: `npm run test:unit -- src/components/common/DynamicFormRenderer.spec.js`
Expected: FAIL — 组件未创建。

**Step 3：创建组件（从 DynamicWorkflowForm 拷贝字段渲染逻辑，去掉 workflow 耦合）**

```vue
<!-- src/components/common/DynamicFormRenderer.vue -->
<template>
  <el-form class="dynamic-form-renderer" :model="localValue" label-width="110px">
    <template v-for="field in visibleFields" :key="field.key">
      <el-form-item :label="field.label" :required="field.required">
        <!-- 字段类型分支：从 DynamicWorkflowForm 拷贝 -->
        <!-- text/qualification/project/person/textarea/date/number/select/attachment/info -->
        <!-- 见原文件 line 5-50 -->
      </el-form-item>
    </template>
    <el-alert v-if="validationMessage" type="warning" :closable="false" :title="validationMessage" />
  </el-form>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'

const props = defineProps({
  fields: { type: Array, required: true },
  modelValue: { type: Object, default: () => ({}) },
  uploadFn: { type: Function, default: null },  // (field, request) => Promise<attachment>
  disabled: { type: Boolean, default: false },
})
const emit = defineEmits(['submit', 'update:modelValue'])

const localValue = reactive({ ...props.modelValue })
const validationMessage = ref('')
const visibleFields = computed(() => props.fields.filter(f => !f.hidden))

watch(() => props.modelValue, (v) => {
  Object.keys(localValue).forEach(k => delete localValue[k])
  Object.assign(localValue, v || {})
}, { deep: true })

watch(localValue, () => emit('update:modelValue', { ...localValue }), { deep: true })

function getAttachmentValue(field) {
  const v = localValue[field.key]
  return Array.isArray(v) ? v : []
}

function getAttachmentFileList(field) {
  return getAttachmentValue(field).map((f, i) => ({
    uid: f.storagePath || f.fileUrl || `${field.key}-${i}`,
    name: f.fileName, url: f.fileUrl, status: 'success', response: f,
  }))
}

async function uploadAttachment(field, request) {
  if (!props.uploadFn) throw new Error('uploadFn is required for attachment fields')
  const file = request?.file?.raw || request?.file
  if (!file) return null
  const attachment = await props.uploadFn(field, request)
  localValue[field.key] = [...getAttachmentValue(field), attachment]
  request?.onSuccess?.(attachment)
  return attachment
}

async function handleAttachmentChange(field, file) {
  const rawFile = file?.raw
  if (!rawFile || file?.status === 'success') return
  await uploadAttachment(field, { file: rawFile })
}

function handleAttachmentRemove(field, file = {}) {
  const name = file.name || file.fileName
  const url = file.url || file.fileUrl
  const storagePath = file.response?.storagePath || file.storagePath
  localValue[field.key] = getAttachmentValue(field).filter(i =>
    (storagePath && i.storagePath !== storagePath) ||
    (!storagePath && url && i.fileUrl !== url) ||
    (!storagePath && !url && name && i.fileName !== name)
  )
}

function isEmptyValue(v) {
  if (Array.isArray(v)) return v.length === 0
  return v === null || v === undefined || String(v).trim() === ''
}

function validate() {
  const missing = visibleFields.value.find(f => f.required && f.type !== 'info' && isEmptyValue(localValue[f.key]))
  const msg = missing ? `请填写${missing.label}` : ''
  validationMessage.value = msg
  return msg
}

function submit() {
  const msg = validate()
  if (msg) return { valid: false, message: msg }
  emit('submit', { ...localValue })
  return { valid: true, data: { ...localValue } }
}

defineExpose({ submit, validate, uploadAttachment })
</script>
```

（模板部分的字段类型分支完整照搬 `DynamicWorkflowForm.vue:5-50`，但要把其中 `field.type === 'attachment'` 分支的 upload 属性 `:http-request="(r) => uploadAttachment(field, r)"`、`@change`、`@remove` 原样保留。）

**Step 4：跑测试**

Run: `npm run test:unit -- src/components/common/DynamicFormRenderer.spec.js`
Expected: PASS。

**Step 5：Commit**

```bash
git add src/components/common/DynamicFormRenderer.vue src/components/common/DynamicFormRenderer.spec.js
git commit -m "feat(common): extract DynamicFormRenderer from DynamicWorkflowForm"
```

---

### Task D3：让 DynamicWorkflowForm 委托给 DynamicFormRenderer

**Files:**
- Modify: `src/components/common/DynamicWorkflowForm.vue`

**Step 1：重写 DynamicWorkflowForm 为薄包装**

```vue
<template>
  <DynamicFormRenderer
    ref="renderer"
    :fields="visibleFields"
    v-model="localValue"
    :upload-fn="workflowUpload"
    @submit="(v) => emit('submit', v)"
  />
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import DynamicFormRenderer from './DynamicFormRenderer.vue'
import { workflowFormApi } from '@/api/modules/workflowForm.js'

const props = defineProps({
  schema: { type: Object, required: true },
  modelValue: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['submit', 'update:modelValue'])

const renderer = ref(null)
const localValue = reactive({ ...props.modelValue })
const visibleFields = computed(() => (props.schema?.fields || []).filter(f => !f.hidden))

watch(() => props.modelValue, v => {
  Object.keys(localValue).forEach(k => delete localValue[k])
  Object.assign(localValue, v || {})
}, { deep: true })
watch(localValue, () => emit('update:modelValue', { ...localValue }), { deep: true })

function getTemplateCode() {
  return props.schema?.templateCode || props.schema?.code || props.schema?.workflowType || props.schema?.businessType || 'QUALIFICATION_BORROW'
}
function getProjectId() {
  return props.schema?.projectId ?? localValue.projectId ?? null
}

async function workflowUpload(field, request) {
  const file = request?.file?.raw || request?.file
  const response = await workflowFormApi.uploadWorkflowFormAttachment(
    getTemplateCode(), field.key, file, { projectId: getProjectId() }
  )
  const data = response?.data || response || {}
  return {
    fileName: data.fileName || data.name || file.name || '',
    fileUrl: data.fileUrl || data.url || '',
    storagePath: data.storagePath || '',
    contentType: data.contentType || file.type || '',
    size: data.size ?? file.size ?? 0,
  }
}

function submit() { return renderer.value?.submit() }
function validate() { return renderer.value?.validate() }
defineExpose({ submit, validate })
</script>
```

**Step 2：跑基线回归测试（必须继续通过）**

Run: `npm run test:unit -- src/components/common/DynamicWorkflowForm.regression.spec.js src/components/common/DynamicWorkflowForm.spec.js`
Expected: PASS。

**Step 3：Commit**

```bash
git add src/components/common/DynamicWorkflowForm.vue
git commit -m "refactor(workflow-form): delegate rendering to DynamicFormRenderer"
```

---

## Phase E — TaskForm 组件

### Task E1：TaskForm.vue 最小可用版本

**Files:**
- Create: `src/components/project/TaskForm.vue`
- Test: `src/components/project/TaskForm.spec.js`

**Step 1：写失败测试**

```js
import { mount, flushPromises } from '@vue/test-utils'
import { describe, it, expect, vi } from 'vitest'
import TaskForm from './TaskForm.vue'

vi.mock('@/api/modules/taskStatusDict.js', () => ({
  taskStatusDictApi: {
    list: vi.fn().mockResolvedValue({ data: [
      { code: 'TODO', name: '待办', category: 'OPEN' },
      { code: 'IN_PROGRESS', name: '进行中', category: 'IN_PROGRESS' },
      { code: 'COMPLETED', name: '已完成', category: 'CLOSED' },
    ]})
  }
}))

describe('TaskForm', () => {
  it('renders system fields (name/content/owner/deadline/priority/status)', async () => {
    const wrapper = mount(TaskForm, { props: { mode: 'create', modelValue: {} } })
    await flushPromises()
    expect(wrapper.text()).toContain('任务名称')
    expect(wrapper.text()).toContain('详细描述')
    expect(wrapper.text()).toContain('负责人')
    expect(wrapper.text()).toContain('截止日期')
    expect(wrapper.text()).toContain('优先级')
    expect(wrapper.text()).toContain('状态')
  })

  it('submit returns invalid when name missing', async () => {
    const wrapper = mount(TaskForm, { props: { mode: 'create', modelValue: {} } })
    await flushPromises()
    const r = wrapper.vm.submit()
    expect(r.valid).toBe(false)
  })

  it('submit returns data when required filled', async () => {
    const wrapper = mount(TaskForm, {
      props: { mode: 'create', modelValue: { name: 'X', status: 'TODO' } }
    })
    await flushPromises()
    const r = wrapper.vm.submit()
    expect(r.valid).toBe(true)
    expect(r.data.name).toBe('X')
  })

  it('view mode disables inputs', async () => {
    const wrapper = mount(TaskForm, { props: { mode: 'view', modelValue: { name: 'X' } } })
    await flushPromises()
    expect(wrapper.find('input[disabled]').exists()).toBe(true)
  })
})
```

**Step 2：运行确认失败**

Run: `npm run test:unit -- src/components/project/TaskForm.spec.js`
Expected: FAIL。

**Step 3：创建 TaskForm.vue**

```vue
<template>
  <div class="task-form">
    <el-form :model="localValue" label-width="110px" :disabled="readonly">
      <el-form-item label="任务名称" required>
        <el-input v-model="localValue.name" placeholder="请输入任务名称" />
      </el-form-item>

      <el-form-item label="详细描述">
        <el-input
          v-model="localValue.content"
          type="textarea"
          :rows="6"
          placeholder="支持 Markdown：# 标题、- 列表、**加粗** 等"
        />
      </el-form-item>

      <el-form-item label="负责人">
        <el-input v-model="localValue.owner" />
      </el-form-item>

      <el-form-item label="截止日期">
        <el-date-picker v-model="localValue.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>

      <el-form-item label="优先级">
        <el-select v-model="localValue.priority" style="width: 100%">
          <el-option label="高" value="high" />
          <el-option label="中" value="medium" />
          <el-option label="低" value="low" />
        </el-select>
      </el-form-item>

      <el-form-item label="状态">
        <el-select v-model="localValue.status" style="width: 100%" :loading="loadingStatuses">
          <el-option v-for="s in statuses" :key="s.code" :label="s.name" :value="s.code" />
        </el-select>
      </el-form-item>

      <el-alert v-if="validationMessage" type="warning" :closable="false" :title="validationMessage" />
    </el-form>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch, onMounted } from 'vue'
import { taskStatusDictApi } from '@/api/modules/taskStatusDict.js'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  mode: { type: String, default: 'create' }, // create | edit | view
})
const emit = defineEmits(['submit', 'update:modelValue'])

const localValue = reactive({ ...props.modelValue })
const statuses = ref([])
const loadingStatuses = ref(false)
const validationMessage = ref('')
const readonly = computed(() => props.mode === 'view')

watch(() => props.modelValue, v => {
  Object.keys(localValue).forEach(k => delete localValue[k])
  Object.assign(localValue, v || {})
}, { deep: true })
watch(localValue, () => emit('update:modelValue', { ...localValue }), { deep: true })

onMounted(async () => {
  loadingStatuses.value = true
  try {
    const res = await taskStatusDictApi.list()
    statuses.value = res?.data || []
    if (!localValue.status && statuses.value.length > 0) {
      const initial = statuses.value.find(s => s.initial) || statuses.value[0]
      localValue.status = initial.code
    }
  } finally {
    loadingStatuses.value = false
  }
})

function validate() {
  if (!localValue.name || !localValue.name.trim()) {
    validationMessage.value = '请填写任务名称'
    return validationMessage.value
  }
  validationMessage.value = ''
  return ''
}

function submit() {
  const msg = validate()
  if (msg) return { valid: false, message: msg }
  emit('submit', { ...localValue })
  return { valid: true, data: { ...localValue } }
}

defineExpose({ submit, validate })
</script>
```

**Step 4：跑测试**

Run: `npm run test:unit -- src/components/project/TaskForm.spec.js`
Expected: PASS。

**Step 5：Commit**

```bash
git add src/components/project/TaskForm.vue src/components/project/TaskForm.spec.js
git commit -m "feat(task): add TaskForm with system fields + status dict"
```

---

## Phase F — TaskBoard 改为动态列

### Task F1：TaskBoard 列从字典拉取，样式按 category

**Files:**
- Modify: `src/components/common/TaskBoard.vue`
- Test: `src/components/common/TaskBoard.spec.js`（可能已存在，追加 case）

**Step 1：写失败测试**

```js
import { mount, flushPromises } from '@vue/test-utils'
import { describe, it, expect, vi } from 'vitest'
import TaskBoard from './TaskBoard.vue'

vi.mock('@/api/modules/taskStatusDict.js', () => ({
  taskStatusDictApi: { list: vi.fn().mockResolvedValue({ data: [
    { code: 'TODO', name: '待办', category: 'OPEN', color: '#909399', sortOrder: 10, terminal: false },
    { code: 'IN_PROGRESS', name: '进行中', category: 'IN_PROGRESS', color: '#409eff', sortOrder: 20, terminal: false },
    { code: 'REVIEW', name: '待审核', category: 'REVIEW', color: '#e6a23c', sortOrder: 30, terminal: false },
    { code: 'COMPLETED', name: '已完成', category: 'CLOSED', color: '#67c23a', sortOrder: 40, terminal: true },
    { code: 'REJECTED', name: '已驳回', category: 'OPEN', color: '#f56c6c', sortOrder: 50, terminal: false },
  ]})}
}))

describe('TaskBoard dynamic columns', () => {
  it('renders a column per enabled status', async () => {
    const wrapper = mount(TaskBoard, { props: { tasks: [] } })
    await flushPromises()
    expect(wrapper.findAll('.board-column').length).toBe(5)
  })

  it('computes progress using terminal flag, not the string "done"', async () => {
    const wrapper = mount(TaskBoard, { props: { tasks: [
      { id: 1, status: 'COMPLETED' },
      { id: 2, status: 'IN_PROGRESS' },
    ]}})
    await flushPromises()
    expect(wrapper.text()).toContain('50%')
  })
})
```

**Step 2：运行确认失败**

Run: `npm run test:unit -- src/components/common/TaskBoard.spec.js`
Expected: FAIL。

**Step 3：改写 TaskBoard.vue**

- 删除硬编码 `columns`，改为 `ref([])`，在 `onMounted` 中调用 `taskStatusDictApi.list()` 填充。
- 删除下拉菜单的四个硬编码 `<el-dropdown-item>`，改为 `v-for="s in statuses"` 动态生成。
- `progress` 和 `allTasksCompleted` 改为：

```js
const progress = computed(() => {
  if (props.tasks.length === 0) return 0
  const terminalCodes = new Set(statuses.value.filter(s => s.terminal).map(s => s.code))
  const done = props.tasks.filter(t => terminalCodes.has(t.status)).length
  return Math.round((done / props.tasks.length) * 100)
})
const allTasksCompleted = computed(() => {
  const terminalCodes = new Set(statuses.value.filter(s => s.terminal).map(s => s.code))
  return props.tasks.length > 0 && props.tasks.every(t => terminalCodes.has(t.status))
})
```

- 列样式改用内联 `:style="{ background: column.color + '22', color: column.color }"`（或保留 class，基于 category 生成）。
- 任务对后端 `status` 的 code 直接存在（`TODO/IN_PROGRESS/REVIEW/COMPLETED`），前端**不再走** `project-utils.js` 的小写映射。

**Step 4：同时改 `project-utils.js`**

`TASK_STATUS_TO_API` / `TASK_STATUS_FROM_API` 改为 identity（短期兼容），并在后续任务里彻底移除调用方。

```js
// 注释：历史前端小写状态已退役；保留身份转换避免破坏调用点
const TASK_STATUS_TO_API = { todo: 'TODO', doing: 'IN_PROGRESS', review: 'REVIEW', done: 'COMPLETED', cancelled: 'CANCELLED' }
const TASK_STATUS_FROM_API = { TODO: 'TODO', IN_PROGRESS: 'IN_PROGRESS', REVIEW: 'REVIEW', COMPLETED: 'COMPLETED', CANCELLED: 'CANCELLED' }
```

**Step 5：跑测试**

Run: `npm run test:unit -- src/components/common/TaskBoard.spec.js`
Expected: PASS。

**Step 6：Commit**

```bash
git add src/components/common/TaskBoard.vue src/components/common/TaskBoard.spec.js src/views/Project/project-utils.js
git commit -m "feat(task-board): dynamic columns from status dict + terminal-based progress"
```

---

## Phase G — 清理硬编码 status 字面量 + 治理脚本

### Task G1：替换所有 `status === 'done'` 为基于字典

**Files:**
- Modify: `src/composables/projectDetail/useProjectDetailTaskActions.js:193`（`allCompleted` 判断）
- Modify: `src/stores/project.js:210`（`doneCount` 计算）

**Step 1：改写判断逻辑**

两处都改为：

```js
// 从 taskStatusDictApi 读取一次并缓存（放 store 或 composable），判断：
const terminalCodes = new Set(statuses.filter(s => s.terminal).map(s => s.code))
const allCompleted = tasks.every(t => terminalCodes.has(t.status))
```

实际上为避免多处重复取字典，建议在 `projectStore` 里新增 `taskStatuses` state + `loadTaskStatuses()` action，所有地方复用。

**Step 2：写测试验证**

```js
// src/composables/projectDetail/useProjectDetailTaskActions.spec.js 追加 case
it('allCompleted uses terminal category, not the string "done"', async () => {
  // 给 store 注入 terminal = ['COMPLETED', 'ARCHIVED'] 的字典
  // 验证含 ARCHIVED 的任务也算完成
})
```

**Step 3：跑测试**

Run: `npm run test:unit -- src/composables/projectDetail/useProjectDetailTaskActions.spec.js`
Expected: PASS。

**Step 4：Commit**

```bash
git add src/stores/project.js src/composables/projectDetail/useProjectDetailTaskActions.js src/composables/projectDetail/useProjectDetailTaskActions.spec.js
git commit -m "refactor(task): replace status literal checks with terminal-based logic"
```

---

### Task G2：加治理脚本 check-task-status-literal.js

**Files:**
- Create: `scripts/check-task-status-literal.js`
- Modify: `package.json`（加 script）

**Step 1：写脚本**

```js
#!/usr/bin/env node
// 扫描前端源码中硬编码的任务状态字面量比较，防止"新增状态后幽灵 bug"
import fs from 'node:fs'
import path from 'node:path'

const ROOTS = ['src']
const FORBIDDEN = [
  /status\s*===\s*['"](todo|doing|review|done)['"]/,
  /status:\s*['"](todo|doing|review|done)['"]/,
]
const SKIP_FILES = new Set([
  'src/views/Project/project-utils.js',  // identity 映射表允许
])

function* walk(dir) {
  for (const f of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, f.name)
    if (f.isDirectory()) yield* walk(full)
    else if (/\.(vue|js|ts)$/.test(f.name)) yield full
  }
}

let violations = 0
for (const root of ROOTS) {
  for (const file of walk(root)) {
    if (SKIP_FILES.has(file)) continue
    const src = fs.readFileSync(file, 'utf8')
    for (const pattern of FORBIDDEN) {
      if (pattern.test(src)) {
        console.error(`[task-status-literal] ${file}: matches ${pattern}`)
        violations++
      }
    }
  }
}
if (violations > 0) {
  console.error(`\n禁止硬编码任务状态字面量。改用 statusCategory === 'CLOSED' 或字典驱动。`)
  process.exit(1)
}
```

**Step 2：package.json 加 script**

```json
"check:task-status-literal": "node scripts/check-task-status-literal.js"
```

**Step 3：跑脚本**

Run: `npm run check:task-status-literal`
Expected: PASS（违规都在 G1 清理过了）。

**Step 4：Commit**

```bash
git add scripts/check-task-status-literal.js package.json
git commit -m "chore(task): add governance script to prevent status string literals"
```

---

## Phase H — TaskForm 接入项目详情（点卡片打开抽屉）

### Task H1：ProjectTaskBoardCard 接入 TaskForm Drawer

**Files:**
- Modify: `src/components/project/ProjectTaskBoardCard.vue`
- Modify: `src/composables/projectDetail/useProjectDetailTaskActions.js`

**Step 1：写失败测试**

```js
// src/components/project/ProjectTaskBoardCard.spec.js
it('opens TaskForm drawer when task card clicked', async () => {
  const wrapper = mount(ProjectTaskBoardCard, { props: { tasks: [{ id: 1, name: 'T' }], canManageProjectTasks: true }})
  await wrapper.find('.task-card').trigger('click')
  expect(wrapper.find('.el-drawer').exists()).toBe(true)
  expect(wrapper.findComponent({ name: 'TaskForm' }).exists()).toBe(true)
})

it('opens create drawer on add-task click', async () => {
  const wrapper = mount(ProjectTaskBoardCard, { props: { tasks: [], canManageProjectTasks: true }})
  await wrapper.find('[data-test="add-task"]').trigger('click')
  expect(wrapper.find('.el-drawer').exists()).toBe(true)
})
```

**Step 2：改写 ProjectTaskBoardCard.vue**

- 在模板底部加 `<el-drawer v-model="drawerVisible" :title="drawerTitle"><TaskForm ref="taskForm" v-model="editing" :mode="mode"/></el-drawer>`
- `task-click` 事件 → `openDrawer('edit', task)`
- `add-task` 事件 → `openDrawer('create', {})`
- 抽屉底部按钮点击 `taskForm.value.submit()`，valid 时调 store 的 `addTask / updateTask`。

**Step 3：跑测试**

Run: `npm run test:unit -- src/components/project/ProjectTaskBoardCard.spec.js`
Expected: PASS。

**Step 4：Commit**

```bash
git add src/components/project/ProjectTaskBoardCard.vue src/components/project/ProjectTaskBoardCard.spec.js src/composables/projectDetail/useProjectDetailTaskActions.js
git commit -m "feat(task): open TaskForm drawer from project task board"
```

---

## Phase I — E2E

### Task I1：E2E 覆盖核心链路

**Files:**
- Create: `e2e/task-board-customization.spec.ts`

**Step 1：写 E2E**

```ts
// e2e/task-board-customization.spec.ts
import { test, expect } from '@playwright/test'

test('项目详情：创建任务 → 编辑内容 → 切换状态 → 进度更新', async ({ page }) => {
  await page.goto('/login')
  await page.fill('input[placeholder*="用户名"]', 'lizong')
  await page.fill('input[placeholder*="密码"]', '123456')
  await page.click('button:has-text("登录")')
  await page.waitForURL(/\/workbench|\/projects/)

  // 进入任一 e2e 项目的详情
  // ...
  // 创建
  await page.click('button:has-text("添加任务")')
  await page.fill('input[placeholder*="任务名称"]', 'E2E 测试任务')
  await page.fill('textarea[placeholder*="Markdown"]', '## 步骤\n- 研究\n- 实现')
  await page.click('.el-drawer button:has-text("保存")')
  await expect(page.locator('.task-card:has-text("E2E 测试任务")')).toBeVisible()

  // 编辑
  await page.click('.task-card:has-text("E2E 测试任务")')
  await expect(page.locator('textarea')).toContainText('步骤')

  // 切换状态到 COMPLETED
  await page.click('.task-card:has-text("E2E 测试任务") .more-icon')
  await page.click('.el-dropdown-item:has-text("已完成")')
  // 进度应包含"100%"或更新
})
```

**Step 2：运行 E2E**

Run: `npm run test:e2e -- task-board-customization`
Expected: PASS（若失败，调试定位符）。

**Step 3：Commit**

```bash
git add e2e/task-board-customization.spec.ts
git commit -m "test(e2e): task board customization core flow"
```

---

## Phase J — 最终验证

### Task J1：全量验证门禁

**Step 1：前端验证**

Run:
```bash
npm run check:front-data-boundaries
npm run check:doc-governance
npm run check:line-budgets
npm run check:task-status-literal
npm run build
npm run test:unit
npm run test:e2e
```
Expected: 每一条都 PASS。

**Step 2：后端验证**

Run:
```bash
cd backend
mvn test -Dtest=TaskStatusDictRepositoryTest,TaskStatusDictControllerTest,TaskContentPersistenceTest,TaskServiceTest,TaskControllerTest
mvn test -Dtest=ArchitectureTest
mvn test
```
Expected: 全 PASS。

**Step 3：人工烟雾测试**

Run: `npm run dev:all`
- 以 `lizong/123456` 登录
- 打开任一项目详情，看到任务看板 4 列正常
- 点"添加任务"→ 抽屉弹出 → 填名称/描述/负责人/截止日期/优先级/状态 → 保存 → 卡片出现
- 点卡片 → 抽屉以 edit 模式打开 → 修改描述 → 保存 → 看板同步
- 下拉切换状态 → 列更新 → 进度更新
- 所有任务设为 COMPLETED → "提交至标书编写"按钮启用

**Step 4：git 状态检查**

Run: `git status && git log --oneline -15`
- 仅应看到本次计划涉及的授权文件改动
- 提交历史清晰，每个 Task 一个 atomic commit

**Step 5：推 WIP 分支（不 merge）**

```bash
git push origin HEAD:$(git rev-parse --abbrev-ref HEAD)
```

**Step 6：开 PR（如用户指示）**

参考 CLAUDE.md 的 `gh pr create` 模板，`base: main`，描述里列出：改了什么 / 迁移脚本 / 验证通过项。

---

## 附录：不在本 MVP 范围

以下归入下一期：

- 状态字典的**管理员编辑界面**（目前只能通过迁移脚本改字典）。
- 任务**扩展字段** schema 模型及管理界面（目前 `TaskForm` 只有系统字段；扩展字段层已预留组件结构，下一期加 `<DynamicFormRenderer :fields="extendedFields"/>`）。
- 任务评论 / 历史 / 变更订阅。
- 拖拽改状态、跨列手动排序。
- 状态字典的"允许转移规则"（谁能改到谁，类似 Jira 工作流图）。

---

## 风险与回退

- **迁移失败**：V101 / V102 如失败，回退脚本：
  ```sql
  DROP TABLE IF EXISTS task_status_dict;
  ALTER TABLE task DROP COLUMN content;
  ```
  先在 dev 库演练；生产上线前 DBA 留快照。
- **富文本 XSS**：当前采用 Markdown 存储，前端渲染必须走 `DOMPurify` 或 Element Plus 官方安全渲染；如临时用 `v-html` 必须加 sanitize。
- **前端缓存字典**：状态字典可能被管理员修改（下一期才支持）；但一旦支持，要在 store 中提供 `invalidate()` 或启动时一次性拉取，避免跨会话缓存不一致。

---

## 验证命令速查

| 场景 | 命令 |
|---|---|
| 前端单测 | `npm run test:unit` |
| 前端构建 | `npm run build` |
| 前端数据边界 | `npm run check:front-data-boundaries` |
| 任务状态字面量扫描 | `npm run check:task-status-literal` |
| E2E | `npm run test:e2e` |
| 后端受影响测试 | `cd backend && mvn test -Dtest=<类>` |
| 后端架构测试 | `cd backend && mvn test -Dtest=ArchitectureTest` |
| 后端全量 | `cd backend && mvn test` |

每个 Task 末尾必有一个 commit。Phase 结束时跑一次对应 phase 的 scope-verification。
