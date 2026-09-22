// Input: resource/project APIs, export API, and user store
// Output: expense page state/actions composed for the resource expense view
// Pos: src/views/Resource/expense/ - Expense page composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { projectsApi, resourcesApi } from '@/api'
import { exportApi } from '@/api/modules/export'
import { useUserStore } from '@/stores/user'
import { buildDepositTrackingList } from './depositTracking.js'

const expenseCategoryMap = {
  保证金: 'OTHER',
  标书费: 'OTHER',
  差旅费: 'TRANSPORTATION',
  其他: 'OTHER'
}

function resolveExpenseCategory(type) {
  return expenseCategoryMap[type] || 'OTHER'
}

export function useExpensePage() {
  const userStore = useUserStore()
  const searchForm = ref({ project: '', type: '', status: '' })
  const fees = ref([])
  const approvalRecords = ref([])
  const availableProjects = ref([])

  const showApplyDialog = ref(false)
  const showRemindDialog = ref(false)
  const showApprovalDialog = ref(false)
  const showDetailDialog = ref(false)

  const applyForm = ref({
    type: '保证金',
    project: '',
    amount: 0,
    remark: '',
    expectedReturnDate: ''
  })
  const approvalForm = ref({ result: 'approved', comment: '' })
  const currentRemindItem = ref(null)
  const currentApprovalItem = ref(null)
  const currentExpenseDetail = ref(null)

  const getProjectNameById = (projectId, fallback = '') => {
    const match = availableProjects.value.find((project) => String(project.id) === String(projectId))
    return match?.name || fallback || (projectId ? `项目#${projectId}` : '未关联项目')
  }

  const hydrateExpenseProjectNames = (items = []) => items.map((item) => ({
    ...item,
    project: getProjectNameById(item.projectId, item.project)
  }))

  const hydrateApprovalProjectNames = (items = []) => items.map((item) => ({
    ...item,
    project: getProjectNameById(item.projectId, item.project)
  }))

  const loadExpenses = async () => {
    const response = await resourcesApi.expenses.getList(searchForm.value)
    if (!response?.success) {
      ElMessage.error(response?.msg || '费用数据加载失败')
      return
    }
    fees.value = hydrateExpenseProjectNames(Array.isArray(response.data) ? response.data : [])
  }

  const loadProjectOptions = async () => {
    const response = await projectsApi.getList()
    if (!response?.success) return
    const projects = Array.isArray(response.data) ? response.data : []
    availableProjects.value = projects
      .filter((project) => project?.id && project?.name)
      .map((project) => ({ id: project.id, name: project.name }))
    fees.value = hydrateExpenseProjectNames(fees.value)
    approvalRecords.value = hydrateApprovalProjectNames(approvalRecords.value)
  }

  const loadApprovalRecords = async () => {
    const response = await resourcesApi.expenses.getApprovalRecords()
    if (!response?.success) {
      ElMessage.error(response?.msg || '审批记录加载失败')
      return
    }
    approvalRecords.value = hydrateApprovalProjectNames(Array.isArray(response.data) ? response.data : [])
  }

  const filteredFees = computed(() => fees.value.filter((item) => {
    if (searchForm.value.project && !item.project.includes(searchForm.value.project)) return false
    if (searchForm.value.type && item.type !== searchForm.value.type) return false
    if (searchForm.value.status && item.status !== searchForm.value.status) return false
    return true
  }))

  const displayedApprovalRecords = computed(() => fees.value.map((expense) => {
    const record = approvalRecords.value.find((item) => String(item.expenseId) === String(expense.id))
    return {
      id: expense.id,
      expenseId: expense.id,
      project: expense.project || record?.project || '-',
      type: expense.type || record?.type || '-',
      amount: Number(expense.amount || record?.amount || 0),
      applicant: expense.createdBy || record?.applicant || '-',
      applyTime: expense.date || record?.applyTime || '-',
      approver: expense.approvedBy || record?.approver || '',
      approvalStatus: expense.approvalStatus || record?.approvalStatus || 'pending',
      remark: expense.approvalComment || record?.remark || expense.description || ''
    }
  }).filter((item) => item.approvalStatus))

  const depositTrackingList = computed(() => buildDepositTrackingList(fees.value))
  const overdueCount = computed(() => depositTrackingList.value.filter((item) => item.trackingStatus === 'overdue').length)
  const totalPaid = computed(() => fees.value.filter((f) => f.status === 'paid').reduce((sum, f) => sum + f.amount, 0).toFixed(2))
  const totalPending = computed(() => fees.value.filter((f) => f.status === 'pending').reduce((sum, f) => sum + f.amount, 0).toFixed(2))
  const depositCount = computed(() => fees.value.filter((f) => f.type === '保证金').length)
  const warningCount = computed(() => depositTrackingList.value.filter((f) => f.trackingStatus !== 'returned').length)

  const refreshPage = async () => {
    await loadExpenses()
    await loadApprovalRecords()
  }

  const handleSearch = () => loadExpenses()

  const handleReset = () => {
    searchForm.value = { project: '', type: '', status: '' }
    loadExpenses()
  }

  const handleExport = async () => {
    const result = await exportApi.exportExcel('expenses', filteredFees.value.map((item) => ({
      项目名称: item.project,
      费用类型: item.type,
      金额_万元: Number(item.amount || 0).toFixed(2),
      状态: item.status,
      审批状态: item.approvalStatus,
      发生日期: item.date,
      预计退还日期: item.expectedReturnDate || '',
      最近提醒时间: item.lastRemindedAt || '',
      备注: item.description || ''
    })), `expenses-${new Date().toISOString().slice(0, 10)}.csv`)
    if (!result?.success) {
      ElMessage.error('导出失败')
      return
    }
    ElMessage.success('费用台账导出成功')
  }

  const handleDetail = async (row) => {
    currentExpenseDetail.value = row
    showDetailDialog.value = true
    const response = await resourcesApi.expenses.getDetail(row.id)
    if (!response?.success) {
      ElMessage.error(response?.msg || '费用详情加载失败')
      return
    }
    currentExpenseDetail.value = response.data || row
  }

  const handleReturn = async (row) => {
    const response = await resourcesApi.expenses.requestReturn(row.id, {
      actor: userStore.userName,
      comment: `${userStore.userName} 发起保证金退还申请`
    })
    if (!response?.success) {
      ElMessage.error(response?.msg || '保证金退还申请失败')
      return
    }
    await refreshPage()
    ElMessage.success(`已提交退还申请：${row.project}`)
  }

  const handleSubmitApply = async () => {
    const response = await resourcesApi.expenses.create({
      projectId: Number(applyForm.value.project),
      category: resolveExpenseCategory(applyForm.value.type),
      amount: applyForm.value.amount,
      date: new Date().toISOString().split('T')[0],
      expectedReturnDate: applyForm.value.expectedReturnDate || null,
      expenseType: applyForm.value.type,
      description: applyForm.value.remark,
      createdBy: userStore.userName
    })
    if (!response?.success) {
      ElMessage.error(response?.msg || '费用申请提交失败')
      return
    }
    await refreshPage()
    ElMessage.success('费用申请已提交，等待审批')
    showApplyDialog.value = false
    applyForm.value = { type: '保证金', project: '', amount: 0, remark: '', expectedReturnDate: '' }
  }

  const handleRemind = (row) => {
    currentRemindItem.value = row
    showRemindDialog.value = true
  }

  const confirmRemind = async () => {
    if (!currentRemindItem.value) return
    const response = await resourcesApi.expenses.sendReturnReminder(currentRemindItem.value.id, {
      actor: userStore.userName,
      comment: `${userStore.userName} 发起保证金退还跟进提醒`
    })
    if (!response?.success) {
      ElMessage.error(response?.msg || '发送提醒失败')
      return
    }
    await refreshPage()
    ElMessage.success(`已发送 ${currentRemindItem.value.project} 保证金退还提醒`)
    showRemindDialog.value = false
    currentRemindItem.value = null
  }

  const handleConfirmReturn = async (row) => {
    const response = await resourcesApi.expenses.confirmReturn(row.id, {
      actor: userStore.userName,
      comment: `${userStore.userName} 确认保证金已退还`
    })
    if (!response?.success) {
      ElMessage.error(response?.msg || '保证金退还确认失败')
      return
    }
    await refreshPage()
    ElMessage.success(`已确认${row.project}保证金退还，金额：${row.amount}万元`)
  }

  const handleApprove = (row) => {
    currentApprovalItem.value = row
    approvalForm.value = { result: 'approved', comment: '' }
    showApprovalDialog.value = true
  }

  const confirmApproval = async () => {
    const response = await resourcesApi.expenses.approve(currentApprovalItem.value?.id, {
      result: approvalForm.value.result === 'approved' ? 'APPROVED' : 'REJECTED',
      comment: approvalForm.value.comment,
      approver: userStore.userName
    })
    if (!response?.success) {
      ElMessage.error(response?.msg || '费用审批失败')
      return
    }
    await refreshPage()
    ElMessage.success(`审批${approvalForm.value.result === 'approved' ? '通过' : '拒绝'}`)
    showApprovalDialog.value = false
    currentApprovalItem.value = null
  }

  const init = async () => {
    await loadProjectOptions()
    await refreshPage()
  }

  return {
    searchForm,
    filteredFees,
    displayedApprovalRecords,
    depositTrackingList,
    overdueCount,
    totalPaid,
    totalPending,
    depositCount,
    warningCount,
    availableProjects,
    showApplyDialog,
    showRemindDialog,
    showApprovalDialog,
    showDetailDialog,
    applyForm,
    approvalForm,
    currentRemindItem,
    currentApprovalItem,
    currentExpenseDetail,
    handleSearch,
    handleReset,
    handleExport,
    handleDetail,
    handleReturn,
    handleSubmitApply,
    handleRemind,
    confirmRemind,
    handleConfirmReturn,
    handleApprove,
    confirmApproval,
    init
  }
}
