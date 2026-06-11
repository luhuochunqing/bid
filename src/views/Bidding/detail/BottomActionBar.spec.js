// Component spec for BottomActionBar (fixed bottom action bar for tender detail).
//
// Behaviour:
// - Hidden when `actions` prop is empty
// - Renders el-button for each action item
// - Emits 'action' with action.key on button click
// - Renders icon when action.icon matches known icon name

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import BottomActionBar from './BottomActionBar.vue'

// Stub @element-plus/icons-vue to avoid importing actual SVG icon components
vi.mock('@element-plus/icons-vue', () => ({
  Edit: { name: 'Edit', render: () => {} },
}))

function stubAttrs(stub) {
  const el = stub.element
  return {
    'data-button-type': el.getAttribute('data-button-type'),
    'data-icon': el.getAttribute('data-icon'),
  }
}

const globalStubs = {
  ElButton: {
    name: 'ElButton',
    props: ['type', 'icon', 'size'],
    template:
      '<button :data-button-type="type" :data-icon="!!icon ? \'true\' : \'false\'" data-test="action-btn" @click="$emit(\'click\', $event)"><slot /></button>',
  },
}

describe('BottomActionBar', () => {
  it('does not render when actions is empty', () => {
    const wrapper = mount(BottomActionBar, {
      props: { actions: [] },
      global: { stubs: globalStubs },
    })
    expect(wrapper.find('.bottom-action-bar').exists()).toBe(false)
  })

  it('renders buttons when actions are provided', () => {
    const actions = [
      { key: 'save', label: '保存', type: 'primary', icon: null },
      { key: 'submit', label: '提交评审', type: 'primary', icon: null },
    ]
    const wrapper = mount(BottomActionBar, {
      props: { actions },
      global: { stubs: globalStubs },
    })
    expect(wrapper.find('.form-action-bar').exists()).toBe(true)

    const buttons = wrapper.findAll('button[data-test="action-btn"]')
    expect(buttons).toHaveLength(2)
    expect(buttons[0].text()).toBe('保存')
    expect(buttons[1].text()).toBe('提交评审')
  })

  it('emits action event with action key when a button is clicked', async () => {
    const actions = [
      { key: 'submit', label: '提交', type: 'primary', icon: null },
    ]
    const wrapper = mount(BottomActionBar, {
      props: { actions },
      global: { stubs: globalStubs },
    })
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('action')).toBeTruthy()
    expect(wrapper.emitted('action')[0]).toEqual(['submit'])
  })

  it('renders multiple action icons correctly', async () => {
    const actions = [
      { key: 'edit', label: '编辑', type: 'primary', icon: 'edit' },
    ]
    const wrapper = mount(BottomActionBar, {
      props: { actions },
      global: { stubs: globalStubs },
    })
    const button = wrapper.find('button[data-test="action-btn"]')
    expect(button.exists()).toBe(true)
    expect(button.text()).toBe('编辑')
    // icon prop should be truthy (Edit component object from icons map)
    expect(button.attributes('data-icon')).toBe('true')
  })
})
