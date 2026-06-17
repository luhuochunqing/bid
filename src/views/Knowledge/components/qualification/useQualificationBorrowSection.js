// Input: Qualification page refs, store state, HTTP client, and workflow APIs
// Output: Qualification borrow/return page state and handlers extracted from Qualification.vue
// Pos: src/views/Knowledge/components/qualification/ - Qualification page borrow composition
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { isBidManager, isBidAdminOrSenior } from '@/utils/permission'

export function useQualificationPermissionMatrix(userStore) {
  const currentRoleCode = computed(() => userStore?.currentUser?.roleCode || userStore?.currentUser?.role || userStore?.userRole || '')
  const canManageQualification = computed(() => isBidManager(currentRoleCode.value) || currentRoleCode.value === 'admin_staff')
  const canViewQualification = computed(() => isBidManager(currentRoleCode.value) || ['admin_staff', 'bid_specialist'].includes(currentRoleCode.value))
  const canAdminQualificationAlert = computed(() => isBidAdminOrSenior(currentRoleCode.value))

  return {
    currentRoleCode,
    canManageQualification,
    canViewQualification,
    canAdminQualificationAlert
  }
}

export function useQualificationBorrowSection({ httpClient }) {
  const alertConfigVisible = ref(false)
  const scanningExpiring = ref(false)

  async function handleScanExpiring(onSuccess) {
    scanningExpiring.value = true
    try {
      const { data } = await httpClient.post('/api/knowledge/qualifications/scan-expiring')
      const count = data?.data ?? 0
      ElMessage.success(`扫描完成，命中 ${count} 条即将到期资质`)
      await onSuccess?.()
    } catch (error) {
      if (error.response?.status === 403) return
      else ElMessage.error(error.response?.data?.msg || '扫描失败')
    } finally {
      scanningExpiring.value = false
    }
  }

  return {
    alertConfigVisible,
    scanningExpiring,
    handleScanExpiring
  }
}