// Input: taskActivityApi and mocked httpClient
// Output: unit tests for task activity API endpoint wiring
// Pos: src/api/modules/ - API module test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { describe, expect, it, vi } from 'vitest'
import { taskActivityApi } from './taskActivity.js'
import httpClient from '../client.js'

vi.mock('../client.js', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

describe('taskActivityApi', () => {
  it('loads task activity from the real API endpoint', async () => {
    httpClient.get.mockResolvedValueOnce({ success: true, data: [] })

    const result = await taskActivityApi.getActivity(99)

    expect(httpClient.get).toHaveBeenCalledWith('/api/tasks/99/activity')
    expect(result).toEqual({ success: true, data: [] })
  })

  it('posts task comments to the real API endpoint', async () => {
    httpClient.post.mockResolvedValueOnce({ success: true, data: { id: 1 } })

    const result = await taskActivityApi.createComment(99, { content: '请 @[Bob](8) 看一下' })

    expect(httpClient.post).toHaveBeenCalledWith('/api/tasks/99/comments', {
      content: '请 @[Bob](8) 看一下',
    })
    expect(result.data.id).toBe(1)
  })
})
