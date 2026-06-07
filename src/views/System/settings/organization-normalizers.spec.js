import { describe, expect, it } from 'vitest'
import {
  buildRolePayload,
  buildUserOrganizationPayload,
  normalizeDeptTree,
  normalizeRoles,
  normalizeUsers,
  toDeptTreeNodes
} from './organization-normalizers'

describe('organization-normalizers', () => {
  it('normalizes department tree rows for persistence and selects', () => {
    const rows = normalizeDeptTree([
      { deptCode: ' SALES ', deptName: ' 销售部 ', parentDeptCode: '', sortOrder: '2' }
    ])

    expect(rows).toEqual([{ deptCode: 'SALES', deptName: '销售部', parentDeptCode: '', sortOrder: 2 }])
    expect(toDeptTreeNodes(rows)).toEqual([{ value: 'SALES', label: '销售部', disabled: false }])
  })

  it('normalizes role rows and role payloads', () => {
    const roles = normalizeRoles([{ code: 'MANAGER', name: '', menuPermissions: ['dashboard', 'dashboard'] }])

    expect(roles[0].code).toBe('manager')
    expect(roles[0].menuPermissions).toEqual(['dashboard'])
    expect(buildRolePayload({ code: 'Bid_Manager', name: '投标经理', enabled: true }).code).toBe('bid_manager')
  })

  it('builds user organization payload from selected department and role', () => {
    const users = normalizeUsers([{ id: 1, fullName: '张三', departmentCode: 'TECH', roleCode: 'staff', enabled: true }])

    expect(users[0].departmentName).toBe('未配置部门')
    expect(buildUserOrganizationPayload({ departmentCode: ' TECH ', roleId: 3, enabled: true }))
      .toEqual({ departmentCode: 'TECH', roleId: 3, enabled: true })
  })
})
