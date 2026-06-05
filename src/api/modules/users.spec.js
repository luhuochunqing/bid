// Input: users.js API module
// Output: unit tests for usersApi
// Pos: src/api/modules/ - API module test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../client.js', () => ({
  default: {
    get: vi.fn()
  }
}))

import httpClient from '../client.js'
import { usersApi } from './users.js'

describe('usersApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('searches users with query and default limit', async () => {
    httpClient.get.mockResolvedValue({ success: true, data: [{ id: 1, name: 'Alice', role: 'STAFF' }] })
    const result = await usersApi.search('ali')
    expect(httpClient.get).toHaveBeenCalledWith('/api/users/search', { params: { q: 'ali', limit: 10 } })
    expect(result).toHaveLength(1)
  })

  it('respects custom limit', async () => {
    httpClient.get.mockResolvedValue({ success: true, data: [] })
    await usersApi.search('bob', 5)
    expect(httpClient.get).toHaveBeenCalledWith('/api/users/search', { params: { q: 'bob', limit: 5 } })
  })

  it('loads task assignment candidates from the organization-backed task endpoint', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: [{ userId: 9, name: '张经理', deptName: '投标管理部', roleName: '部门经理' }],
    })

    const result = await usersApi.getTaskAssignmentCandidates({ deptCode: 'BID', roleCode: 'manager' })

    expect(httpClient.get).toHaveBeenCalledWith('/api/tasks/assignment-candidates', {
      params: { deptCode: 'BID', roleCode: 'manager' },
    })
    expect(result).toEqual([{ userId: 9, name: '张经理', deptName: '投标管理部', roleName: '部门经理' }])
  })

  it('propagates HTTP errors', async () => {
    httpClient.get.mockRejectedValue(new Error('Network'))
    await expect(usersApi.search('x')).rejects.toThrow('Network')
  })
})
