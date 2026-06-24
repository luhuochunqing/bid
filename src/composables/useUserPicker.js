// Input: picker mode (search|candidates) + optional context/deptCode/roleCode
// Output: reactive options list, loading flag, debounced search, loadCandidates, formatLabel
// Pos: src/composables/ - shared user picker composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { ref, onUnmounted } from 'vue'
import { usersApi } from '@/api/modules/users.js'
import { formatDisplayName } from '@/utils/formatDisplayName.js'

const DEBOUNCE_MS = 300

export function useUserPicker(options = {}) {
  const {
    mode = 'search',
    context,
    deptCode,
    roleCode,
  } = options

  const userOptions = ref([])
  const loading = ref(false)

  let debounceTimer = null
  // P1.3: AbortController 用于取消未完成请求，防止旧请求覆盖新请求结果
  let currentAbortController = null

  function debounce(fn, delay) {
    return function debounced(...args) {
      if (debounceTimer !== null) {
        clearTimeout(debounceTimer)
      }
      debounceTimer = setTimeout(() => {
        debounceTimer = null
        fn(...args)
      }, delay)
    }
  }

  // P1.3: 取消当前进行中的请求
  function cancelCurrentRequest() {
    if (currentAbortController) {
      currentAbortController.abort()
      currentAbortController = null
    }
  }

  async function searchUsers(query) {
    const trimmed = query == null ? '' : String(query).trim()
    if (trimmed.length === 0) {
      userOptions.value = []
      return
    }
    // P1.3: 取消前一个请求，避免竞态
    cancelCurrentRequest()
    const abortController = new AbortController()
    currentAbortController = abortController

    loading.value = true
    try {
      const results = await usersApi.search(trimmed, 10, { signal: abortController.signal })
      // P1.3: 只在请求未被取消时更新结果
      if (!abortController.signal.aborted) {
        userOptions.value = Array.isArray(results) ? results : []
      }
    } catch (err) {
      // P1.3: abort 不算错误
      if (err?.name === 'AbortError' || abortController.signal.aborted) {
        return
      }
      console.error('[useUserPicker] search failed', err)
      userOptions.value = []
    } finally {
      if (!abortController.signal.aborted) {
        loading.value = false
      }
      if (currentAbortController === abortController) {
        currentAbortController = null
      }
    }
  }

  const search = debounce(searchUsers, DEBOUNCE_MS)

  async function loadCandidates() {
    // P1.3: 取消前一个请求
    cancelCurrentRequest()
    const abortController = new AbortController()
    currentAbortController = abortController

    loading.value = true
    try {
      const results = await usersApi.getAssignableCandidates({
        context,
        deptCode,
        roleCode,
      }, { signal: abortController.signal })
      if (!abortController.signal.aborted) {
        userOptions.value = Array.isArray(results) ? results : []
      }
    } catch (err) {
      if (err?.name === 'AbortError' || abortController.signal.aborted) {
        return
      }
      console.error('[useUserPicker] loadCandidates failed', err)
      userOptions.value = []
    } finally {
      if (!abortController.signal.aborted) {
        loading.value = false
      }
      if (currentAbortController === abortController) {
        currentAbortController = null
      }
    }
  }

  function formatLabel(user) {
    if (!user) return ''
    const name = user.name || user.fullName || ''
    if (!name) return ''
    const parts = [user.deptName, user.roleName].filter(Boolean)
    if (parts.length === 0) return name
    return `${name}（${parts.join('·')}）`
  }

  // P1.4: 组件卸载时清理未完成请求和防抖定时器
  onUnmounted(() => {
    cancelCurrentRequest()
    if (debounceTimer !== null) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
  })

  return {
    options: userOptions,
    loading,
    search,
    loadCandidates,
    formatLabel,
  }
}
