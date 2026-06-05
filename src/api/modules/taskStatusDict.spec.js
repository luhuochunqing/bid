// Input: taskStatusDictApi module with mocked HTTP client
// Output: task status dictionary listing endpoint contract coverage
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, it, expect, vi, beforeEach } from 'vitest'
import httpClient from '../client.js'
import { taskStatusDictApi } from './taskStatusDict.js'

vi.mock('../client.js', () => ({ default: { get: vi.fn() } }))

describe('taskStatusDictApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('lists enabled statuses via GET /api/task-status-dict', async () => {
    httpClient.get.mockResolvedValue({ success: true, data: [{ code: 'TODO' }] })
    const result = await taskStatusDictApi.list()
    expect(httpClient.get).toHaveBeenCalledWith('/api/task-status-dict')
    expect(result.data[0].code).toBe('TODO')
  })
})
