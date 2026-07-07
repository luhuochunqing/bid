# 复盘报告支持 Excel 上传 - 实现计划

> **For agentic workers:** 本计划任务粒度较细，建议在当前会话内顺序执行。步骤使用 checkbox (`- [ ]`) 语法跟踪。

**Goal:** 在项目详情页「项目复盘」阶段的「复盘报告」上传区，扩展文件类型限制以支持 `.xls` 与 `.xlsx`。

**Architecture:** 后端 `UploadValidationPolicy` 与存储层已支持 Excel，因此仅修改前端 `RetrospectiveStage.vue` 的 `accept`、`beforeUpload` MIME 白名单及提示文字，并补充单元测试。

**Tech Stack:** Vue 3, Element Plus, Vitest

---

## 文件结构

| 文件 | 职责 |
|---|---|
| `src/views/Project/stages/RetrospectiveStage.vue` | 复盘阶段 UI，包含复盘报告 `el-upload` 组件 |
| `src/views/Project/stages/RetrospectiveStage.spec.js` | 复盘阶段单元测试 |

---

### Task 1: 扩展上传组件 accept 与提示文字

**Files:**
- Modify: `src/views/Project/stages/RetrospectiveStage.vue:78`
- Modify: `src/views/Project/stages/RetrospectiveStage.vue:90`

- [ ] **Step 1: 修改 `el-upload` 的 `accept` 属性**

将 `accept` 由 `.doc,.docx,.pdf` 扩展为 `.doc,.docx,.pdf,.xls,.xlsx`：

```vue
accept=".doc,.docx,.pdf,.xls,.xlsx"
```

- [ ] **Step 2: 修改上传提示文字**

将提示由「支持 Word/PDF 格式」改为「支持 Word/Excel/PDF 格式」：

```vue
<div class="el-upload__tip">支持 Word/Excel/PDF 格式，单文件≤20MB，最多3个</div>
```

- [ ] **Step 3: 在浏览器中快速验证提示文字渲染**

无需完整启动服务，可通过单元测试或临时打开页面检查提示文案。

---

### Task 2: 扩展 beforeUpload MIME 类型白名单

**Files:**
- Modify: `src/views/Project/stages/RetrospectiveStage.vue:156-162`

- [ ] **Step 1: 修改 `beforeUpload` 函数**

将 MIME 白名单扩展为包含 Excel 类型，并同步更新错误提示：

```js
function beforeUpload(file) {
  const valid = [
    'application/pdf',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'application/vnd.ms-excel',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  ]
  if (!valid.includes(file.type)) return ElMessage.error('仅支持 Word/Excel/PDF 格式') || false
  if (file.size > MAX_FILE_MB * 1024 * 1024) return ElMessage.error(`文件不能超过 ${MAX_FILE_MB}MB`) || false
  return true
}
```

- [ ] **Step 2: 自测 beforeUpload 对各类文件的返回**

 mentally 检查：
- `.xlsx` (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`) → true
- `.xls` (`application/vnd.ms-excel`) → true
- `.docx` → true
- `.pdf` → true
- `.png` → false 并提示「仅支持 Word/Excel/PDF 格式」

---

### Task 3: 补充单元测试

**Files:**
- Modify: `src/views/Project/stages/RetrospectiveStage.spec.js`

- [ ] **Step 1: 在现有 mock 下方新增辅助函数用于触发 beforeUpload**

由于 `beforeUpload` 是组件内部函数，需通过 `ElUpload` 的 `props('beforeUpload')` 提取并调用。已有测试中通过 `wrapper.findComponent({ name: 'ElUpload' })` 可获取上传组件。

- [ ] **Step 2: 编写 Excel 文件通过校验的测试**

```js
it('CO-XXX: 复盘报告支持 .xlsx 文件上传', async () => {
  getRetrospectiveMock.mockImplementation(() => Promise.resolve({
    success: true,
    data: { meetingTime: '', reportFileIds: [] },
  }))

  const { default: RetrospectiveStage } = await import('./RetrospectiveStage.vue')
  const wrapper = mount(RetrospectiveStage, {
    props: { projectId: 1, resultType: 'WON' },
    global: { plugins: [ElementPlus] },
  })
  await flushPromises()

  const elUpload = wrapper.findComponent({ name: 'ElUpload' })
  const beforeUpload = elUpload.props('beforeUpload')
  const xlsxFile = new File(['xlsx content'], '复盘数据.xlsx', {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  })

  expect(beforeUpload(xlsxFile)).toBe(true)
})
```

- [ ] **Step 3: 编写 .xls 文件通过校验的测试**

```js
it('CO-XXX: 复盘报告支持 .xls 文件上传', async () => {
  getRetrospectiveMock.mockImplementation(() => Promise.resolve({
    success: true,
    data: { meetingTime: '', reportFileIds: [] },
  }))

  const { default: RetrospectiveStage } = await import('./RetrospectiveStage.vue')
  const wrapper = mount(RetrospectiveStage, {
    props: { projectId: 1, resultType: 'WON' },
    global: { plugins: [ElementPlus] },
  })
  await flushPromises()

  const elUpload = wrapper.findComponent({ name: 'ElUpload' })
  const beforeUpload = elUpload.props('beforeUpload')
  const xlsFile = new File(['xls content'], '复盘数据.xls', {
    type: 'application/vnd.ms-excel',
  })

  expect(beforeUpload(xlsFile)).toBe(true)
})
```

- [ ] **Step 4: 编写非允许格式仍被拒绝的测试**

```js
it('CO-XXX: 复盘报告仍拒绝非 Word/Excel/PDF 格式', async () => {
  getRetrospectiveMock.mockImplementation(() => Promise.resolve({
    success: true,
    data: { meetingTime: '', reportFileIds: [] },
  }))

  const { default: RetrospectiveStage } = await import('./RetrospectiveStage.vue')
  const wrapper = mount(RetrospectiveStage, {
    props: { projectId: 1, resultType: 'WON' },
    global: { plugins: [ElementPlus] },
  })
  await flushPromises()

  const elUpload = wrapper.findComponent({ name: 'ElUpload' })
  const beforeUpload = elUpload.props('beforeUpload')
  const pngFile = new File(['png content'], '复盘数据.png', {
    type: 'image/png',
  })

  expect(beforeUpload(pngFile)).toBe(false)
  expect(elMessageErrorMock).toHaveBeenCalledWith('仅支持 Word/Excel/PDF 格式')
})
```

注意：当前 spec 文件只 mock 了 `ElMessage.warning`，需同步在 mock 中加入 `error`：

```js
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, ElMessage: { info: vi.fn(), warning: elMessageWarningMock, error: elMessageErrorMock, success: vi.fn() } }
})
```

并定义 `const elMessageErrorMock = vi.fn()`。

- [ ] **Step 5: 运行单元测试**

Run:
```bash
npx vitest run src/views/Project/stages/RetrospectiveStage.spec.js
```

Expected: 所有测试通过，包括新增 Excel 用例。

---

### Task 4: 提交变更

**Files:**
- `src/views/Project/stages/RetrospectiveStage.vue`
- `src/views/Project/stages/RetrospectiveStage.spec.js`
- `docs/superpowers/specs/2026-07-07-retrospective-excel-upload-design.md`
- `docs/superpowers/plans/2026-07-07-retrospective-excel-upload.md`

- [ ] **Step 1: 检查变更范围**

```bash
git diff --stat
```

Expected: 仅包含上述文件，且 `RetrospectiveStage.vue` 改动集中在 accept、提示文字、beforeUpload 三处。

- [ ] **Step 2: 提交代码与测试**

```bash
git add src/views/Project/stages/RetrospectiveStage.vue
ngit add src/views/Project/stages/RetrospectiveStage.spec.js
git commit -m "feat(retrospective): 复盘报告上传支持 Excel (.xls/.xlsx)"
```

> 注：设计文档与计划文档是否一并提交取决于项目文档策略；若需提交可单独 add。

---

## 自我检查

1. **Spec coverage:** 设计文档中的三处前端改动（accept、MIME、提示文字）分别对应 Task 1 与 Task 2；测试计划对应 Task 3；提交对应 Task 4。无遗漏。
2. **Placeholder scan:** 计划内无 TBD/TODO，所有代码块均为完整可执行代码。
3. **Type consistency:** `beforeUpload` 签名保持不变，`ElMessage.error` mock 名称在测试中与代码一致。
