// Regression: ISSUE-001 — empty expense apply submits to backend and throws UI warning
// Found by /qa on 2026-04-20
// Report: .gstack/qa-reports/qa-report-127-0-0-1-2026-04-20.md
// Input: useExpensePageActions dependencies with mocked API and Element Plus messages
// Output: apply form validation blocks invalid submission before API call
// Pos: src/composables/ - expense page action guards

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

const { createExpense, createPayment, messageApi } = vi.hoisted(() => ({
  createExpense: vi.fn(),
  createPayment: vi.fn(),
  messageApi: {
    warning: vi.fn(),
    error: vi.fn(),
    success: vi.fn(),
  },
}))

vi.mock('@/api', () => ({
  resourcesApi: {
    expenses: {
      create: createExpense,
      approve: vi.fn(),
      createPayment,
      getDetail: vi.fn(),
      requestReturn: vi.fn(),
    },
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: messageApi,
}))

import { useExpensePageActions } from './useExpensePageActions.js'

function createContext() {
  return {
    userStore: { userName: '李总' },
    searchForm: ref({ project: '', type: '', status: '' }),
    applyForm: ref({ type: '保证金', project: '', amount: 0, remark: '' }),
    approvalForm: ref({ result: 'approved', comment: '' }),
    paymentForm: ref({ amount: 0, paidAt: '2026-04-20', paidBy: '李总', paymentMethod: 'BANK_TRANSFER', paymentReference: '', remark: '' }),
    availableProjects: ref([]),
    currentExpense: ref(null),
    currentApprovalItem: ref(null),
    currentRemindItem: ref(null),
    expenseDetail: ref(null),
    detailPayments: ref([]),
    applySubmitting: ref(false),
    approvalSubmitting: ref(false),
    paymentSubmitting: ref(false),
    returnSubmitting: ref(false),
    detailLoading: ref(false),
    showApplyDialog: ref(true),
    showApprovalDialog: ref(false),
    showPaymentDialog: ref(false),
    showDetailDialog: ref(false),
    showRemindDialog: ref(false),
    loaders: {
      refreshPage: vi.fn(),
      loadExpenses: vi.fn(),
      loadPaymentRecords: vi.fn(),
      getProjectNameById: vi.fn(),
    },
    exportExpenseRows: vi.fn(),
  }
}

describe('useExpensePageActions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('submitApply(): blocks empty project/amount before calling backend', async () => {
    const context = createContext()
    const actions = useExpensePageActions(context)

    await actions.submitApply()

    expect(createExpense).not.toHaveBeenCalled()
    expect(messageApi.warning).toHaveBeenCalledWith('暂无可关联项目，请先创建投标项目')
    expect(context.applySubmitting.value).toBe(false)
    expect(context.showApplyDialog.value).toBe(true)
  })

  it('submitPayment(): upgrades date-only input to backend LocalDateTime payload', async () => {
    const context = createContext()
    context.currentExpense.value = { id: 42, amount: 8.5 }
    context.showPaymentDialog.value = true
    createPayment.mockResolvedValue({ success: true, data: { id: 42 } })

    const actions = useExpensePageActions(context)

    await actions.submitPayment()

    expect(createPayment).toHaveBeenCalledWith(42, expect.objectContaining({
      paidAt: '2026-04-20T00:00:00'
    }))
    expect(context.showPaymentDialog.value).toBe(false)
    expect(messageApi.success).toHaveBeenCalledWith('支付登记成功')
  })
})
