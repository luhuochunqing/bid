// 4.1.3.5 下架确认弹窗状态机
// 职责：管理 retireDialogVisible / retireTarget / retireLoading + 调 /retire 接口
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

export function useRetireDialog({ httpClient, onRetired }) {
  const retireDialogVisible = ref(false)
  const retireTarget = ref(null)
  const retireLoading = ref(false)

  const openRetireDialog = (row) => {
    if (!row) return
    retireTarget.value = { id: row.id, name: row.name, certificateNo: row.certificateNo }
    retireDialogVisible.value = true
  }

  const submitRetire = async (reason) => {
    if (!retireTarget.value?.id) return
    retireLoading.value = true
    try {
      await httpClient.post(`/api/knowledge/qualifications/${retireTarget.value.id}/retire`, { reason })
      ElMessage.success('已下架')
      retireDialogVisible.value = false
      if (typeof onRetired === 'function') await onRetired()
    } catch (err) {
      ElMessage.error(err?.message || '下架失败')
    } finally {
      retireLoading.value = false
    }
  }

  return {
    retireDialogVisible,
    retireTarget,
    retireLoading,
    openRetireDialog,
    submitRetire
  }
}
