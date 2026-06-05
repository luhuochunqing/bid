// Input: tendersApi.create 调用
// Output: 断言发出 POST 时携带 Idempotency-Key
// Pos: tests - verifies frontend idempotency key injection
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

vi.mock('../client.js', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

import httpClient from '../client.js'
import { tendersApi } from './tenders.js'

describe('tendersApi idempotency', () => {
  beforeEach(() => {
    httpClient.post.mockReset()
    httpClient.post.mockResolvedValue({ success: true, data: { id: 1 } })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('injects Idempotency-Key header on create', async () => {
    await tendersApi.create({ title: 'x' })

    const [url, body, config] = httpClient.post.mock.calls[0]
    expect(url).toBe('/api/tenders')
    expect(body).toEqual({ title: 'x' })
    expect(config).toBeDefined()
    expect(config.headers['Idempotency-Key']).toBeDefined()
    expect(config.headers['Idempotency-Key']).toMatch(/^[0-9a-zA-Z-]{16,}$/)
  })

  it('produces unique key per call', async () => {
    await tendersApi.create({ title: 'a' })
    await tendersApi.create({ title: 'b' })

    const first = httpClient.post.mock.calls[0][2].headers['Idempotency-Key']
    const second = httpClient.post.mock.calls[1][2].headers['Idempotency-Key']
    expect(first).not.toBe(second)
  })
})
