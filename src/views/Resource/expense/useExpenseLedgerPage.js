// Input: expense ledger APIs, export API, projects API, and current user session
// Output: composable state/actions for expense ledger page and approval/return flows
// Pos: src/views/Resource/expense/ - Expense page feature composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { exportApi } from '@/api/modules/export'
import { projectsApi, resourcesApi } from '@/api'
import { useUserStore } from '@/stores/user'

const expenseCategoryMap = {
  保证金: 'OTHER',
  标书费: 'OTHER',
  差旅费: 'TRANSPORTATION',
  材料费: 'MATERIAL',
  其他: 'OTHER'
}

const defaultSummary = () => ({
  recordCount: 0,
  totalAmount: 0,
  pendingApprovalAmount: 0,
  approvedAmount: 0,
  returnedAmount: 0,
  depositCount: 0,
  pendingReturnCount: 0,
  byDepartment: [],
  byProject: []
})

const defaultSearchForm = () => ({
  projectId: '',
  department: '',
  expenseType: '',
  status: '',
  dateRange: []
})

export function useExpenseLedgerPage() {
  const userStore = useUserStore()
  const loading = ref(false)
  const projectOptions = ref([])
  const ledgerItems = ref([])
  const approvalRecords = ref([])
  const summary = ref(defaultSummary())
  const searchForm = ref(defaultSearchForm())

  const showApplyDialog = ref(false)
  const showRemindDialog = ref(false)
  const showApprovalDialog = ref(false)
  const showDetailDialog = ref(false)

  const currentRemindItem = ref(null)
  const currentApprovalItem = ref(null)
  const currentExpenseDetail = ref(null)

  const applyForm = ref({
    type: '保证金',
    projectId: '',
    amount: 0,
    remark: ''
  })

  const approvalForm = ref({
    result: 'approved',
    comment: ''
  })

  const departmentOptions = computed(() => summary.value.byDepartment || [])

  const depositItems = computed(() => ledgerItems.value
    .filter((item) => item.type === '保证金')
    .map((item) => {
      const expectedReturn = item.returnDate || calculateExpectedReturn(item.date)
      const isOverdue = item.status !== 'returned' && new Date(expectedReturn) < new Date()
      return {
        ...item,
        expectedReturn,
        isOverdue
      }
    }))

  const overdueCount = computed(() => depositItems.value.filter((item) => item.isOverdue).length)
  const approvalItems = computed(() => ledgerItems.value
    .filter((item) => item.approvalStatus)
    .map((item) => {
      const record = approvalRecords.value.find((entry) => String(entry.expenseId) === String(item.id))
      return {
        ...item,
        applicant: record?.applicant || item.createdBy || '',
        approver: record?.approver || item.approvedBy || '',
        applyTime: record?.applyTime || item.createdAt || item.date,
        remark: record?.remark || item.approvalComment || item.description || ''
      }
    }))

  const loadProjectOptions = async () => {
    const response = await projectsApi.getList()
    if (!response?.success) return
    projectOptions.value = (Array.isArray(response.data) ? response.data : [])
      .filter((project) => project?.id && project?.name)
      .map((project) => ({ id: project.id, name: project.name }))
  }

  const loadLedger = async () => {
    loading.value = true
    const response = await resourcesApi.expenses.getLedger({
      projectId: searchForm.value.projectId || undefined,
      department: searchForm.value.department || undefined,
      type: searchForm.value.expenseType || undefined,
      status: searchForm.value.status || undefined,
      dateRange: searchForm.value.dateRange || []
    })
    loading.value = false

    if (!response?.success) {
      ElMessage.error(response?.msg || '费用台账加载失败')
      return
    }

    ledgerItems.value = Array.isArray(response.data?.items) ? response.data.items : []
    summary.value = response.data?.summary || defaultSummary()
  }

  const loadApprovalRecords = async () => {
    const response = await resourcesApi.expenses.getApprovalRecords(searchForm.value.projectId || undefined)
    if (!response?.success) {
      ElMessage.error(response?.msg || '审批记录加载失败')
      return
    }
    approvalRecords.value = Array.isArray(response.data) ? response.data : []
  }

  const handleSearch = async () => {
    await loadLedger()
    await loadApprovalRecords()
  }

  const handleReset = async () => {
    searchForm.value = defaultSearchForm()
    await handleSearch()
  }

  const handleExport = async () => {
    const rows = ledgerItems.value.map((item) => ({
      项目名称: item.project || item.projectName,
      部门: item.department || item.departmentName || '-',
      费用类型: item.type,
      金额_万元: Number(item.amount || 0).toFixed(2),
      状态: item.backendStatus,
      发生日期: item.date,
      备注: item.description || ''
    }))
    const result = await exportApi.exportExcel('expense-ledger', rows, `expense-ledger-${Date.now()}.csv`)
    if (result?.success) {
      ElMessage.success('费用台账导出成功')
    }
  }

  const handleDetail = async (row) => {
    currentExpenseDetail.value = row
    showDetailDialog.value = true
    const response = await resourcesApi.expenses.getDetail(row.id)
    if (response?.success) {
      const detail = response.data || {}
      const hasReadableProject = Boolean(detail.projectName) || (detail.project && !/^项目#\d+$/.test(detail.project))
      const hasReadableDepartment = Boolean(detail.departmentName) || Boolean(detail.department)

      currentExpenseDetail.value = {
        ...row,
        ...detail,
        project: hasReadableProject ? (detail.project || detail.projectName) : row.project,
        projectName: hasReadableProject ? (detail.projectName || detail.project || row.projectName) : (row.projectName || row.project),
        department: hasReadableDepartment ? (detail.department || detail.departmentName) : row.department,
        departmentName: hasReadableDepartment
          ? (detail.departmentName || detail.department || row.departmentName)
          : (row.departmentName || row.department)
      }
    }
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
    await handleSearch()
    ElMessage.success(`已提交退还申请：${row.project || row.projectName}`)
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
    await handleSearch()
    ElMessage.success(`已确认${row.project || row.projectName}保证金退还`)
  }

  const handleSubmitApply = async () => {
    if (!applyForm.value.projectId) {
      ElMessage.warning('请选择关联项目')
      return
    }
    if (!applyForm.value.amount || Number(applyForm.value.amount) <= 0) {
      ElMessage.warning('请输入有效金额')
      return
    }

    const response = await resourcesApi.expenses.create({
      projectId: Number(applyForm.value.projectId),
      category: expenseCategoryMap[applyForm.value.type] || 'OTHER',
      amount: applyForm.value.amount,
      date: new Date().toISOString().split('T')[0],
      expenseType: applyForm.value.type,
      description: applyForm.value.remark,
      createdBy: userStore.userName
    })
    if (!response?.success) {
      ElMessage.error(response?.msg || '费用申请提交失败')
      return
    }

    showApplyDialog.value = false
    applyForm.value = { type: '保证金', projectId: '', amount: 0, remark: '' }
    await handleSearch()
    ElMessage.success('费用申请已提交，等待审批')
  }

  const openApprovalDialog = (row) => {
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
    showApprovalDialog.value = false
    currentApprovalItem.value = null
    await handleSearch()
    ElMessage.success(`审批${approvalForm.value.result === 'approved' ? '通过' : '拒绝'}`)
  }

  const openRemindDialog = (row) => {
    currentRemindItem.value = row
    showRemindDialog.value = true
  }

  const confirmRemind = () => {
    if (currentRemindItem.value) {
      ElMessage.success(`已向${currentRemindItem.value.project || currentRemindItem.value.projectName}相关方发送提醒`)
    }
    showRemindDialog.value = false
    currentRemindItem.value = null
  }

  onMounted(async () => {
    await loadProjectOptions()
    await handleSearch()
  })

  return {
    loading,
    projectOptions,
    departmentOptions,
    searchForm,
    ledgerItems,
    summary,
    depositItems,
    overdueCount,
    approvalItems,
    showApplyDialog,
    showRemindDialog,
    showApprovalDialog,
    showDetailDialog,
    currentRemindItem,
    currentApprovalItem,
    currentExpenseDetail,
    applyForm,
    approvalForm,
    handleSearch,
    handleReset,
    handleExport,
    handleDetail,
    handleReturn,
    handleConfirmReturn,
    handleSubmitApply,
    openApprovalDialog,
    confirmApproval,
    openRemindDialog,
    confirmRemind
  }
}

function calculateExpectedReturn(payDate) {
  if (!payDate) return ''
  const date = new Date(payDate)
  date.setDate(date.getDate() + 60)
  return date.toISOString().split('T')[0]
}
