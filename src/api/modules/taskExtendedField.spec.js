// Input: taskExtendedFieldApi module with mocked HTTP client
// Output: task extended field listing endpoint contract coverage
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, it, expect, vi, beforeEach } from 'vitest'
import httpClient from '../client.js'
import { taskExtendedFieldApi } from './taskExtendedField.js'

vi.mock('../client.js', () => ({ default: { get: vi.fn() } }))

describe('taskExtendedFieldApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('lists enabled extended fields via GET /api/task-extended-fields', async () => {
    httpClient.get.mockResolvedValue({ success: true, data: [{ fieldKey: 'priority' }] })
    const result = await taskExtendedFieldApi.list()
    expect(httpClient.get).toHaveBeenCalledWith('/api/task-extended-fields')
    expect(result.data[0].fieldKey).toBe('priority')
  })
})
