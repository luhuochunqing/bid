// Input: options (pollingInterval, autoStart)
// Output: useNotifications composable
// Pos: src/composables/ - Vue composables layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 通知轮询 composable
 * 自动轮询未读通知数量
 * 在收到 429 限流响应时自动暂停轮询 60 秒
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { useNotificationStore } from '@/stores/notifications'

export function useNotifications(options = {}) {
  const { pollingInterval = 30000, autoStart = true } = options
  const store = useNotificationStore()
  const pollingTimer = ref(null)
  const backoffUntil = ref(0)

  const stopPolling = () => {
    if (pollingTimer.value) {
      clearInterval(pollingTimer.value)
      pollingTimer.value = null
    }
  }

  const tick = async () => {
    if (backoffUntil.value > Date.now()) {
      return
    }
    try {
      await store.fetchUnreadCount({ silentRateLimit: true })
    } catch (err) {
      const status = err?.response?.status
      if (status === 429) {
        backoffUntil.value = Date.now() + 60000
      } else if (status === 403) {
        // 无通知权限的角色：永久停止轮询，避免控制台 403 刷屏
        stopPolling()
      }
      // 401 不永久停止轮询：token 刷新后应自动恢复，由全局 axios 拦截器处理重登
    }
  }

  const startPolling = () => {
    stopPolling()
    tick()
    pollingTimer.value = setInterval(tick, pollingInterval)
  }

  if (autoStart) {
    onMounted(startPolling)
    onUnmounted(stopPolling)
  }

  return {
    store,
    startPolling,
    stopPolling
  }
}
