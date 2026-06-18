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
