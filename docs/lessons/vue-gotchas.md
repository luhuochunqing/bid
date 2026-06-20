# Vue 陷阱与调试经验

记录开发过程中遇到的 Vue 框架陷阱、调试方法论和设计教训。

---

## 1. Composable 中 ref 初始化与 props 同步陷阱

### 问题

```javascript
// ❌ 错误：ref() 只在初始化时求值一次，不会追踪 props 变化
export function useMyComposable(props) {
  const localState = ref(props.someValue)  // 初始化后不再更新
  return { localState }
}

// ✅ 正确：使用 watch 同步 props 变化
export function useMyComposable(props) {
  const localState = ref(props.someValue)
  watch(() => props.someValue, (newVal) => {
    localState.value = newVal
  })
  return { localState }
}
```

`ref()` 接收的是初始值，不是响应式引用。当父组件更新 props 时，composable 中的 ref 不会自动同步。

### 正确写法

```javascript
// 方案1：使用 watch 同步（适合需要本地修改的场景）
export function useMyComposable(props) {
  const localState = ref(props.someValue)
  
  watch(() => props.someValue, (newVal) => {
    if (newVal !== localState.value) {
      localState.value = newVal
    }
  })
  
  return { localState }
}

// 方案2：使用 computed（适合只读场景）
export function useMyComposable(props) {
  const localState = computed(() => props.someValue)
  return { localState }
}

// 方案3：使用 toRef（适合需要双向绑定的场景）
export function useMyComposable(props) {
  const localState = toRef(props, 'someValue')
  return { localState }
}
```

### 调试方法

如果怀疑 props 同步问题，在 composable 中加日志：

```javascript
export function useMyComposable(props) {
  const localState = ref(props.someValue)
  
  watch(() => props.someValue, (newVal, oldVal) => {
    console.log('[useMyComposable] props.someValue changed:', oldVal, '->', newVal)
  })
  
  watch(localState, (newVal, oldVal) => {
    console.log('[useMyComposable] localState changed:', oldVal, '->', newVal)
  })
  
  return { localState }
}
```

或者用 Vue DevTools 检查：
1. 打开 Vue DevTools
2. 找到使用 composable 的组件
3. 检查 props 和 computed/ref 的值是否一致

---

## 2. 身份 UI 不要用业务角色做 fallback

### 问题

CO-282 中，系统顶部栏显示「游客」，但后端 `/api/auth/me` 返回的是已登录用户「系统管理员」。根因不是后端存在游客用户，而是 Header 组件在 session 恢复中或 `currentUser` 为空时使用了业务身份文案兜底：

```javascript
// ❌ 错误：把业务角色当 fallback，会制造不存在的身份
const roleTextMap = {
  admin: '管理员',
  manager: '经理',
  sales: '项目负责人',
  staff: '员工',
  guest: '游客',
}

const userName = computed(() => userStore.currentUser?.name || '游客')
const userRoleText = computed(() => userStore.currentUser?.roleName || roleTextMap[userStore.userRole] || '游客')
```

这会造成两个问题：

1. 用户以为系统真的存在「游客」账号或权限状态错误。
2. 旧 bundle/cache 残留时，即使后端已修好，页面仍可能继续显示旧 fallback，干扰排查。

### 正确写法

```javascript
// ✅ 恢复中显示状态，无用户显示通用占位，不伪造业务身份
const roleTextMap = {
  admin: '管理员',
  manager: '经理',
  sales: '项目负责人',
  staff: '员工',
}

const userName = computed(() => {
  if (userStore.isRestoringSession) return '加载中'
  return userStore.userName || '用户'
})

const userRoleText = computed(() => {
  if (userStore.isRestoringSession) return '加载中'
  if (!userStore.currentUser) return '用户'
  return userStore.currentUser?.roleName || roleTextMap[userStore.userRole] || '用户'
})
```

### 配套防护

浏览器 storage 中旧 `user` hint 也要做结构校验，不能因为存在任意 JSON 就认为用户有效：

```javascript
const hasUserIdentityHint = (user) => {
  if (!user || typeof user !== 'object' || Array.isArray(user)) return false
  return user.id != null || hasText(user.name) || hasText(user.fullName) || hasText(user.username) || hasText(user.employeeNumber)
}
```

### 验证方法

```bash
pnpm vitest run src/components/layout/Header.spec.js
rg "游客|guest" src
```

预期：

```text
Header.spec.js passed
No matches found
```

### 相关文档

- `docs/lessons/root-cause-analysis-co-282.md` — CO-282 完整根因分析
- `src/components/layout/Header.vue` — 顶部栏身份展示
- `src/api/session.js` — storage user hint 结构校验
