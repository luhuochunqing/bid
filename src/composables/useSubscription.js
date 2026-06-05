// Input: entityType + entityId target reference
// Output: reactive subscribed state + toggle/fetch handlers
// Pos: src/composables/ - Subscription composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { ref, onMounted } from 'vue'
import { subscriptionsApi } from '@/api/modules/subscriptions'

export function useSubscription(entityType, entityId) {
  const subscribed = ref(false)
  const loading = ref(false)
  const error = ref(null)

  const fetchState = async () => {
    try {
      const result = await subscriptionsApi.check(entityType, entityId)
      subscribed.value = result?.subscribed ?? false
    } catch (e) {
      error.value = e?.message || String(e)
    }
  }

  const toggle = async () => {
    if (loading.value) return
    loading.value = true
    error.value = null
    try {
      if (subscribed.value) {
        await subscriptionsApi.unsubscribe(entityType, entityId)
      } else {
        await subscriptionsApi.subscribe(entityType, entityId)
      }
      subscribed.value = !subscribed.value
    } catch (e) {
      error.value = e?.message || String(e)
    } finally {
      loading.value = false
    }
  }

  onMounted(fetchState)

  return { subscribed, loading, error, toggle, fetchState }
}

export default useSubscription
