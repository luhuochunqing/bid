// Input: Qualification page refs, store state, HTTP client, and workflow APIs
// Output: Qualification borrow/return page state and handlers extracted from Qualification.vue
// Pos: src/views/Knowledge/components/qualification/ - Qualification page borrow composition
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { isFeatureUnavailableResponse, workflowFormApi } from '@/api'

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

export function useQualificationBorrowSection({ qualificationStore, httpClient, canViewQualification, qualificationsRef }) {
  const alertConfigVisible = ref(false)
  const scanningExpiring = ref(false)
  const borrowDialogVisible = ref(false)
  const currentProjectId = ref('')
  const checkingBorrow = ref(false)
  const activeQualId = ref(null)

  const borrowApplyDialogVisible = ref(false)
  const currentBorrowQualification = ref(null)
  const borrowFormSchema = ref({ fields: [] })
  const borrowForm = reactive({
    borrower: '',
    department: '',
    projectId: '',
    purpose: '',
    expectedReturnDate: '',
    remark: '',
    qualificationName: ''
  })

  const borrowRecords = computed(() => qualificationStore.borrowRecords || [])
  const borrowLoading = computed(() => qualificationStore.borrowLoading)
  const borrowFeaturePlaceholder = computed(() => qualificationStore.borrowFeaturePlaceholder)

  async function loadBorrowRecords(qualificationId = null) {
    if (!canViewQualification.value) return
    try {
      await qualificationStore.loadBorrowRecords(qualificationId)
    } catch {
      ElMessage.error('借阅记录加载失败')
    }
  }

  function resetBorrowForm() {
    Object.assign(borrowForm, {
      borrower: '',
      department: '',
      projectId: '',
      purpose: '',
      expectedReturnDate: '',
      remark: '',
      qualificationName: currentBorrowQualification.value?.name || ''
    })
  }

  async function openBorrow(row) {
    currentBorrowQualification.value = row
    resetBorrowForm()
    borrowForm.qualificationName = row?.name || ''
    try {
      const definition = await workflowFormApi.getFormDefinition('QUALIFICATION_BORROW')
      borrowFormSchema.value = definition?.data || { fields: [] }
    } catch {
      borrowFormSchema.value = { fields: [] }
    }
    borrowApplyDialogVisible.value = true
  }

  function openBorrowFromHistory() {
    if (!qualificationsRef.value.length) {
      ElMessage.warning('暂无可借阅资质')
      return
    }
    openBorrow(qualificationsRef.value[0])
  }

  async function openBorrowHistory(row) {
    currentBorrowQualification.value = row
    await loadBorrowRecords(row?.id)
  }

  async function submitBorrowApplication() {
    if (!currentBorrowQualification.value?.id) {
      ElMessage.warning('请先选择待借阅资质')
      return
    }
    const result = await qualificationStore.submitBorrow(currentBorrowQualification.value.id, {
      borrower: borrowForm.borrower,
      department: borrowForm.department,
      projectId: borrowForm.projectId,
      purpose: borrowForm.purpose,
      returnDate: borrowForm.expectedReturnDate,
      remark: borrowForm.remark
    })
    if (result?.success) {
      borrowApplyDialogVisible.value = false
      ElMessage.success('资质借阅申请已提交')
      await loadBorrowRecords(currentBorrowQualification.value.id)
      return
    }
    if (isFeatureUnavailableResponse(result)) {
      ElMessage.warning(result.msg || '资质借阅接口暂未接入')
      return
    }
    ElMessage.error(result?.msg || '借阅申请提交失败')
  }

  async function handleReturnBorrow(row) {
    try {
      await ElMessageBox.confirm(`确认「${row.qualificationName}」已归还吗？`, '归还确认', {
        confirmButtonText: '确认归还',
        cancelButtonText: '取消',
        type: 'success'
      })
      const result = await qualificationStore.returnBorrow(row.id)
      if (result?.success) {
        ElMessage.success('归还成功')
        await loadBorrowRecords(currentBorrowQualification.value?.id || null)
        return
      }
      if (isFeatureUnavailableResponse(result)) {
        ElMessage.warning(result.msg || '资质归还接口暂未接入')
        return
      }
      ElMessage.error(result?.msg || '归还失败')
    } catch {
      // cancelled
    }
  }

  function handleDownload(row) {
    activeQualId.value = row.id
    currentProjectId.value = ''
    borrowDialogVisible.value = true
  }

  async function confirmBorrowCheck() {
    if (!currentProjectId.value) {
      ElMessage.warning('请输入关联的项目ID')
      return
    }
    checkingBorrow.value = true
    try {
      const { data } = await httpClient.get(`/api/qualification/${activeQualId.value}/check-borrow`, {
        params: { projectId: currentProjectId.value }
      })
      if (data.allowed) {
        ElMessage.success('审计通过')
        borrowDialogVisible.value = false
      } else {
        ElMessage.error('被拦截：' + (data.reason || '未绑定借阅流程'))
      }
    } catch (error) {
      if (error.response?.status === 403) ElMessage.error('权限不足')
      else ElMessage.error('网络异常')
    } finally {
      checkingBorrow.value = false
    }
  }

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
    borrowDialogVisible,
    currentProjectId,
    checkingBorrow,
    borrowApplyDialogVisible,
    currentBorrowQualification,
    borrowFormSchema,
    borrowForm,
    borrowRecords,
    borrowLoading,
    borrowFeaturePlaceholder,
    loadBorrowRecords,
    openBorrow,
    openBorrowFromHistory,
    openBorrowHistory,
    submitBorrowApplication,
    handleReturnBorrow,
    handleDownload,
    confirmBorrowCheck,
    handleScanExpiring
  }
}

export default {
  useQualificationPermissionMatrix,
  useQualificationBorrowSection
}
