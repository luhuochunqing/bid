// Input: wecomBinding.js API module
// Output: unit tests for admin binding wrappers
// Pos: src/api/modules/ - API module test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../client.js', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

import httpClient from '../client.js'
import { wecomBindingApi } from './wecomBinding.js'

describe('wecomBindingApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('get reads current binding for a user', async () => {
    httpClient.get.mockResolvedValue({ success: true, data: { wecomUserId: 'wc_007' } })

    const result = await wecomBindingApi.get(7)

    expect(httpClient.get).toHaveBeenCalledWith('/api/admin/users/7/wecom-binding')
    expect(result).toEqual({ wecomUserId: 'wc_007' })
  })

  it('bind sends PUT with wecomUserId payload', async () => {
    httpClient.put.mockResolvedValue({ success: true, data: { wecomUserId: 'wc_123' } })

    const result = await wecomBindingApi.bind(7, 'wc_123')

    expect(httpClient.put).toHaveBeenCalledWith('/api/admin/users/7/wecom-binding', {
      wecomUserId: 'wc_123'
    })
    expect(result.wecomUserId).toBe('wc_123')
  })

  it('unbind sends DELETE', async () => {
    httpClient.delete.mockResolvedValue({ success: true, data: { released: true } })

    const result = await wecomBindingApi.unbind(7)

    expect(httpClient.delete).toHaveBeenCalledWith('/api/admin/users/7/wecom-binding')
    expect(result).toEqual({ released: true })
  })

  it('encodes user id in path', async () => {
    httpClient.get.mockResolvedValue({ success: true, data: { wecomUserId: null } })

    await wecomBindingApi.get('foo/bar')

    expect(httpClient.get).toHaveBeenCalledWith('/api/admin/users/foo%2Fbar/wecom-binding')
  })
})
