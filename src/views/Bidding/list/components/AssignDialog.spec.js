import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AssignDialog from './AssignDialog.vue'

function mountDialog(props = {}) {
  return shallowMount(AssignDialog, {
    props: {
      modelValue: true,
      tenderId: 100,
      tenderTitle: '测试标讯名称',
      ...props,
    },
    global: {
      stubs: {
        'el-dialog': { template: '<section><slot /><slot name="footer" /></section>' },
        'el-form': { template: '<form><slot /></form>' },
        'el-form-item': { props: ['label'], template: '<label>{{ label }}<slot /></label>' },
        'el-text': { template: '<span><slot /></span>' },
        UserPicker: {
          name: 'UserPicker',
          props: ['modelValue', 'mode', 'context', 'placeholder'],
          emits: ['update:modelValue', 'select'],
          template: '<div class="user-picker-stub" />',
        },
        'el-button': { template: '<button><slot /></button>' },
        'el-input': {
          name: 'ElInput',
          props: ['modelValue'],
          template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)"></textarea>'
        },
      },
    },
  })
}

describe('AssignDialog', () => {
  it('renders UserPicker with candidates mode and tender context', () => {
    const wrapper = mountDialog()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.exists()).toBe(true)
    expect(picker.props('mode')).toBe('candidates')
    expect(picker.props('context')).toBe('tender')
  })

  it('shows department automatically when user is selected via @select', async () => {
    const wrapper = mountDialog()

    expect(wrapper.text()).toContain('请先选择项目负责人')

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    await picker.vm.$emit('select', { id: 1, name: '张三', departmentName: '交付部' })

    expect(wrapper.text()).toContain('交付部')
    expect(wrapper.text()).not.toContain('请先选择项目负责人')
  })

  it('emits submit event with form payload on confirm', async () => {
    const wrapper = mountDialog()

    // Select assignee via UserPicker v-model
    const picker = wrapper.findComponent({ name: 'UserPicker' })
    await picker.vm.$emit('update:modelValue', 2)

    // Fill remark
    const input = wrapper.findComponent({ name: 'ElInput' })
    await input.vm.$emit('update:modelValue', '请尽快处理')

    // Click confirm
    const buttons = wrapper.findAll('button')
    const confirmButton = buttons.find((btn) => btn.text().includes('确认指派'))
    expect(confirmButton).toBeDefined()
    await confirmButton.trigger('click')

    expect(wrapper.emitted('submit')).toBeDefined()
    expect(wrapper.emitted('submit')[0][0]).toEqual({
      tenderId: 100,
      assignee: 2,
      remark: '请尽快处理',
    })
  })

  it('does not contain the priority field', () => {
    const wrapper = mountDialog()
    expect(wrapper.text()).not.toContain('优先级')
  })
})
