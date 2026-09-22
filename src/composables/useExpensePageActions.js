import { ElMessage } from 'element-plus'
import { resourcesApi } from '@/api'
import {
  defaultApplyForm,
  defaultApprovalForm,
  defaultPaymentForm,
  defaultSearchForm,
  normalizePaymentDateTime,
  resolveExpenseCategory,
  today
} from '@/composables/expensePageShared'

export function useExpensePageActions({
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
}) {
  const { refreshPage, loadExpenses, loadPaymentRecords, getProjectNameById } = loaders

  const handleSearch = () => {
    loadExpenses()
  }

  const handleReset = () => {
    searchForm.value = defaultSearchForm()
    loadExpenses()
  }

  const handleExport = () => {
    exportExpenseRows().then((result) => {
      if (!result?.success) {
        ElMessage.error('导出失败')
        return
      }
      ElMessage.success('费用台账导出成功')
    })
  }

  const openApplyDialog = () => {
    applyForm.value = defaultApplyForm()
    showApplyDialog.value = true
  }

  const validateApplyForm = () => {
    if (!applyForm.value.type) {
      ElMessage.warning('请选择费用类型')
      return false
    }

    if (!applyForm.value.project) {
      ElMessage.warning(
        availableProjects.value?.length
          ? '请选择关联项目'
          : '暂无可关联项目，请先创建投标项目'
      )
      return false
    }

    if (!(Number(applyForm.value.amount) > 0)) {
      ElMessage.warning('请输入大于 0 的费用金额')
      return false
    }

    return true
  }

  const submitApply = async () => {
    if (!validateApplyForm()) return

    applySubmitting.value = true
    try {
      const response = await resourcesApi.expenses.create({
        projectId: Number(applyForm.value.project),
        category: resolveExpenseCategory(applyForm.value.type),
        amount: applyForm.value.amount,
        date: today(),
        expenseType: applyForm.value.type,
        expectedReturnDate: applyForm.value.expectedReturnDate || null,
        description: applyForm.value.remark,
        createdBy: userStore.userName
      })

      if (!response?.success) {
        ElMessage.error(response?.msg || '费用申请提交失败')
        return
      }

      showApplyDialog.value = false
      applyForm.value = defaultApplyForm()
      await refreshPage()
      ElMessage.success('费用申请已提交，等待审批')
    } catch (error) {
      ElMessage.error(error?.message || '费用申请提交失败')
    } finally {
      applySubmitting.value = false
    }
  }

  const openApprovalDialog = (row) => {
    currentApprovalItem.value = row
    approvalForm.value = defaultApprovalForm()
    showApprovalDialog.value = true
  }

  const submitApproval = async () => {
    if (!currentApprovalItem.value) return

    approvalSubmitting.value = true
    try {
      const response = await resourcesApi.expenses.approve(currentApprovalItem.value.id, {
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
      await refreshPage()
      ElMessage.success(`审批${approvalForm.value.result === 'approved' ? '通过' : '拒绝'}`)
    } finally {
      approvalSubmitting.value = false
    }
  }

  const openPaymentDialog = (row) => {
    currentExpense.value = row
    paymentForm.value = {
      ...defaultPaymentForm(userStore.userName),
      amount: Number(row.amount || 0)
    }
    detailPayments.value = []
    showPaymentDialog.value = true

    loadPaymentRecords(row.id).then((records) => {
      detailPayments.value = records
    })
  }

  const submitPayment = async () => {
    if (!currentExpense.value) return

    paymentSubmitting.value = true
    try {
      const response = await resourcesApi.expenses.createPayment(currentExpense.value.id, {
        amount: paymentForm.value.amount,
        paidAt: normalizePaymentDateTime(paymentForm.value.paidAt),
        paidBy: paymentForm.value.paidBy,
        paymentMethod: paymentForm.value.paymentMethod,
        paymentReference: paymentForm.value.paymentReference,
        remark: paymentForm.value.remark
      })

      if (!response?.success) {
        ElMessage.error(response?.msg || '登记支付失败')
        return
      }

      showPaymentDialog.value = false
      await refreshPage()
      ElMessage.success('支付登记成功')
    } finally {
      paymentSubmitting.value = false
    }
  }

  const openDetailDialog = async (row) => {
    showDetailDialog.value = true
    detailLoading.value = true
    expenseDetail.value = row
    detailPayments.value = []

    try {
      const [detailResponse, payments] = await Promise.all([
        resourcesApi.expenses.getDetail(row.id),
        loadPaymentRecords(row.id)
      ])

      if (!detailResponse?.success) {
        ElMessage.error(detailResponse?.message || '费用详情加载失败')
        return
      }

      expenseDetail.value = {
        ...(detailResponse.data || row),
        project: getProjectNameById(detailResponse?.data?.projectId, detailResponse?.data?.project || row.project)
      }
      detailPayments.value = payments
    } finally {
      detailLoading.value = false
    }
  }

  const requestReturn = async (row) => {
    returnSubmitting.value = true
    try {
      const response = await resourcesApi.expenses.requestReturn(row.id, {
        actor: userStore.userName,
        requestedBy: userStore.userName,
        comment: `${userStore.userName} 发起保证金退还申请`
      })

      if (!response?.success) {
        ElMessage.error(response?.msg || '保证金退还申请失败')
        return
      }

      await refreshPage()
      ElMessage.success(`已提交退还申请：${row.project}`)
    } finally {
      returnSubmitting.value = false
    }
  }

  const openRemindDialog = (row) => {
    currentRemindItem.value = row
    showRemindDialog.value = true
  }

  const confirmRemind = async () => {
    if (!currentRemindItem.value) return

    returnSubmitting.value = true
    try {
      const response = await resourcesApi.expenses.remindReturn(currentRemindItem.value.id, {
        actor: userStore.userName,
        comment: `${userStore.userName} 发起保证金归还提醒`
      })

      if (!response?.success) {
        ElMessage.error(response?.msg || '保证金归还提醒发送失败')
        return
      }

      await refreshPage()
      ElMessage.success(`已向${currentRemindItem.value.project || '相关项目'}发送保证金归还提醒`)
      showRemindDialog.value = false
      currentRemindItem.value = null
    } finally {
      returnSubmitting.value = false
    }
  }

  const confirmReturn = async (row) => {
    returnSubmitting.value = true
    try {
      const response = await resourcesApi.expenses.confirmReturn(row.id, {
        actor: userStore.userName,
        confirmedBy: userStore.userName,
        comment: `${userStore.userName} 确认保证金已退还`
      })

      if (!response?.success) {
        ElMessage.error(response?.msg || '保证金退还确认失败')
        return
      }

      await refreshPage()
      ElMessage.success(`已确认${row.project}保证金退还，金额：${row.amount}万元`)
    } finally {
      returnSubmitting.value = false
    }
  }

  return {
    handleSearch,
    handleReset,
    handleExport,
    openApplyDialog,
    submitApply,
    openApprovalDialog,
    submitApproval,
    openPaymentDialog,
    submitPayment,
    openDetailDialog,
    requestReturn,
    openRemindDialog,
    confirmRemind,
    confirmReturn
  }
}
