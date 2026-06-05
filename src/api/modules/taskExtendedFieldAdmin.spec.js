// Input: taskExtendedFieldAdminApi module with mocked HTTP client
// Output: admin CRUD + reorder endpoint contract coverage
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../client.js', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn() },
}))
import httpClient from '../client.js'
import { taskExtendedFieldAdminApi } from './taskExtendedFieldAdmin.js'

describe('taskExtendedFieldAdminApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('list issues GET /api/admin/task-extended-fields', async () => {
    httpClient.get.mockResolvedValue({ success: true, data: [] })
    await taskExtendedFieldAdminApi.list()
    expect(httpClient.get).toHaveBeenCalledWith('/api/admin/task-extended-fields')
  })

  it('create issues POST with body', async () => {
    httpClient.post.mockResolvedValue({ success: true })
    await taskExtendedFieldAdminApi.create({ fieldKey: 'priority', label: 'Priority' })
    expect(httpClient.post).toHaveBeenCalledWith('/api/admin/task-extended-fields', { fieldKey: 'priority', label: 'Priority' })
  })

  it('update issues PUT with key in path', async () => {
    httpClient.put.mockResolvedValue({ success: true })
    await taskExtendedFieldAdminApi.update('priority', { label: 'New' })
    expect(httpClient.put).toHaveBeenCalledWith('/api/admin/task-extended-fields/priority', { label: 'New' })
  })

  it('disable issues PATCH /{key}/disable', async () => {
    httpClient.patch.mockResolvedValue({ success: true })
    await taskExtendedFieldAdminApi.disable('priority')
    expect(httpClient.patch).toHaveBeenCalledWith('/api/admin/task-extended-fields/priority/disable')
  })

  it('enable issues PATCH /{key}/enable', async () => {
    httpClient.patch.mockResolvedValue({ success: true })
    await taskExtendedFieldAdminApi.enable('priority')
    expect(httpClient.patch).toHaveBeenCalledWith('/api/admin/task-extended-fields/priority/enable')
  })

  it('reorder issues PATCH /reorder with items', async () => {
    httpClient.patch.mockResolvedValue({ success: true })
    await taskExtendedFieldAdminApi.reorder([{ fieldKey: 'priority', sortOrder: 10 }])
    expect(httpClient.patch).toHaveBeenCalledWith('/api/admin/task-extended-fields/reorder', { items: [{ fieldKey: 'priority', sortOrder: 10 }] })
  })
})
