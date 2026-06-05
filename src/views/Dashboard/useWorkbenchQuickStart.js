// Input: quick-start API dependencies, current user, and submitted callback
// Output: state/actions for Workbench one-stop request flows
// Pos: src/views/Dashboard/ - Dashboard application-shell composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, ref } from 'vue'
import { approvalApi, projectsApi, qualificationsApi, resourcesApi } from '@/api'
import { contractBorrowApi } from '@/api/modules/contractBorrow.js'
import { today } from '@/composables/expensePageShared.js'
import { useSupportRequest } from '@/views/Dashboard/useSupportRequest.js'
import {
  buildContractBorrowPayload,
  buildQualificationBorrowPayload,
  buildQuickExpensePayload,
  createDefaultBorrowRequestForm,
  createDefaultQuickExpenseForm,
  normalizeSupportProjects,
  validateBorrowRequest,
  validateQuickExpense,
} from '@/views/Dashboard/workbench-core.js'

const noopMessage = {
  success: () => {},
  warning: () => {},
  error: () => {},
}

function normalizeQualificationOptions(items = []) {
  return (Array.isArray(items) ? items : [])
    .map((item) => ({ id: Number(item?.id), name: item?.name || `资质#${item?.id}` }))
    .filter((item) => Number.isFinite(item.id))
}

export function useWorkbenchQuickStart({
  currentUserRef,
  message = noopMessage,
  onSubmitted,
  api = {},
} = {}) {
  const projectApi = api.projectsApi || projectsApi
  const qualificationApi = api.qualificationsApi || qualificationsApi
  const borrowApi = api.contractBorrowApi || contractBorrowApi
  const expenseApi = api.resourcesApi || resourcesApi

  const support = useSupportRequest({
    approvalApi: api.approvalApi || approvalApi,
    projectsApi: projectApi,
    message,
    onSubmitted,
  })

  const projects = ref([])
  const qualifications = ref([])
  const optionsError = ref('')
  const borrowDialogVisible = ref(false)
  const borrowSubmitting = ref(false)
  const borrowForm = ref(createDefaultBorrowRequestForm())
  const expenseDialogVisible = ref(false)
  const expenseSubmitting = ref(false)
  const expenseForm = ref(createDefaultQuickExpenseForm())
  const loadingOptions = ref(false)
  const currentUser = computed(() => currentUserRef?.value || {})

  const loadOptions = async ({ includeQualifications = false } = {}) => {
    optionsError.value = ''
    loadingOptions.value = true
    try {
      const requests = [projectApi.getList()]
      if (includeQualifications) requests.push(qualificationApi.getList())
      const [projectResult, qualificationResult] = await Promise.all(requests)

      if (!projectResult?.success || !Array.isArray(projectResult?.data)) {
        throw new Error(projectResult?.message || '项目列表加载失败')
      }
      projects.value = normalizeSupportProjects(projectResult.data)
      support.supportRequestProjects.value = projects.value

      if (includeQualifications) {
        if (!qualificationResult?.success || !Array.isArray(qualificationResult?.data)) {
          throw new Error(qualificationResult?.message || '资质列表加载失败')
        }
        qualifications.value = normalizeQualificationOptions(qualificationResult.data)
      }
    } catch (error) {
      optionsError.value = error?.message || '快速发起选项加载失败'
      message.error?.(optionsError.value)
    } finally {
      loadingOptions.value = false
    }
  }

  const openSupportDialog = async () => {
    await support.openSupportRequestDialog()
  }

  const openBorrowDialog = async () => {
    await loadOptions({ includeQualifications: true })
    borrowForm.value = createDefaultBorrowRequestForm({
      projects: projects.value,
      qualifications: qualifications.value,
      user: currentUser.value,
    })
    borrowDialogVisible.value = true
  }

  const openExpenseDialog = async () => {
    await loadOptions()
    expenseForm.value = createDefaultQuickExpenseForm(projects.value)
    expenseDialogVisible.value = true
  }

  const submitBorrow = async () => {
    const validation = validateBorrowRequest(borrowForm.value)
    if (!validation.valid) {
      message.warning?.(validation.message)
      return false
    }

    borrowSubmitting.value = true
    try {
      const result = borrowForm.value.mode === 'qualification'
        ? await qualificationApi.createBorrow(
          borrowForm.value.qualificationId,
          buildQualificationBorrowPayload(borrowForm.value)
        )
        : await borrowApi.create(buildContractBorrowPayload(borrowForm.value))

      if (!result?.success) throw new Error(result?.msg || '借阅申请提交失败')

      message.success?.(borrowForm.value.mode === 'qualification' ? '资质借阅申请已提交' : '合同借阅申请已提交')
      borrowDialogVisible.value = false
      await onSubmitted?.()
      return true
    } catch (error) {
      message.error?.(error?.message || '借阅申请提交失败')
      return false
    } finally {
      borrowSubmitting.value = false
    }
  }

  const submitExpense = async () => {
    const validation = validateQuickExpense(expenseForm.value)
    if (!validation.valid) {
      message.warning?.(validation.message)
      return false
    }

    expenseSubmitting.value = true
    try {
      const result = await expenseApi.expenses.create(buildQuickExpensePayload(expenseForm.value, {
        today: today(),
        createdBy: currentUser.value?.name || '当前用户',
      }))
      if (!result?.success) throw new Error(result?.msg || '费用申请提交失败')

      message.success?.('投标费用申请已提交，等待审批')
      expenseDialogVisible.value = false
      await onSubmitted?.()
      return true
    } catch (error) {
      message.error?.(error?.message || '费用申请提交失败')
      return false
    } finally {
      expenseSubmitting.value = false
    }
  }

  const handleQuickAction = (action) => {
    if (action.key === 'support') return openSupportDialog()
    if (action.key === 'borrow') return openBorrowDialog()
    if (action.key === 'expense') return openExpenseDialog()
    return undefined
  }

  return {
    ...support,
    projects,
    qualifications,
    optionsError,
    loadingOptions,
    borrowDialogVisible,
    borrowSubmitting,
    borrowForm,
    expenseDialogVisible,
    expenseSubmitting,
    expenseForm,
    loadOptions,
    openSupportDialog,
    openBorrowDialog,
    openExpenseDialog,
    handleQuickAction,
    submitBorrow,
    submitExpense,
  }
}
