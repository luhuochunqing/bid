<template>
  <div class="expense-page">
    <ExpenseToolbar
      v-model:search-form="searchForm"
      :stats="stats"
      @search="handleSearch"
      @reset="handleReset"
      @apply="openApplyDialog"
      @export="handleExport"
    >
      <ExpenseLedgerTable
        :expenses="filteredExpenses"
        :loading="listLoading"
        @detail="openDetailDialog"
        @pay="openPaymentDialog"
        @request-return="requestReturn"
      />
    </ExpenseToolbar>

    <ExpenseDepositTrackingTable
      :deposits="depositList"
      :overdue-count="overdueCount"
      :loading="listLoading || returnSubmitting"
      @remind="openRemindDialog"
      @confirm-return="confirmReturn"
    />

    <ExpenseApprovalTable
      :records="displayedApprovalRecords"
      :loading="approvalLoading"
      @approve="openApprovalDialog"
    />

    <ExpenseApplyDialog
      v-model="showApplyDialog"
      v-model:form="applyForm"
      :projects="availableProjects"
      :submitting="applySubmitting"
      @submit="submitApply"
    />

    <ExpenseApproveDialog
      v-model="showApprovalDialog"
      v-model:form="approvalForm"
      :expense="currentApprovalItem"
      :submitting="approvalSubmitting"
      @submit="submitApproval"
    />

    <ExpensePaymentDialog
      v-model="showPaymentDialog"
      v-model:form="paymentForm"
      :expense="currentExpense"
      :records="detailPayments"
      :submitting="paymentSubmitting"
      @submit="submitPayment"
    />

    <ExpenseDetailDialog
      v-model="showDetailDialog"
      :expense="expenseDetail"
      :payment-records="detailPayments"
      :loading="detailLoading"
    />

    <ExpenseRemindDialog
      v-model="showRemindDialog"
      :expense="currentRemindItem"
      :is-overdue="isOverdue"
      :overdue-days="getOverdueDays"
      @submit="confirmRemind"
    />
  </div>
</template>

<script setup>
import ExpenseApprovalTable from '@/components/expense/ExpenseApprovalTable.vue'
import ExpenseDepositTrackingTable from '@/components/expense/ExpenseDepositTrackingTable.vue'
import ExpenseLedgerTable from '@/components/expense/ExpenseLedgerTable.vue'
import ExpenseToolbar from '@/components/expense/ExpenseToolbar.vue'
import ExpenseApplyDialog from '@/components/expense/dialogs/ExpenseApplyDialog.vue'
import ExpenseApproveDialog from '@/components/expense/dialogs/ExpenseApproveDialog.vue'
import ExpenseDetailDialog from '@/components/expense/dialogs/ExpenseDetailDialog.vue'
import ExpensePaymentDialog from '@/components/expense/dialogs/ExpensePaymentDialog.vue'
import ExpenseRemindDialog from '@/components/expense/dialogs/ExpenseRemindDialog.vue'
import { useExpensePage } from '@/composables/useExpensePage'

const {
  searchForm,
  applyForm,
  approvalForm,
  paymentForm,
  filteredExpenses,
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
} = useExpensePage()
</script>

<style scoped lang="scss">
.expense-page {
  padding: 20px;
}

@media (max-width: 768px) {
  .expense-page {
    padding: 12px;
  }
}
</style>
