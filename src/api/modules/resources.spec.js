// Input: resources API module with mocked HTTP client
// Output: resources expense normalization and return reminder endpoint coverage
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

import httpClient from '@/api/client'
import { expensesApi } from './resources.js'

describe('expensesApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getList(): normalizes deposit return tracking fields from backend DTOs', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: {
        content: [
          {
            id: 18,
            projectId: 99,
            projectName: '西域智算中心',
            expenseType: '保证金',
            amount: 125000,
            status: 'RETURN_REQUESTED',
            date: '2026-04-01',
            expectedReturnDate: '2026-04-15',
            overdue: true,
            lastReturnReminderAt: '2026-04-18T09:30:00Z',
            returnComment: '催办中'
          }
        ],
        totalElements: 1
      }
    })

    const result = await expensesApi.getList()

    expect(httpClient.get).toHaveBeenCalledWith('/api/resources/expenses')
    expect(result.data).toEqual([
      expect.objectContaining({
        id: 18,
        project: '西域智算中心',
        type: '保证金',
        status: 'paid',
        expectedReturnDate: '2026-04-15',
        lastRemindedAt: expect.any(String),
        overdue: true
      })
    ])
  })

  it('sendReturnReminder(): uses the dedicated backend reminder endpoint', async () => {
    httpClient.post.mockResolvedValue({
      success: true,
      data: {
        id: 18,
        expenseType: '保证金',
        status: 'RETURN_REQUESTED',
        expectedReturnDate: '2026-04-15',
        lastReturnReminderAt: '2026-04-19T08:00:00Z'
      }
    })

    await expensesApi.sendReturnReminder(18, {
      actor: '前端测试',
      comment: '请尽快办理退还'
    })

    expect(httpClient.post).toHaveBeenCalledWith('/api/resources/expenses/18/return-reminder', {
      actor: '前端测试',
      comment: '请尽快办理退还'
    })
  })
})
