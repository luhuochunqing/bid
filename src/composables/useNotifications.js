// Input: options (pollingInterval, autoStart)
// Output: useNotifications composable
// Pos: src/composables/ - Vue composables layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * 通知轮询 composable
 * 自动轮询未读通知数量
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { useNotificationStore } from '@/stores/notifications'

export function useNotifications(options = {}) {
  const { pollingInterval = 15000, autoStart = true } = options
  const store = useNotificationStore()
  const pollingTimer = ref(null)

  const startPolling = () => {
    stopPolling()
    store.fetchUnreadCount()
    pollingTimer.value = setInterval(() => {
      store.fetchUnreadCount()
    }, pollingInterval)
  }

  const stopPolling = () => {
    if (pollingTimer.value) {
      clearInterval(pollingTimer.value)
      pollingTimer.value = null
    }
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
