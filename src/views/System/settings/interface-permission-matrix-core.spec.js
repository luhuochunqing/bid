// Input: endpoint permission matrix rows and filters
// Output: pure filtering and display helper coverage
// Pos: src/views/System/settings/ - pure core tests

import { describe, expect, it } from 'vitest'
import {
  filterEndpointPermissions,
  hasEndpointRole,
  permissionRoleTags,
  riskTagType,
  sourceLabel,
} from './interface-permission-matrix-core.js'

const rows = [
  { method: 'GET', path: '/api/admin/roles', module: 'admin', controller: 'AdminRoleController', roles: ['admin'], source: 'METHOD_PRE_AUTHORIZE', riskLevel: 'HIGH' },
  { method: 'GET', path: '/api/alerts/history/unresolved', module: 'alerts', controller: 'AlertHistoryController', roles: ['admin', 'manager'], source: 'METHOD_PRE_AUTHORIZE', riskLevel: 'MEDIUM' },
  { method: 'POST', path: '/api/projects', module: 'project', controller: 'ProjectController', roles: ['admin', 'manager', 'staff'], source: 'DEFAULT_AUTHENTICATED', riskLevel: 'LOW' },
]

describe('interface-permission-matrix-core', () => {
  it('filters by keyword, method, module and role', () => {
    const result = filterEndpointPermissions(rows, {
      keyword: 'alert',
      method: 'GET',
      module: 'alerts',
      role: 'manager',
      riskLevel: '',
      source: '',
    })

    expect(result).toEqual([rows[1]])
  })

  it('derives role tags and labels without mutating rows', () => {
    expect(hasEndpointRole(rows[0], 'staff')).toBe(false)
    expect(permissionRoleTags(rows[1])).toEqual(['管理员', '经理'])
    expect(riskTagType('HIGH')).toBe('danger')
    expect(sourceLabel('METHOD_PRE_AUTHORIZE')).toBe('后端注解')
  })
})
