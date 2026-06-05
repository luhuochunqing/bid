// Input: SubscribeButton component + stubbed useSubscription
// Output: render + emit behavior coverage
// Pos: src/components/common/ - SubscribeButton unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const toggleMock = vi.fn()
const subscribedRef = ref(false)

vi.mock('@/composables/useSubscription', () => ({
  useSubscription: () => ({
    subscribed: subscribedRef,
    loading: ref(false),
    error: ref(null),
    toggle: toggleMock,
    fetchState: vi.fn()
  })
}))

import SubscribeButton from './SubscribeButton.vue'

const globalStubs = {
  'el-button': {
    props: ['type', 'loading', 'plain', 'size', 'ariaLabel'],
    emits: ['click'],
    template: '<button :aria-label="ariaLabel" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-icon': { template: '<i><slot /></i>' }
}

describe('SubscribeButton', () => {
  beforeEach(() => {
    toggleMock.mockReset()
    subscribedRef.value = false
  })

  it('renders 关注 label when not subscribed', () => {
    const wrapper = mount(SubscribeButton, {
      props: { entityType: 'PROJECT', entityId: 42, entityLabel: '项目' },
      global: { stubs: globalStubs }
    })
    expect(wrapper.text()).toContain('关注')
    expect(wrapper.text()).not.toContain('已关注')
  })

  it('emits toggle event after click', async () => {
    toggleMock.mockImplementation(async () => {
      subscribedRef.value = true
    })
    const wrapper = mount(SubscribeButton, {
      props: { entityType: 'PROJECT', entityId: 42 },
      global: { stubs: globalStubs }
    })

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(toggleMock).toHaveBeenCalled()
    expect(wrapper.emitted('toggle')).toBeTruthy()
    expect(wrapper.emitted('toggle')[0]).toEqual([true])
  })
})
