// Regression: apply action from the expense toolbar should still open the dialog

import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'

const showApplyDialog = ref(false)

vi.mock('@/composables/useExpensePage', () => ({
  useExpensePage: () => ({
    searchForm: ref({ project: '', type: '', status: '' }),
    applyForm: ref({ type: '保证金', project: '', amount: 0, remark: '', expectedReturnDate: '' }),
    approvalForm: ref({ result: 'approved', comment: '' }),
    paymentForm: ref({ amount: 0, paidAt: '', paidBy: '', paymentMethod: '', paymentReference: '', remark: '' }),
    filteredExpenses: ref([]),
    displayedApprovalRecords: ref([]),
    availableProjects: ref([]),
    depositList: ref([]),
    overdueCount: ref(0),
    stats: ref({ totalPaid: '0.00', totalPending: '0.00', depositCount: 0, warningCount: 0 }),
    currentExpense: ref(null),
    currentApprovalItem: ref(null),
    currentRemindItem: ref(null),
    expenseDetail: ref(null),
    detailPayments: ref([]),
    listLoading: ref(false),
    approvalLoading: ref(false),
    detailLoading: ref(false),
    applySubmitting: ref(false),
    approvalSubmitting: ref(false),
    paymentSubmitting: ref(false),
    returnSubmitting: ref(false),
    showApplyDialog,
    showApprovalDialog: ref(false),
    showPaymentDialog: ref(false),
    showDetailDialog: ref(false),
    showRemindDialog: ref(false),
    getOverdueDays: vi.fn(() => 0),
    isOverdue: vi.fn(() => false),
    handleSearch: vi.fn(),
    handleReset: vi.fn(),
    handleExport: vi.fn(),
    openApplyDialog: vi.fn(() => {
      showApplyDialog.value = true
    }),
    submitApply: vi.fn(),
    openApprovalDialog: vi.fn(),
    submitApproval: vi.fn(),
    openPaymentDialog: vi.fn(),
    submitPayment: vi.fn(),
    openDetailDialog: vi.fn(),
    requestReturn: vi.fn(),
    openRemindDialog: vi.fn(),
    confirmRemind: vi.fn(),
    confirmReturn: vi.fn()
  })
}))

vi.mock('@/components/expense/ExpenseToolbar.vue', () => ({
  default: defineComponent({
    emits: ['apply'],
    setup(_, { emit, slots }) {
      return () => h('div', [
        h('button', {
          class: 'open-apply-trigger',
          onClick: () => emit('apply')
        }, 'open apply'),
        slots.default?.()
      ])
    }
  })
}))

const passthroughStub = (name) => defineComponent({
  name,
  setup(_, { slots }) {
    return () => h('div', { class: `${name}-stub` }, slots.default?.())
  }
})

vi.mock('@/components/expense/ExpenseLedgerTable.vue', () => ({ default: passthroughStub('expense-ledger-table') }))
vi.mock('@/components/expense/ExpenseDepositTrackingTable.vue', () => ({ default: passthroughStub('expense-deposit-tracking-table') }))
vi.mock('@/components/expense/ExpenseApprovalTable.vue', () => ({ default: passthroughStub('expense-approval-table') }))
vi.mock('@/components/expense/dialogs/ExpenseApplyDialog.vue', () => ({ default: passthroughStub('expense-apply-dialog') }))
vi.mock('@/components/expense/dialogs/ExpenseApproveDialog.vue', () => ({ default: passthroughStub('expense-approve-dialog') }))
vi.mock('@/components/expense/dialogs/ExpensePaymentDialog.vue', () => ({ default: passthroughStub('expense-payment-dialog') }))
vi.mock('@/components/expense/dialogs/ExpenseDetailDialog.vue', () => ({ default: passthroughStub('expense-detail-dialog') }))
vi.mock('@/components/expense/dialogs/ExpenseRemindDialog.vue', () => ({ default: passthroughStub('expense-remind-dialog') }))

describe('Expense.vue regression', () => {
  it('opens the apply dialog when the toolbar emits apply', async () => {
    showApplyDialog.value = false

    const Expense = (await import('./Expense.vue')).default
    const wrapper = mount(Expense)

    await wrapper.get('.open-apply-trigger').trigger('click')
    await nextTick()

    expect(showApplyDialog.value).toBe(true)
  }, 15000)
})
