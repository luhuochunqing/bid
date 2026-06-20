// Input: crm API module with mocked HTTP client
// Output: CRM opportunity and contact-person endpoint contract coverage
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: {
    post: vi.fn(),
  },
}))

import httpClient from '@/api/client'
import { crmApi } from './crm.js'

describe('crmApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('searchOpportunities(): posts page-list query unchanged', async () => {
    const params = {
      pageIndex: 1,
      pageSize: 10,
      body: { groupName: ['山东海化集团有限公司'] },
    }
    httpClient.post.mockResolvedValue({ data: { list: [], totalCount: 0 } })

    const result = await crmApi.searchOpportunities(params)

    expect(httpClient.post).toHaveBeenCalledWith('/api/xiyu/crm/chances/page-list', params)
    expect(result).toEqual({ data: { list: [], totalCount: 0 } })
  })

  it('searchOpportunitiesByTender(): posts blueprint criteria to search-by-tender', async () => {
    const params = {
      tenderer: '山东海化集团有限公司',
      registrationDeadline: '2026-06-03 23:59:00',
      bidOpeningTime: '2026-06-04 23:59:00',
      pageIndex: 1,
      pageSize: 10,
    }
    httpClient.post.mockResolvedValue({ data: { list: [], totalCount: 0 } })

    await crmApi.searchOpportunitiesByTender(params)

    expect(httpClient.post).toHaveBeenCalledWith('/api/xiyu/crm/chances/search-by-tender', params)
  })

  it('getContactPersons(): posts chance id as raw request body', async () => {
    httpClient.post.mockResolvedValue({ data: [] })

    await crmApi.getContactPersons(285001)

    expect(httpClient.post).toHaveBeenCalledWith('/api/xiyu/crm/chances/contact-persons', 285001)
  })
})
