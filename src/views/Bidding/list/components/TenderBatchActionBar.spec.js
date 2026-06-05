import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TenderBatchActionBar from './TenderBatchActionBar.vue'

function mountBar(props = {}) {
  return shallowMount(TenderBatchActionBar, {
    props: {
      selectedCount: 2,
      selectAllChecked: false,
      isIndeterminate: true,
      canManageTenders: false,
      ...props,
    },
    global: {
      stubs: {
        'el-button': { template: '<button><slot /></button>' },
        'el-checkbox': { template: '<label><slot /></label>' },
        'el-icon': { template: '<span><slot /></span>' },
      },
    },
  })
}

describe('TenderBatchActionBar permissions', () => {
  it('hides batch management actions from staff users', () => {
    const wrapper = mountBar()

    expect(wrapper.text()).toContain('已选择 2 条标讯')
    expect(wrapper.text()).toContain('取消选择')
    expect(wrapper.text()).not.toContain('批量分发')
    expect(wrapper.text()).not.toContain('领取标讯')
    expect(wrapper.text()).not.toContain('批量关注')
  })

  it('shows batch management actions for manager and admin users', () => {
    const wrapper = mountBar({ canManageTenders: true })

    expect(wrapper.text()).toContain('批量分发')
    expect(wrapper.text()).toContain('领取标讯')
    expect(wrapper.text()).toContain('批量关注')
  })
})
