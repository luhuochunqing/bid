// Input: system info API response
// Output: system info state and load action
// Pos: System settings system info composable

import { ref } from 'vue'
import { settingsApi } from '@/api'

export function useSystemInfo() {
  const loading = ref(false)
  const error = ref(null)
  const systemInfo = ref(null)

  const loadSystemInfo = async () => {
    loading.value = true
    error.value = null
    try {
      const response = await settingsApi.getSystemInfo()
      systemInfo.value = response?.data || null
    } catch (e) {
      console.error('Failed to load system info:', e)
      error.value = e.message || '加载系统信息失败'
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    error,
    systemInfo,
    loadSystemInfo
  }
}
