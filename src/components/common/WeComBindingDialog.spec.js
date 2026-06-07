// Input: WeComBindingDialog component
// Output: unit tests for the bind/unbind dialog behaviors
// Pos: src/components/common/ - Component test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'

vi.mock('@/api/modules/wecomBinding', () => ({
  wecomBindingApi: {
    bind: vi.fn(),
    unbind: vi.fn(),
    get: vi.fn()
  }
}))

vi.mock('element-plus', async () => {
  const actual = await vi.importActual('element-plus')
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn() }
  }
})

import { wecomBindingApi } from '@/api/modules/wecomBinding'
import WeComBindingDialog from './WeComBindingDialog.vue'

const mountDialog = (props = {}) =>
  mount(WeComBindingDialog, {
    props: {
      modelValue: true,
      userId: 7,
      userName: '张三',
      currentBinding: '',
      ...props
    },
    global: {
      stubs: {
        'el-dialog': {
          template: '<div class="el-dialog-stub"><slot /><div class="el-dialog-footer-stub"><slot name="footer" /></div></div>'
        },
        'el-form': {
          template: '<form><slot /></form>',
          methods: { validate: () => Promise.resolve(true) }
        },
        'el-form-item': { template: '<div class="el-form-item-stub"><slot /></div>' },
        'el-input': {
          props: ['modelValue'],
          template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
        },
        'el-button': {
          props: ['disabled', 'loading'],
          template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
        }
      }
    }
  })

describe('WeComBindingDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('populates form with currentBinding on open', async () => {
    const wrapper = mountDialog({ currentBinding: 'wc_legacy' })
    await flushPromises()
    const input = wrapper.find('input')
    expect(input.element.value).toBe('wc_legacy')
  })

  it('save emits saved with new wecomUserId', async () => {
    wecomBindingApi.bind.mockResolvedValue({ wecomUserId: 'wc_new' })
    const wrapper = mountDialog()

    const input = wrapper.find('input')
    input.element.value = 'wc_new'
    await input.trigger('input')

    const saveButton = wrapper.findAll('button').find(btn => btn.text().includes('保存'))
    await saveButton.trigger('click')
    await flushPromises()

    expect(wecomBindingApi.bind).toHaveBeenCalledWith(7, 'wc_new')
    expect(wrapper.emitted('saved')).toBeTruthy()
    expect(wrapper.emitted('saved')[0][0]).toBe('wc_new')
  })

  it('unbind emits unbound when triggered from an already-bound user', async () => {
    wecomBindingApi.unbind.mockResolvedValue({ success: true })
    const wrapper = mountDialog({ currentBinding: 'wc_old' })
    await nextTick()

    const unbindButton = wrapper.findAll('button').find(btn => btn.text().includes('解除绑定'))
    await unbindButton.trigger('click')
    await flushPromises()

    expect(wecomBindingApi.unbind).toHaveBeenCalledWith(7)
    expect(wrapper.emitted('unbound')).toBeTruthy()
  })

  it('does not show 解除绑定 button when unbound', () => {
    const wrapper = mountDialog({ currentBinding: '' })
    const unbindButton = wrapper.findAll('button').find(btn => btn.text().includes('解除绑定'))
    expect(unbindButton).toBeUndefined()
  })

  it('save button disabled when input blank', () => {
    const wrapper = mountDialog({ currentBinding: '' })
    const saveButton = wrapper.findAll('button').find(btn => btn.text().includes('保存'))
    expect(saveButton.attributes('disabled')).toBeDefined()
  })
})
