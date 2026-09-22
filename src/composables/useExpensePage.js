import { onMounted, ref } from 'vue'
import { exportApi } from '@/api/modules/export'
import { useUserStore } from '@/stores/user'
import {
  defaultApplyForm,
  defaultApprovalForm,
  defaultPaymentForm,
  defaultSearchForm,
  getOverdueDays,
  isOverdue,
  today
} from '@/composables/expensePageShared'
import { useExpensePageActions } from '@/composables/useExpensePageActions'
import { useExpensePageDerived } from '@/composables/useExpensePageDerived'
import { useExpensePageLoaders } from '@/composables/useExpensePageLoaders'

export function useExpensePage() {
  const userStore = useUserStore()

  const searchForm = ref(defaultSearchForm())
  const applyForm = ref(defaultApplyForm())
  const approvalForm = ref(defaultApprovalForm())
  const paymentForm = ref(defaultPaymentForm(userStore.userName))

  const expenses = ref([])
  const approvalRecords = ref([])
  const availableProjects = ref([])
  const currentExpense = ref(null)
  const currentApprovalItem = ref(null)
  const currentRemindItem = ref(null)
  const expenseDetail = ref(null)
  const detailPayments = ref([])

  const listLoading = ref(false)
  const approvalLoading = ref(false)
  const detailLoading = ref(false)
  const applySubmitting = ref(false)
  const approvalSubmitting = ref(false)
  const paymentSubmitting = ref(false)
  const returnSubmitting = ref(false)

  const showApplyDialog = ref(false)
  const showApprovalDialog = ref(false)
  const showPaymentDialog = ref(false)
  const showDetailDialog = ref(false)
  const showRemindDialog = ref(false)

  const loaders = useExpensePageLoaders({
    searchForm,
    expenses,
    approvalRecords,
    availableProjects,
    expenseDetail,
    listLoading,
    approvalLoading
  })

  const {
    filteredExpenses,
    displayedApprovalRecords,
    depositList,
    overdueCount,
    stats
  } = useExpensePageDerived({
    expenses,
    approvalRecords,
    searchForm
  })

  const exportExpenseRows = () => exportApi.exportExcel('expenses', filteredExpenses.value.map((item) => ({
      项目名称: item.project,
      费用类型: item.type,
      金额_万元: Number(item.amount || 0).toFixed(2),
      状态: item.status,
      审批状态: item.approvalStatus,
      发生日期: item.date,
      支付日期: item.paidAt || '',
      备注: item.description || ''
    })), `expenses-${today()}.csv`)

  const actions = useExpensePageActions({
    userStore,
    searchForm,
    applyForm,
    approvalForm,
    paymentForm,
    availableProjects,
    currentExpense,
    currentApprovalItem,
    currentRemindItem,
    expenseDetail,
    detailPayments,
    applySubmitting,
    approvalSubmitting,
    paymentSubmitting,
    returnSubmitting,
    detailLoading,
    showApplyDialog,
    showApprovalDialog,
    showPaymentDialog,
    showDetailDialog,
    showRemindDialog,
    loaders,
    exportExpenseRows
  })

  onMounted(async () => {
    await loaders.loadProjectOptions()
    await loaders.refreshPage()
  })

  return {
    searchForm,
    applyForm,
    approvalForm,
    paymentForm,
    expenses,
    filteredExpenses,
    approvalRecords,
    displayedApprovalRecords,
    availableProjects,
    depositList,
    overdueCount,
    stats,
    currentExpense,
    currentApprovalItem,
    currentRemindItem,
    expenseDetail,
    detailPayments,
    listLoading,
    approvalLoading,
    detailLoading,
    applySubmitting,
    approvalSubmitting,
    paymentSubmitting,
    returnSubmitting,
    showApplyDialog,
    showApprovalDialog,
    showPaymentDialog,
    showDetailDialog,
    showRemindDialog,
    getOverdueDays,
    isOverdue,
    ...actions
  }
}
