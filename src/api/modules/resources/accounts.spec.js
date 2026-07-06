// Input: accountsApi with mocked httpClient
// Output: CO-522 audit logs endpoint coverage
// Pos: src/api/modules/resources/ - API submodule unit tests

import { describe, expect, it, vi, beforeEach } from 'vitest'

const httpClient = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn()
}

vi.mock('@/api/modules/resources/shared', () => ({
  httpClient,
  invalidIdMessage: (scope) => ({ success: false, message: `invalid ${scope} id` }),
  isNumericId: (id) => typeof id === 'number' || /^\d+$/.test(String(id)),
  formatDateTime: vi.fn(),
  pageContent: vi.fn()
}))

const { accountsApi } = await import('./accounts.js')

describe('accountsApi — CO-522 操作日志端点', () => {
  beforeEach(() => {
    httpClient.get.mockReset()
    httpClient.get.mockResolvedValue({ data: [] })
  })

  it('CO-522: getAuditLogs(accountId) 调用 GET /api/platform/accounts/{id}/audit-logs', async () => {
    await accountsApi.getAuditLogs(42)

    expect(httpClient.get).toHaveBeenCalledWith('/api/platform/accounts/42/audit-logs')
  })

  it('CO-522: 非数字 accountId 不发请求，返回 invalidIdMessage', async () => {
    const result = await accountsApi.getAuditLogs('abc')

    expect(httpClient.get).not.toHaveBeenCalled()
    expect(result).toMatchObject({ success: false, message: expect.stringContaining('invalid') })
  })

  it('CO-522: getAuditLogs 与 getBorrowApplications 使用不同端点（不混淆）', async () => {
    await accountsApi.getAuditLogs(1)
    await accountsApi.getBorrowApplications(1)

    const calls = httpClient.get.mock.calls.map(c => c[0])
    expect(calls).toContain('/api/platform/accounts/1/audit-logs')
    expect(calls).toContain('/api/platform/accounts/1/borrow-applications')
  })
})
