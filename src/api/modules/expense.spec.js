// Input: expense API module with mocked HTTP client
// Output: expense ledger normalization and filter-param coverage
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn()
  }
}))

import httpClient from '@/api/client'
import { expensesApi } from './expense.js'

describe('expensesApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getLedger(): maps multidimensional filters and normalizes ledger summary/items', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: {
        items: [
          {
            id: 8,
            projectId: 21,
            projectName: '西北能源项目',
            departmentCode: 'MKT',
            departmentName: '市场经营部',
            category: 'TRANSPORTATION',
            expenseType: '差旅费',
            amount: 1.26,
            date: '2026-04-10',
            description: '现场踏勘',
            createdBy: '李工',
            status: 'RETURN_REQUESTED',
            approvedBy: '王经理',
            approvedAt: '2026-04-12T02:30:00Z',
            returnRequestedAt: '2026-04-16T03:00:00Z',
            returnComment: '等待退还'
          }
        ],
        summary: {
          recordCount: 1,
          totalAmount: 1.26,
          pendingApprovalAmount: 0,
          approvedAmount: 1.26,
          returnedAmount: 0,
          depositCount: 0,
          pendingReturnCount: 1,
          byDepartment: [
            { key: 'MKT', label: '市场经营部', count: 1, totalAmount: 1.26 }
          ],
          byProject: [
            { key: '21', label: '西北能源项目', count: 1, totalAmount: 1.26 }
          ]
        }
      }
    })

    const result = await expensesApi.getLedger({
      projectId: 21,
      dateRange: ['2026-04-01', '2026-04-30'],
      department: '市场经营部',
      type: '差旅费',
      status: 'return_requested'
    })

    expect(httpClient.get).toHaveBeenCalledWith('/api/resources/expenses/ledger', {
      params: {
        projectId: 21,
        startDate: '2026-04-01',
        endDate: '2026-04-30',
        department: '市场经营部',
        expenseType: '差旅费',
        status: 'RETURN_REQUESTED'
      }
    })

    expect(result.success).toBe(true)
    expect(result.data.summary).toMatchObject({
      recordCount: 1,
      totalAmount: 1.26,
      pendingReturnCount: 1,
      byDepartment: [
        { key: 'MKT', label: '市场经营部', count: 1, totalAmount: 1.26 }
      ]
    })
    expect(result.data.items[0]).toMatchObject({
      id: 8,
      project: '西北能源项目',
      department: '市场经营部',
      type: '差旅费',
      amount: 1.26,
      status: 'paid',
      approvalStatus: 'approved',
      backendStatus: 'RETURN_REQUESTED',
      returnRequestedAt: expect.any(String)
    })
  })
})
