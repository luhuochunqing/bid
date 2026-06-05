// Input: subscriptions API module with mocked HTTP client
// Output: coverage for subscribe/unsubscribe/listMine/check wrappers
// Pos: src/api/modules/ - Subscriptions API unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../client.js', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn()
  }
}))

import httpClient from '../client.js'
import { subscriptionsApi } from './subscriptions.js'

describe('subscriptionsApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('subscribe posts target payload and returns data envelope', async () => {
    httpClient.post.mockResolvedValue({ success: true, data: { subscriptionId: 100 } })

    const result = await subscriptionsApi.subscribe('PROJECT', 42)

    expect(httpClient.post).toHaveBeenCalledWith('/api/subscriptions', {
      targetEntityType: 'PROJECT',
      targetEntityId: 42
    })
    expect(result).toEqual({ subscriptionId: 100 })
  })

  it('unsubscribe sends DELETE with JSON body', async () => {
    httpClient.delete.mockResolvedValue({ success: true, data: { affected: 1 } })

    const result = await subscriptionsApi.unsubscribe('PROJECT', 42)

    expect(httpClient.delete).toHaveBeenCalledWith('/api/subscriptions', {
      data: { targetEntityType: 'PROJECT', targetEntityId: 42 }
    })
    expect(result).toEqual({ affected: 1 })
  })

  it('listMine passes paging params and returns paged data', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }
    })

    const result = await subscriptionsApi.listMine({ page: 0, size: 20 })

    expect(httpClient.get).toHaveBeenCalledWith('/api/subscriptions/me', {
      params: { page: 0, size: 20 }
    })
    expect(result.content).toEqual([])
  })

  it('check encodes path params and returns subscribed flag', async () => {
    httpClient.get.mockResolvedValue({ success: true, data: { subscribed: true } })

    const result = await subscriptionsApi.check('PROJECT', 42)

    expect(httpClient.get).toHaveBeenCalledWith('/api/entities/PROJECT/42/subscription')
    expect(result).toEqual({ subscribed: true })
  })
})
