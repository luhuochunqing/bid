// Input: projects/approval APIs and message dependency
// Output: support request dialog state/actions composable for Dashboard Workbench
// Pos: src/views/Dashboard/ - dashboard feature composables
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, ref } from 'vue'
import { approvalApi as defaultApprovalApi, projectsApi as defaultProjectsApi } from '@/api'
import {
  buildSupportRequestPayload,
  createDefaultSupportRequestForm,
  normalizeSupportProjects,
  validateSupportRequest,
} from '@/views/Dashboard/workbench-core.js'

const noopMessage = {
  success: () => {},
  warning: () => {},
  error: () => {},
}

export function useSupportRequest({
  approvalApi = defaultApprovalApi,
  projectsApi = defaultProjectsApi,
  message = noopMessage,
  onSubmitted,
  normalizeProjects = normalizeSupportProjects,
} = {}) {
  const supportRequestDialogVisible = ref(false)
  const supportRequestSubmitting = ref(false)
  const supportRequestProjects = ref([])
  const supportRequestForm = ref(createDefaultSupportRequestForm())
  const supportProjectsError = ref('')
  const myProjectCount = computed(() => supportRequestProjects.value.length)

  const loadSupportRequestProjects = async () => {
    supportProjectsError.value = ''
    try {
      const result = await projectsApi.getList()
      if (!result?.success || !Array.isArray(result?.data)) {
        throw new Error(result?.msg || '加载项目列表失败')
      }
      supportRequestProjects.value = normalizeProjects(result.data)
    } catch {
      supportRequestProjects.value = []
      supportProjectsError.value = '可申请支持的项目加载失败，请稍后重试'
    }
    return supportRequestProjects.value
  }

  const resetSupportRequestForm = () => {
    supportRequestForm.value = createDefaultSupportRequestForm(supportRequestProjects.value)
  }

  const openSupportRequestDialog = async () => {
    supportRequestDialogVisible.value = true
    if (supportRequestProjects.value.length === 0) {
      await loadSupportRequestProjects()
    }
    resetSupportRequestForm()
  }

  const submitSupportRequest = async () => {
    const validation = validateSupportRequest(supportRequestForm.value)
    if (!validation.valid) {
      message.warning?.(validation.message)
      return false
    }

    supportRequestSubmitting.value = true
    try {
      const result = await approvalApi.submitApproval(
        buildSupportRequestPayload(supportRequestForm.value, supportRequestProjects.value)
      )
      if (!result?.success) {
        throw new Error(result?.msg || '提交标书支持申请失败')
      }
      message.success?.('标书支持申请已提交')
      supportRequestDialogVisible.value = false
      await onSubmitted?.()
      resetSupportRequestForm()
      return true
    } catch (error) {
      message.error?.(error?.message || '提交标书支持申请失败')
      return false
    } finally {
      supportRequestSubmitting.value = false
    }
  }

  return {
    supportRequestDialogVisible,
    supportRequestSubmitting,
    supportRequestProjects,
    supportRequestForm,
    supportProjectsError,
    myProjectCount,
    loadSupportRequestProjects,
    resetSupportRequestForm,
    openSupportRequestDialog,
    submitSupportRequest,
  }
}
