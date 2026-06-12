// Input: Qualification page refs, store state, HTTP client, and workflow APIs
// Output: Qualification borrow/return page state and handlers extracted from Qualification.vue
// Pos: src/views/Knowledge/components/qualification/ - Qualification page borrow composition
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

export function useQualificationPermissionMatrix(userStore) {
  const MANAGED_ROLES = ['admin_staff', 'bid_admin', 'bid_lead']
  const VIEW_ROLES = ['admin_staff', 'bid_admin', 'bid_lead', 'bid_specialist']
  const ALERT_ADMIN_ROLES = ['bid_admin']

  const currentRoleCode = computed(() => userStore?.currentUser?.roleCode || userStore?.currentUser?.role || userStore?.userRole || '')
  const canManageQualification = computed(() => MANAGED_ROLES.includes(currentRoleCode.value))
  const canViewQualification = computed(() => VIEW_ROLES.includes(currentRoleCode.value))
  const canAdminQualificationAlert = computed(() => ALERT_ADMIN_ROLES.includes(currentRoleCode.value))

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
      if (error.response?.status === 403) ElMessage.error('权限不足')
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