// Input: taskStatusDictAdminApi module with mocked HTTP client
// Output: admin CRUD + reorder endpoint contract coverage
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../client.js', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn() },
}))
import httpClient from '../client.js'
import { taskStatusDictAdminApi } from './taskStatusDictAdmin.js'

describe('taskStatusDictAdminApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('list issues GET /api/admin/task-status-dict', async () => {
    httpClient.get.mockResolvedValue({ success: true, data: [] })
    await taskStatusDictAdminApi.list()
    expect(httpClient.get).toHaveBeenCalledWith('/api/admin/task-status-dict')
  })

  it('create issues POST with body', async () => {
    httpClient.post.mockResolvedValue({ success: true })
    await taskStatusDictAdminApi.create({ code: 'X', name: 'x' })
    expect(httpClient.post).toHaveBeenCalledWith('/api/admin/task-status-dict', { code: 'X', name: 'x' })
  })

  it('update issues PUT with code in path', async () => {
    httpClient.put.mockResolvedValue({ success: true })
    await taskStatusDictAdminApi.update('TODO', { name: 'new' })
    expect(httpClient.put).toHaveBeenCalledWith('/api/admin/task-status-dict/TODO', { name: 'new' })
  })

  it('disable issues PATCH /{code}/disable', async () => {
    httpClient.patch.mockResolvedValue({ success: true })
    await taskStatusDictAdminApi.disable('TODO')
    expect(httpClient.patch).toHaveBeenCalledWith('/api/admin/task-status-dict/TODO/disable')
  })

  it('enable issues PATCH /{code}/enable', async () => {
    httpClient.patch.mockResolvedValue({ success: true })
    await taskStatusDictAdminApi.enable('TODO')
    expect(httpClient.patch).toHaveBeenCalledWith('/api/admin/task-status-dict/TODO/enable')
  })

  it('reorder issues PATCH /reorder with items', async () => {
    httpClient.patch.mockResolvedValue({ success: true })
    await taskStatusDictAdminApi.reorder([{ code: 'TODO', sortOrder: 10 }])
    expect(httpClient.patch).toHaveBeenCalledWith('/api/admin/task-status-dict/reorder', { items: [{ code: 'TODO', sortOrder: 10 }] })
  })
})
