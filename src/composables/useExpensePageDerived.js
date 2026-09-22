import { computed } from 'vue'
import { isOverdue } from '@/composables/expensePageShared'

export function useExpensePageDerived({ expenses, approvalRecords, searchForm }) {
  const filteredExpenses = computed(() => expenses.value.filter((item) => {
    if (searchForm.value.project && !String(item.project || '').includes(searchForm.value.project)) return false
    if (searchForm.value.type && item.type !== searchForm.value.type) return false
    if (searchForm.value.status && item.status !== searchForm.value.status) return false
    return true
  }))

  const displayedApprovalRecords = computed(() => expenses.value.map((expense) => {
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

  const depositList = computed(() => expenses.value
    .filter((item) => item.type === '保证金')
    .map((item) => {
      const expectedReturn = item.expectedReturnDate || ''
      return {
        ...item,
        expectedReturn,
        payee: item.project || '未知收款方'
      }
    }))

  const overdueCount = computed(() => depositList.value.filter((item) => item.status !== 'returned' && isOverdue(item.expectedReturn)).length)
  const totalPaid = computed(() => expenses.value.filter((item) => item.status === 'paid').reduce((sum, item) => sum + Number(item.amount || 0), 0).toFixed(2))
  const totalPending = computed(() => expenses.value.filter((item) => item.status === 'pending').reduce((sum, item) => sum + Number(item.amount || 0), 0).toFixed(2))
  const depositCount = computed(() => expenses.value.filter((item) => item.type === '保证金').length)
  const warningCount = computed(() => depositList.value.filter((item) => item.status !== 'returned').length)

  const stats = computed(() => ({
    totalPaid: totalPaid.value,
    totalPending: totalPending.value,
    depositCount: depositCount.value,
    warningCount: warningCount.value
  }))

  return {
    filteredExpenses,
    displayedApprovalRecords,
    depositList,
    overdueCount,
    stats
  }
}
