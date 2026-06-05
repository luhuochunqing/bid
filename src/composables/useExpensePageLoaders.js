import { ElMessage } from 'element-plus'
import { projectsApi, resourcesApi } from '@/api'

export function useExpensePageLoaders({
  searchForm,
  expenses,
  approvalRecords,
  availableProjects,
  expenseDetail,
  listLoading,
  approvalLoading
}) {
  const getProjectNameById = (projectId, fallback = '') => {
    const match = availableProjects.value.find((project) => String(project.id) === String(projectId))
    return match?.name || fallback || (projectId ? `项目#${projectId}` : '未关联项目')
  }

  const hydrateProjectNames = (items = []) => items.map((item) => ({
    ...item,
    project: getProjectNameById(item.projectId, item.project)
  }))

  const loadProjectOptions = async () => {
    const response = await projectsApi.getList()
    if (!response?.success) return

    const list = Array.isArray(response.data) ? response.data : []
    availableProjects.value = list
      .filter((project) => project?.id && project?.name)
      .map((project) => ({
        id: project.id,
        name: project.name
      }))

    expenses.value = hydrateProjectNames(expenses.value)
    approvalRecords.value = hydrateProjectNames(approvalRecords.value)
    expenseDetail.value = expenseDetail.value
      ? { ...expenseDetail.value, project: getProjectNameById(expenseDetail.value.projectId, expenseDetail.value.project) }
      : null
  }

  const loadExpenses = async () => {
    listLoading.value = true
    try {
      const response = await resourcesApi.expenses.getList(searchForm.value)
      if (!response?.success) {
        ElMessage.error(response?.msg || '费用数据加载失败')
        return
      }

      expenses.value = hydrateProjectNames(Array.isArray(response.data) ? response.data : [])
    } finally {
      listLoading.value = false
    }
  }

  const loadApprovalRecords = async () => {
    approvalLoading.value = true
    try {
      const response = await resourcesApi.expenses.getApprovalRecords()
      if (!response?.success) {
        ElMessage.error(response?.msg || '审批记录加载失败')
        return
      }

      approvalRecords.value = hydrateProjectNames(Array.isArray(response.data) ? response.data : [])
    } finally {
      approvalLoading.value = false
    }
  }

  const loadPaymentRecords = async (expenseId) => {
    const response = await resourcesApi.expenses.getPayments(expenseId)
    if (!response?.success) {
      ElMessage.error(response?.msg || '支付记录加载失败')
      return []
    }

    return Array.isArray(response.data) ? response.data : []
  }

  const refreshPage = async () => {
    await Promise.all([loadExpenses(), loadApprovalRecords()])
  }

  return {
    getProjectNameById,
    loadProjectOptions,
    loadExpenses,
    loadApprovalRecords,
    loadPaymentRecords,
    refreshPage
  }
}
