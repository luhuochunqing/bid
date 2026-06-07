import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AssignDialog from './AssignDialog.vue'

const candidates = [
  { id: 1, name: '张三', departmentName: '交付部' },
  { id: 2, name: '李四', departmentName: '销售部' },
]

function mountDialog(props = {}) {
  return shallowMount(AssignDialog, {
    props: {
      modelValue: true,
      tenderId: 100,
      tenderTitle: '测试标讯名称',
      candidates,
      ...props,
    },
    global: {
      stubs: {
        'el-dialog': { template: '<section><slot /><slot name="footer" /></section>' },
        'el-form': { template: '<form><slot /></form>' },
        'el-form-item': { props: ['label'], template: '<label>{{ label }}<slot /></label>' },
        'el-text': { template: '<span><slot /></span>' },
        'el-select': {
          name: 'ElSelect',
          props: ['modelValue'],
          template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', Number($event.target.value))"><slot /></select>'
        },
        'el-option': { template: '<option />' },
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
  it('shows department automatically when assignee is selected locally', async () => {
    const wrapper = mountDialog()

    expect(wrapper.text()).toContain('请先选择项目负责人')

    const select = wrapper.findComponent({ name: 'ElSelect' })
    expect(select.exists()).toBe(true)

    // Trigger local state assignee to 1
    await select.vm.$emit('update:modelValue', 1)

    // The department '交付部' should show up immediately due to local reactivity
    expect(wrapper.text()).toContain('交付部')
    expect(wrapper.text()).not.toContain('请先选择项目负责人')
  })

  it('emits submit event with form payload on confirm', async () => {
    const wrapper = mountDialog()

    // Select assignee 2
    const select = wrapper.findComponent({ name: 'ElSelect' })
    await select.vm.$emit('update:modelValue', 2)

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
