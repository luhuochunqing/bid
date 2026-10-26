// Input: scoreParse API module and isolated HTTP adapter
// Output: independent estimate refresh endpoint contract verification
// Pos: src/api/modules/ - API boundary regression test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { describe, expect, it, vi } from 'vitest'
import httpClient from '../client.js'
import { scoreParseApi } from './scoreParse.js'

vi.mock('../client.js', () => ({ default: { post: vi.fn() } }))

describe('score estimate refresh API', () => {
  it('posts without a parse or scoring body and preserves the response envelope', async () => {
    const response = { data: { items: [{ id: 1, estimateStatus: 'FRESH' }] } }
    httpClient.post.mockResolvedValue(response)
    expect(await scoreParseApi.refreshEstimates(123)).toBe(response)
    expect(httpClient.post).toHaveBeenCalledWith('/api/projects/123/score-parse/estimates/refresh')
  })
})
