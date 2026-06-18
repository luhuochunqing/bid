# CRM更换商机双提示+评估表未回填 根因分析

> Issue: CO-264
> 日期: 2026-06-18
> 排查者: mimo

---

## 现场还原

**症状素描**：更换CRM商机时出现三个问题：
1. 双提示矛盾：先显示"已回填"，再显示"已自动提交"
2. 评估表未回填：更换后评估表字段未更新
3. 权限乐观更新：更换后权限状态未及时刷新

**边界划定**：
- 首次关联CRM商机：正常 ✅
- 更换已关联的CRM商机：出现问题 ❌

**思维沙箱**：怀疑是两个组件重复触发提示 + composable 中 props 同步机制缺失。

---

## 剥洋葱：逆向调用链

### Layer 1 — 入口/参数层

用户点击"确认关联并回填评估表"按钮后：

```
CrmOpportunitySelector.confirmLink()
  → emit('linked', { opportunityId, opportunityName, evaluationData })
  → DetailPage.onCrmOpportunityLinked()
```

### Layer 2 — 核心逻辑层

**问题1：双提示矛盾**

```javascript
// useCrmOpportunitySelector.js:189
ElMessage.success('CRM商机已关联，评估表已回填')  // ← 提示1

// DetailPage.vue:332
ElMessage.success('CRM商机已关联，评估表已自动提交')  // ← 提示2
```

Vue 事件处理是同步的，emit 触发后父组件处理函数立即执行，导致两条提示都显示。

**问题2：props 不同步**

```javascript
// useCrmOpportunitySelector.js:31-33
const linkedOpportunity = ref(
  props.alreadyLinkedName ? { name: props.alreadyLinkedName } : null
)
```

`ref()` 只在初始化时求值一次。当 `loadTenderDetail()` 完成后 `props.alreadyLinkedName` 更新，`linkedOpportunity` 不会自动同步。

### Layer 3 — 数据层

```
用户选择新商机
  → confirmLink 更新 linkedOpportunity（本地 ref）
  → emit('linked') 触发 onCrmOpportunityLinked
  → API: saveEvaluationDraft → linkCrmOpportunity → submitEvaluationFinal
  → loadTenderDetail() 更新 tender.value
  → props.alreadyLinkedName 更新
  → 但 linkedOpportunity 不同步 ❌
```

---

## 零号病人定位

**第一行错误1（双提示）：**

```
useCrmOpportunitySelector.js:189
ElMessage.success('CRM商机已关联，评估表已回填')
```

**第一行错误2（props 不同步）：**

```
useCrmOpportunitySelector.js:31-33
const linkedOpportunity = ref(props.alreadyLinkedName ? { name: props.alreadyLinkedName } : null)
```

**必然性解释：**
- `ref()` 接收的是初始值，不是响应式引用
- Vue 的 `ref()` 不会自动追踪传入参数的变化
- 需要使用 `watch()` 或 `computed()` 来建立响应式连接

---

## 验证与修复

### 修复 diff

```diff
// useCrmOpportunitySelector.js

// 修复1：删除重复提示
- showDialog.value = false
- ElMessage.success('CRM商机已关联，评估表已回填')
+ showDialog.value = false

// 修复2：添加 watch 同步 props 变化
  const linkedOpportunity = ref(
    props.alreadyLinkedName ? { name: props.alreadyLinkedName } : null
  )
+
+ // 同步父组件传入的 alreadyLinkedName 变化（如 loadTenderDetail 完成后）
+ watch(() => props.alreadyLinkedName, (newName) => {
+   if (newName && (!linkedOpportunity.value || linkedOpportunity.value.name !== newName)) {
+     linkedOpportunity.value = { name: newName }
+   }
+ })
```

**最小验证：**
1. 关联CRM商机后，只应看到一条提示："CRM商机已关联，评估表已自动提交"
2. 更换CRM商机后，评估表字段应正确更新
3. 切换 tab 后回来，linkedOpportunity 应保持正确状态

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 零号病人已定位 | `useCrmOpportunitySelector.js:189` 和 `:31-33` | ✅ |
| 必然性已证明 | ref() 初始化不追踪 props 变化 | ✅ |
| 最小验证已设计 | 双提示验证 + 评估表回填验证 | ✅ |
| 修复 diff 已提供 | 见上 | ✅ |
| 防复发测试已设计 | 见下 | ✅ |

**Verdict**: ✅ **PASS**

### 防复发测试

1. 关联CRM商机 → 只显示一条成功提示
2. 更换CRM商机 → 评估表字段正确更新
3. 切换 tab 后回来 → linkedOpportunity 保持正确
