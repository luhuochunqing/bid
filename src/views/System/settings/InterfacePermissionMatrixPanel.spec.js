// Input: InterfacePermissionMatrixPanel with mocked permissionMatrixApi
// Output: rendering coverage for endpoint permission matrix panel
// Pos: src/views/System/settings/ - component tests

import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

const mocks = vi.hoisted(() => ({
  getEndpointPermissions: vi.fn(),
}))

vi.mock('@/api', () => ({
  permissionMatrixApi: { getEndpointPermissions: mocks.getEndpointPermissions },
}))

import InterfacePermissionMatrixPanel from './InterfacePermissionMatrixPanel.vue'

const stubs = {
  ElButton: { template: '<button @click="$emit(\'click\')"><slot /></button>' },
  ElCard: { template: '<section><slot name="header" /><slot /></section>' },
  ElAlert: { props: ['title'], template: '<div>{{ title }}</div>' },
  ElInput: { template: '<input />' },
  ElOption: { template: '<option><slot /></option>' },
  ElSelect: { template: '<select><slot /></select>' },
  ElTag: { template: '<span><slot /></span>' },
  ElTable: {
    props: ['data'],
    template: '<div><div v-for="row in data" :key="row.path"><slot :row="row" /></div></div>',
  },
  ElTableColumn: { template: '<div />' },
}

describe('InterfacePermissionMatrixPanel', () => {
  it('loads endpoint permission rows and renders readonly context', async () => {
    mocks.getEndpointPermissions.mockResolvedValueOnce({
      success: true,
      data: [{
        method: 'GET',
        path: '/api/admin/roles',
        module: 'admin',
        controller: 'AdminRoleController',
        handler: 'listRoles',
        expression: "hasRole('ADMIN')",
        roles: ['admin'],
        riskLevel: 'HIGH',
        source: 'METHOD_PRE_AUTHORIZE',
      }],
    })

    const wrapper = mount(InterfacePermissionMatrixPanel, {
      global: { stubs, directives: { loading: {} } },
    })
    await flushPromises()
    await nextTick()

    expect(mocks.getEndpointPermissions).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('接口权限矩阵')
    expect(wrapper.text()).toContain('当前筛选 1 个')
    expect(wrapper.text()).toContain('入口层权限')
  })
})
