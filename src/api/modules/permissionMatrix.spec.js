// Input: permissionMatrixApi with mocked httpClient
// Output: endpoint permission matrix API normalization coverage
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn() }
}))

import httpClient from '@/api/client'
import { permissionMatrixApi } from './permissionMatrix.js'

describe('permissionMatrixApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads endpoint permission rows from the admin-only backend endpoint', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: [{
        method: 'get',
        path: '/api/alerts/history/unresolved',
        module: 'alerts',
        controller: 'AlertHistoryController',
        handler: 'getUnresolvedAlertHistories',
        expression: "hasAnyRole('ADMIN', 'MANAGER')",
        allowedRoles: ['ADMIN', 'MANAGER'],
        accessLevel: 'ADMIN_MANAGER',
        riskLevel: 'MEDIUM',
        configurable: false,
        source: 'METHOD_PRE_AUTHORIZE',
      }],
    })

    const result = await permissionMatrixApi.getEndpointPermissions()

    expect(httpClient.get).toHaveBeenCalledWith('/api/admin/permissions/endpoints')
    expect(result.data[0]).toMatchObject({
      method: 'GET',
      path: '/api/alerts/history/unresolved',
      roles: ['admin', 'manager'],
      configurable: false,
    })
  })
})
