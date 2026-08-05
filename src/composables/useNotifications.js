// Input: options (pollingInterval, autoStart)
// Output: useNotifications composable
// Pos: src/composables/ - Vue composables layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 通知轮询 composable
 * 自动轮询未读通知数量
 * 在收到 429 限流响应时自动暂停轮询 60 秒
 *
 * CO-605: 使用模块级单例确保全局只启动一个轮询定时器。
 * 即使 Header 组件因路由切换重挂载或多个组件同时调用 useNotifications，
 * 也只会有一个 setInterval 在跑，避免重复请求 unread-count。
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { useNotificationStore } from '@/stores/notifications'

// 模块级单例状态（所有 useNotifications 实例共享）
let globalPollingTimer = null
let globalBackoffUntil = 0
let activeInstanceCount = 0

const resetBackoff = () => {
  globalBackoffUntil = 0
}

export function useNotifications(options = {}) {
  const { pollingInterval = 30000, autoStart = true } = options
  const store = useNotificationStore()
  // 每个实例自己的 pollingTimer ref（用于模板/计算属性观察），实际指向全局单例
  const pollingTimer = ref(null)

  const stopPolling = () => {
    if (activeInstanceCount > 0) activeInstanceCount -= 1
    // 只有最后一个活跃实例卸载时才真正停止全局轮询
    if (activeInstanceCount === 0 && globalPollingTimer) {
      clearInterval(globalPollingTimer)
      globalPollingTimer = null
    }
    pollingTimer.value = null
  }

  const tick = async () => {
    if (globalBackoffUntil > Date.now()) {
      return
    }
    try {
      await store.fetchUnreadCount({ silentRateLimit: true })
    } catch (err) {
      const status = err?.response?.status
      if (status === 429) {
        globalBackoffUntil = Date.now() + 60000
      } else if (status === 403) {
        // 无通知权限的角色：永久停止轮询，避免控制台 403 刷屏
        stopPolling()
      }
      // 401 不永久停止轮询：token 刷新后应自动恢复，由全局 axios 拦截器处理重登
    }
  }

  const startPolling = () => {
    activeInstanceCount += 1
    // 如果已有全局轮询在跑，复用，不重复启动
    if (globalPollingTimer) {
      pollingTimer.value = globalPollingTimer
      return
    }
    tick()
    globalPollingTimer = setInterval(tick, pollingInterval)
    pollingTimer.value = globalPollingTimer
  }

  if (autoStart) {
    onMounted(startPolling)
    onUnmounted(stopPolling)
  }

  return {
    store,
    startPolling,
    stopPolling,
    // 暴露给测试用（仅测试场景下重置模块状态）
    _resetForTest: () => {
      if (globalPollingTimer) {
        clearInterval(globalPollingTimer)
        globalPollingTimer = null
      }
      activeInstanceCount = 0
      resetBackoff()
    }
  }
}
