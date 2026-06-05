// Input: mentions.js API module
// Output: unit tests for mentionsApi
// Pos: src/api/modules/ - API module test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../client.js', () => ({
  default: {
    post: vi.fn()
  }
}))

import httpClient from '../client.js'
import { mentionsApi } from './mentions.js'

describe('mentionsApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('creates a mention with full payload', async () => {
    httpClient.post.mockResolvedValue({ success: true, data: { mentionCount: 2, notificationId: 100 } })
    const result = await mentionsApi.create({
      content: '@[Alice](1) please review',
      sourceEntityType: 'DOCUMENT',
      sourceEntityId: 42,
      title: '审核请求'
    })
    expect(httpClient.post).toHaveBeenCalledWith('/api/mentions', {
      content: '@[Alice](1) please review',
      sourceEntityType: 'DOCUMENT',
      sourceEntityId: 42,
      title: '审核请求'
    })
    expect(result.mentionCount).toBe(2)
  })

  it('returns response data', async () => {
    httpClient.post.mockResolvedValue({ success: true, data: { mentionCount: 0, notificationId: 0 } })
    const result = await mentionsApi.create({ content: 'plain text', title: 'none' })
    expect(result.mentionCount).toBe(0)
  })
})
