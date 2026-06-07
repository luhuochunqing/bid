// Input: mounted composable + mocked subscriptionsApi
// Output: useSubscription fetch/toggle behavior coverage
// Pos: src/composables/ - useSubscription unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/modules/subscriptions', () => ({
  subscriptionsApi: {
    check: vi.fn(),
    subscribe: vi.fn(),
    unsubscribe: vi.fn()
  }
}))

import { subscriptionsApi } from '@/api/modules/subscriptions'
import { useSubscription } from './useSubscription.js'

function mountWith(entityType, entityId) {
  let captured
  const Harness = {
    template: '<div />',
    setup() {
      captured = useSubscription(entityType, entityId)
      return {}
    }
  }
  const wrapper = mount(Harness)
  return { wrapper, getState: () => captured }
}

describe('useSubscription', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('fetches initial subscription state on mount', async () => {
    subscriptionsApi.check.mockResolvedValue({ subscribed: true })

    const { getState } = mountWith('PROJECT', 42)
    await flushPromises()

    expect(subscriptionsApi.check).toHaveBeenCalledWith('PROJECT', 42)
    expect(getState().subscribed.value).toBe(true)
  })

  it('toggle from unsubscribed calls subscribe and flips state', async () => {
    subscriptionsApi.check.mockResolvedValue({ subscribed: false })
    subscriptionsApi.subscribe.mockResolvedValue({ subscriptionId: 100 })

    const { getState } = mountWith('PROJECT', 42)
    await flushPromises()

    await getState().toggle()
    await flushPromises()

    expect(subscriptionsApi.subscribe).toHaveBeenCalledWith('PROJECT', 42)
    expect(getState().subscribed.value).toBe(true)
  })

  it('toggle from subscribed calls unsubscribe and flips state', async () => {
    subscriptionsApi.check.mockResolvedValue({ subscribed: true })
    subscriptionsApi.unsubscribe.mockResolvedValue({ affected: 1 })

    const { getState } = mountWith('PROJECT', 42)
    await flushPromises()

    await getState().toggle()
    await flushPromises()

    expect(subscriptionsApi.unsubscribe).toHaveBeenCalledWith('PROJECT', 42)
    expect(getState().subscribed.value).toBe(false)
  })
})
