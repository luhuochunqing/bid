# 只读态多行文本框滚动条无法滑动 — 根因与修复

## 现象

标讯详情页只读态多行文本框（标讯描述、标讯信息）填入大量内容后，右侧滚动条置灰，无法上下滑动查看全部内容。编辑态 textarea 正常。

## 涉及字段

| 组件 | 字段 | 状态 |
|------|------|------|
| `BasicInfoReadOnly.vue` | 标讯描述 (`description`) | 只读 |
| `BasicInfoReadOnly.vue` | 标讯信息 (`tenderInfo`) | 只读 |

## 根因

Element Plus `el-input`（`type="textarea"` + `readonly` + `autosize`）内部通过 `textareaCalcStyle` 直接操作 DOM inline style 来控制高度和滚动。

源码路径：`node_modules/element-plus/es/components/input/src/input.vue2.mjs`

### 正常流程（编辑态）

```
用户输入
  → resizeTextarea()
    → textareaCalcStyle = { overflowY: "hidden", height: "..." }  // ① 先锁死，防闪烁
    → nextTick:
        textareaCalcStyle = { height: "..." }                       // ② 解锁，回到浏览器默认
```

每次输入触发 `resizeTextarea`，`overflowY: "hidden"` 仅在 ① 短暂存在，② 执行后移除。浏览器默认 `overflow: auto` 接管，滚动条正常。

### 异常流程（只读态）

```
组件挂载
  → resizeTextarea() 执行一次
    → textareaCalcStyle = { overflowY: "hidden", height: "..." }   // ① 锁死
    → nextTick:
        textareaCalcStyle = { height: "..." }                        // ② 解锁
```

`readonly` 禁止输入 → 无后续输入事件 → `resizeTextarea` 不再触发。

但如果内容在挂载后**异步加载**（如 API 返回数据），挂载时内容为空 → `height` 按最小高度计算 → 内容加载后 textarea 高度不变 → `scrollHeight > clientHeight` 但 `overflow-y: hidden` 已在 ② 中移除，理论上滚动条应出现。

**实际问题**：Element Plus 通过 `element.style.overflowY` 直接设置 inline style。`nextTick` 的解锁是**异步的**，在复杂异步场景（数据异步加载 + Vue 重渲染 + EP 组件生命周期）下，① 的 `hidden` 可能在内容加载完成后未正确回退，残留为 inline `overflow-y: hidden`。

**关键**：inline style 的 `overflow-y: hidden` 优先级高于一切 CSS 规则（包括 `!important`），所以通过 CSS 无法覆盖。

## 修复方案

**绕过 Element Plus，使用原生 `<textarea>`**。浏览器原生 `<textarea readonly>` 不存在上述异步时序问题。

### Before

```html
<el-input
  :model-value="tender.description || '-'"
  type="textarea"
  :autosize="{ minRows: 3, maxRows: 10 }"
  readonly
/>
```

### After

```html
<textarea
  :value="tender.description || '-'"
  rows="10"
  readonly
  class="readonly-textarea"
/>
```

### CSS

```css
.readonly-textarea {
  width: 100%;
  min-height: 72px;
  padding: 5px 11px;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  font-family: inherit;
  font-size: inherit;
  line-height: 1.5;
  color: #303133;
  background: #f5f7fa;
  resize: vertical;
  overflow-y: scroll;
}
```

## 修改文件

`src/views/Bidding/detail/components/BasicInfoReadOnly.vue`
- 标讯描述（`:64`）：`el-input` → `<textarea>`
- 标讯信息（`:69`）：`el-input` → `<textarea>`
- CSS：新增 `.readonly-textarea` 样式

## 关联

- CO-272：标讯详情-多行文本字段无法滚动查看完整内容(缺少autosize)
- CO-272 的 `autosize` 修复解决了编辑态滚动问题，但只读态的根本原因是 Element Plus 的 inline style 机制，需要绕过。
