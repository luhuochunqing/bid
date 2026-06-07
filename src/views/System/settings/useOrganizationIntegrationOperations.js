// Input: organizationIntegrationApi and Element Plus message bridge
// Output: API-only organization integration operation state, errors, and guarded actions
// Pos: src/views/System/settings/ - system integration composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { organizationIntegrationApi } from '@/api/modules/systemIntegration.js'

export function useOrganizationIntegrationOperations() {
  const loading = ref(false)
  const syncing = ref(false)
  const resyncingUser = ref(false)
  const resyncingDepartment = ref(false)
  const replayingDeadLetter = ref(false)
  const status = ref(null)
  const loaded = ref(false)
  const errorText = ref('')
  const userId = ref('')
  const deptId = ref('')
  const deadLetterEventKey = ref('')
  const canOperate = computed(() => loaded.value && !errorText.value && status.value?.enabled === true)

  const load = async () => {
    loading.value = true
    errorText.value = ''
    try {
      status.value = await organizationIntegrationApi.getOperationsStatus()
      loaded.value = true
    } catch (error) {
      status.value = null
      loaded.value = false
      errorText.value = error?.message || '组织架构状态加载失败'
      ElMessage.error(errorText.value)
    } finally {
      loading.value = false
    }
  }

  const ensureOperable = () => {
    if (canOperate.value) return true
    ElMessage.warning(errorText.value || '组织架构集成未启用')
    return false
  }

  const startSyncRun = async () => {
    if (!ensureOperable()) return
    syncing.value = true
    try {
      await organizationIntegrationApi.startSyncRun({ sourceApp: 'oss', runType: 'RECONCILIATION' })
      ElMessage.success('组织架构对账已触发')
      await load()
    } finally {
      syncing.value = false
    }
  }

  const resyncUser = async () => {
    if (!userId.value.trim()) {
      ElMessage.warning('请输入用户 ID')
      return
    }
    if (!ensureOperable()) return
    resyncingUser.value = true
    try {
      await organizationIntegrationApi.resyncUser(userId.value.trim())
      ElMessage.success('用户重同步已触发')
      userId.value = ''
      await load()
    } finally {
      resyncingUser.value = false
    }
  }

  const resyncDepartment = async () => {
    if (!deptId.value.trim()) {
      ElMessage.warning('请输入部门 ID')
      return
    }
    if (!ensureOperable()) return
    resyncingDepartment.value = true
    try {
      await organizationIntegrationApi.resyncDepartment(deptId.value.trim())
      ElMessage.success('部门重同步已触发')
      deptId.value = ''
      await load()
    } finally {
      resyncingDepartment.value = false
    }
  }

  const replayDeadLetter = async () => {
    const eventKey = deadLetterEventKey.value.trim()
    if (!eventKey) {
      ElMessage.warning('请输入事件 ID')
      return
    }
    if (!ensureOperable()) return
    replayingDeadLetter.value = true
    try {
      const result = await organizationIntegrationApi.replayDeadLetter(eventKey)
      if (result?.code === '200') {
        ElMessage.success('死信事件重放成功')
        deadLetterEventKey.value = ''
      } else {
        ElMessage.warning(result?.msg || '死信事件重放未成功')
      }
      await load()
    } finally {
      replayingDeadLetter.value = false
    }
  }

  return {
    loading,
    syncing,
    resyncingUser,
    resyncingDepartment,
    replayingDeadLetter,
    status,
    loaded,
    errorText,
    canOperate,
    userId,
    deptId,
    deadLetterEventKey,
    load,
    startSyncRun,
    resyncUser,
    resyncDepartment,
    replayDeadLetter,
  }
}
