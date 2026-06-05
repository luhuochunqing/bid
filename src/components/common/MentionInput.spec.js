// Input: MentionInput.vue component
// Output: unit tests for MentionInput
// Pos: src/components/common/ - Component test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'

vi.mock('@/api/modules/users', () => ({
  usersApi: {
    search: vi.fn()
  }
}))

import { usersApi } from '@/api/modules/users'
import MentionInput from './MentionInput.vue'

const mountInput = (props = {}) =>
  mount(MentionInput, {
    props,
    global: {
      stubs: {
        'el-input': {
          props: ['modelValue'],
          emits: ['update:modelValue', 'input', 'keydown'],
          template: `<textarea ref="textarea" :value="modelValue"
            @input="$emit('update:modelValue', $event.target.value); $emit('input', $event.target.value)"
            @keydown="$emit('keydown', $event)" />`
        }
      }
    }
  })

describe('MentionInput', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    usersApi.search.mockResolvedValue({ data: [{ id: 7, name: 'Alice', role: 'STAFF' }] })
  })

  it('mounts with initial value', () => {
    const wrapper = mountInput({ modelValue: 'hello' })
    expect(wrapper.find('textarea').element.value).toBe('hello')
  })

  it('emits update:modelValue on input', async () => {
    const wrapper = mountInput({ modelValue: '' })
    const textarea = wrapper.find('textarea')
    textarea.element.value = 'hi'
    await textarea.trigger('input')
    await nextTick()
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    expect(emitted[emitted.length - 1][0]).toBe('hi')
  })

  it('triggers search when @ prefix typed', async () => {
    const wrapper = mountInput({ modelValue: '', debounceMs: 0 })
    const textarea = wrapper.find('textarea')
    textarea.element.value = 'hey @ali'
    Object.defineProperty(textarea.element, 'selectionStart', { value: 8, configurable: true })
    await textarea.trigger('input')
    await flushPromises()
    await new Promise(resolve => setTimeout(resolve, 5))
    await flushPromises()
    expect(usersApi.search).toHaveBeenCalled()
    expect(usersApi.search.mock.calls[0][0]).toBe('ali')
  })

  it('submit emits parsed content', async () => {
    const wrapper = mountInput({ modelValue: '@[Alice](7) hello' })
    wrapper.vm.submit()
    const emitted = wrapper.emitted('parsed')
    expect(emitted).toBeTruthy()
    expect(emitted[0][0].mentionedUserIds).toEqual([7])
    expect(emitted[0][0].plainText).toBe('@Alice hello')
  })
})
