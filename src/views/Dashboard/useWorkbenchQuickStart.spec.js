// Input: mocked real API modules and quick-start composable
// Output: Workbench quick-start submission payload coverage
// Pos: src/views/Dashboard/ - quick-start workflow tests

import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useWorkbenchQuickStart } from './useWorkbenchQuickStart.js'

function createMessage() {
  return { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}

function createApi() {
  return {
    projectsApi: { getList: vi.fn().mockResolvedValue({ success: true, data: [{ id: 9, name: '智慧办公' }] }) },
    qualificationsApi: {
      getList: vi.fn().mockResolvedValue({ success: true, data: [{ id: 12, name: '高新技术企业证书' }] }),
      createBorrow: vi.fn().mockResolvedValue({ success: true, data: { id: 88 } }),
    },
    contractBorrowApi: { create: vi.fn().mockResolvedValue({ success: true, data: { id: 77 } }) },
    resourcesApi: { expenses: { create: vi.fn().mockResolvedValue({ success: true, data: { id: 66 } }) } },
    approvalApi: { submitApproval: vi.fn().mockResolvedValue({ success: true, data: { id: 'A1' } }) },
  }
}

describe('useWorkbenchQuickStart', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('submits support request through the existing approval API', async () => {
    const api = createApi()
    const message = createMessage()
    const submitted = vi.fn()
    const quickStart = useWorkbenchQuickStart({
      currentUserRef: ref({ name: '小王', dept: '销售部' }),
      message,
      onSubmitted: submitted,
      api,
    })

    await quickStart.openSupportRequestDialog()
    quickStart.supportRequestForm.value.description = '  需要商务标支持  '

    await expect(quickStart.submitSupportRequest()).resolves.toBe(true)

    expect(api.approvalApi.submitApproval).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 9,
      approvalType: 'bid_support',
      title: '智慧办公 - 标书支持申请',
      description: '需要商务标支持',
    }))
    expect(submitted).toHaveBeenCalledTimes(1)
  })

  it('submits qualification borrow with projectId', async () => {
    const api = createApi()
    const quickStart = useWorkbenchQuickStart({
      currentUserRef: ref({ name: '小王', dept: '销售部' }),
      message: createMessage(),
      api,
    })

    await quickStart.openBorrowDialog()
    quickStart.borrowForm.value.purpose = '投标使用'

    await expect(quickStart.submitBorrow()).resolves.toBe(true)

    expect(api.qualificationsApi.createBorrow).toHaveBeenCalledWith(12, {
      borrower: '小王',
      department: '销售部',
      projectId: '9',
      purpose: '投标使用',
      returnDate: '',
      remark: '',
    })
  })

  it('submits contract borrow and bidding expense requests', async () => {
    const api = createApi()
    const quickStart = useWorkbenchQuickStart({
      currentUserRef: ref({ name: '小王', dept: '销售部' }),
      message: createMessage(),
      api,
    })

    await quickStart.openBorrowDialog()
    quickStart.borrowForm.value = {
      ...quickStart.borrowForm.value,
      mode: 'contract',
      contractNo: 'HT-001',
      contractName: '年度框架合同',
      purpose: '投标证明',
      expectedReturnDate: '2026-05-01',
    }
    await expect(quickStart.submitBorrow()).resolves.toBe(true)
    expect(api.contractBorrowApi.create).toHaveBeenCalledWith(expect.objectContaining({
      contractNo: 'HT-001',
      contractName: '年度框架合同',
      borrowerName: '小王',
      expectedReturnDate: '2026-05-01',
    }))

    await quickStart.openExpenseDialog()
    quickStart.expenseForm.value = {
      ...quickStart.expenseForm.value,
      type: '标书购买费',
      amount: 300,
      remark: '购买招标文件',
    }
    await expect(quickStart.submitExpense()).resolves.toBe(true)
    expect(api.resourcesApi.expenses.create).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 9,
      category: 'OTHER',
      amount: 300,
      expenseType: '标书购买费',
      expectedReturnDate: null,
      description: '购买招标文件',
      createdBy: '小王',
    }))
  })
})
