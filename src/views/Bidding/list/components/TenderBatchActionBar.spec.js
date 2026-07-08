import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TenderBatchActionBar from './TenderBatchActionBar.vue'

function mountBar(props = {}) {
  return shallowMount(TenderBatchActionBar, {
    props: {
      selectedCount: 2,
      selectAllChecked: false,
      isIndeterminate: true,
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

describe('TenderBatchActionBar', () => {
  // CO-547: 批量操作按钮（批量分发/领取标讯/批量关注/取消选择）已隐藏，
  // 仅保留已选条数信息与全选复选框。
  it('hides all batch action buttons and only shows selected-count info', () => {
    const wrapper = mountBar()

    expect(wrapper.text()).toContain('已选择 2 条标讯')
    expect(wrapper.text()).not.toContain('批量分发')
    expect(wrapper.text()).not.toContain('领取标讯')
    expect(wrapper.text()).not.toContain('批量关注')
    expect(wrapper.text()).not.toContain('取消选择')
  })
})
