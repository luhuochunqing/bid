import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import WarehouseFilterBar from './WarehouseFilterBar.vue'

vi.mock('@/stores/user.js', () => ({
  useUserStore: () => ({ userRole: 'admin', currentUser: { role: 'admin' } })
}))

describe('WarehouseFilterBar', () => {
  it('renders total count', () => {
    const wrapper = mount(WarehouseFilterBar, {
      props: { filters: {}, total: 42, selectedCount: 0 },
      global: {
        stubs: {
          'el-icon': true, 'el-button': true, 'el-select': true,
          'el-input': true, 'el-form': true, 'el-form-item': true,
          'el-date-picker': true, 'el-checkbox': true
        }
      }
    })
    expect(wrapper.find('.filter-bar').exists()).toBe(true)
    expect(wrapper.text()).toContain('42')
  })
})
