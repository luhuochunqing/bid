// Input: NotificationPanel.vue component
// Output: unit tests for body display and jump arrow indicator
// Pos: src/components/common/ - Component test
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import NotificationPanel from './NotificationPanel.vue'
import { useNotificationStore } from '@/stores/notifications'

vi.mock('element-plus', () => ({
  ElMessage: {
    info: vi.fn()
  }
}))

describe('NotificationPanel', () => {
  let store

  beforeEach(() => {
    setActivePinia(createPinia())
    store = useNotificationStore()
  })

  const mountPanel = () => mount(NotificationPanel, {
    global: {
      mocks: {
        $router: { push: vi.fn() }
      }
    }
  })

  it('renders notification item with body when item.body exists', () => {
    const wrapper = mountPanel()

    store.$patch({
      notifications: [
        {
          id: 1,
          notificationId: 100,
          title: 'Test notification',
          body: 'This is the body text',
          type: 'INFO',
          read: false,
          createdAt: new Date().toISOString(),
          sourceEntityType: 'PROJECT',
          sourceEntityId: 42
        }
      ],
      loading: false
    })

    return new Promise(resolve => {
      setTimeout(() => {
        const bodyEl = wrapper.find('.notification-item-desc')
        expect(bodyEl.exists()).toBe(true)
        expect(bodyEl.text()).toBe('This is the body text')
        resolve()
      }, 0)
    })
  })

  it('does not render body element when item.body is null', () => {
    const wrapper = mountPanel()

    store.$patch({
      notifications: [
        {
          id: 1,
          notificationId: 100,
          title: 'Test notification',
          body: null,
          type: 'INFO',
          read: false,
          createdAt: new Date().toISOString(),
          sourceEntityType: null,
          sourceEntityId: null
        }
      ],
      loading: false
    })

    return new Promise(resolve => {
      setTimeout(() => {
        const bodyEl = wrapper.find('.notification-item-desc')
        expect(bodyEl.exists()).toBe(false)
        resolve()
      }, 0)
    })
  })

  it('renders arrow icon when notification has valid route', () => {
    const wrapper = mountPanel()

    store.$patch({
      notifications: [
        {
          id: 1,
          notificationId: 100,
          title: 'Test notification',
          body: 'Body text',
          type: 'INFO',
          read: false,
          createdAt: new Date().toISOString(),
          sourceEntityType: 'PROJECT',
          sourceEntityId: 42
        }
      ],
      loading: false
    })

    return new Promise(resolve => {
      setTimeout(() => {
        const arrowEl = wrapper.find('.notification-item-arrow')
        expect(arrowEl.exists()).toBe(true)
        resolve()
      }, 0)
    })
  })

  it('does not render arrow icon when notification has no valid route', () => {
    const wrapper = mountPanel()

    store.$patch({
      notifications: [
        {
          id: 1,
          notificationId: 100,
          title: 'Test notification',
          body: 'Body text',
          type: 'INFO',
          read: false,
          createdAt: new Date().toISOString(),
          sourceEntityType: null,
          sourceEntityId: null
        }
      ],
      loading: false
    })

    return new Promise(resolve => {
      setTimeout(() => {
        const arrowEl = wrapper.find('.notification-item-arrow')
        expect(arrowEl.exists()).toBe(false)
        resolve()
      }, 0)
    })
  })

  it('adds clickable class when notification has valid route', () => {
    const wrapper = mountPanel()

    store.$patch({
      notifications: [
        {
          id: 1,
          notificationId: 100,
          title: 'Test notification',
          type: 'INFO',
          read: false,
          createdAt: new Date().toISOString(),
          sourceEntityType: 'PROJECT',
          sourceEntityId: 42
        }
      ],
      loading: false
    })

    return new Promise(resolve => {
      setTimeout(() => {
        const itemEl = wrapper.find('.notification-item')
        expect(itemEl.classes()).toContain('notification-item--clickable')
        resolve()
      }, 0)
    })
  })
})