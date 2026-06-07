// Input: contract borrow API module with mocked HTTP client
// Output: endpoint wiring and contract-borrow status normalization coverage
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
import { contractBorrowApi } from './contractBorrow.js'

describe('contractBorrowApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getList(): forwards filters and normalizes paged OVERDUE rows', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: {
        items: [
          {
            id: 7,
            contractNo: 'HT-2026-0421',
            contractName: '西域智算中心年度框架合同',
            borrowerName: '小王',
            expectedReturnDate: '2026-04-20',
            status: 'APPROVED',
            displayStatus: 'OVERDUE',
            overdue: true
          }
        ],
        total: 21,
        page: 2,
        size: 10,
        totalPages: 3
      }
    })

    const result = await contractBorrowApi.getList({
      keyword: '智算',
      status: 'overdue',
      page: 2,
      size: 10
    })

    expect(httpClient.get).toHaveBeenCalledWith('/api/contract-borrows', {
      params: {
        keyword: '智算',
        status: 'OVERDUE',
        page: 2,
        size: 10
      }
    })
    expect(result.data).toMatchObject({
      total: 21,
      page: 2,
      size: 10,
      totalPages: 3
    })
    expect(result.data.items[0]).toMatchObject({
      id: 7,
      contractNo: 'HT-2026-0421',
      status: 'APPROVED',
      displayStatus: 'OVERDUE',
      overdue: true,
      statusLabel: '已逾期'
    })
  })

  it('create and lifecycle actions use the dedicated contract borrow endpoints', async () => {
    httpClient.post.mockResolvedValue({ success: true, data: { id: 7, status: 'PENDING_APPROVAL' } })

    await contractBorrowApi.create({ contractNo: 'HT-1', expectedReturnDate: '2026-04-30' })
    await contractBorrowApi.approve(7, { actorName: '张经理', comment: '同意' })
    await contractBorrowApi.reject(7, { actorName: '张经理', reason: '信息不完整' })
    await contractBorrowApi.returnBack(7, { actorName: '小王', comment: '已归还' })
    await contractBorrowApi.cancel(7, { actorName: '小王', reason: '不再需要' })
    await contractBorrowApi.getEvents(7)

    expect(httpClient.post).toHaveBeenNthCalledWith(1, '/api/contract-borrows', {
      contractNo: 'HT-1',
      expectedReturnDate: '2026-04-30'
    })
    expect(httpClient.post).toHaveBeenNthCalledWith(2, '/api/contract-borrows/7/approve', {
      actorName: '张经理',
      comment: '同意'
    })
    expect(httpClient.post).toHaveBeenNthCalledWith(3, '/api/contract-borrows/7/reject', {
      actorName: '张经理',
      reason: '信息不完整'
    })
    expect(httpClient.post).toHaveBeenNthCalledWith(4, '/api/contract-borrows/7/return', {
      actorName: '小王',
      comment: '已归还'
    })
    expect(httpClient.post).toHaveBeenNthCalledWith(5, '/api/contract-borrows/7/cancel', {
      actorName: '小王',
      reason: '不再需要'
    })
    expect(httpClient.get).toHaveBeenCalledWith('/api/contract-borrows/7/events')
  })
})
