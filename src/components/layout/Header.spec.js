import { shallowMount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, getActivePinia, setActivePinia } from 'pinia'

import Header from './Header.vue'
import { useUserStore } from '@/stores/user'

const messageSuccess = vi.hoisted(() => vi.fn())
const messageError = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    error: messageError,
    info: vi.fn(),
    success: messageSuccess,
    warning: vi.fn(),
  },
}))

vi.mock('@/stores/notifications', () => ({
  useNotificationStore: () => ({ unreadCount: 0 }),
}))

vi.mock('@/composables/useNotifications', () => ({
  useNotifications: vi.fn(),
}))

vi.mock('@/api', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
    refreshToken: vi.fn(),
    getCurrentUser: vi.fn(),
    loginByWeCom: vi.fn(),
  },
}))
vi.mock('@/api/authStoreBridge.js', () => ({
  registerAuthStoreBridge: vi.fn(),
}))
vi.mock('@/api/modules/auth.js', () => ({
  clearAuthState: vi.fn(),
  hasPersistentSession: vi.fn(() => false),
}))
vi.mock('@/api/modules/settings.js', () => ({
  persistRuntimeSettings: vi.fn(),
}))
vi.mock('@/api/session.js', () => ({
  getStoredUser: vi.fn(() => null),
  persistUserHint: vi.fn(),
}))
const navigateToLogin = vi.hoisted(() => vi.fn())

vi.mock('@/router/sessionNavigation.js', () => ({
  navigateToLogin,
}))

const globalStubs = {
  NotificationPanel: { template: '<div />' },
  'el-icon': { template: '<span><slot /></span>' },
  'el-popover': { template: '<div><slot name="reference" /><slot /></div>' },
  'el-badge': { template: '<span><slot /></span>' },
  'el-dropdown': {
    name: 'ElDropdown',
    emits: ['command'],
    template: '<div data-test="user-dropdown"><slot /><slot name="dropdown" /></div>',
  },
  'el-dropdown-menu': { template: '<div><slot /></div>' },
  'el-dropdown-item': { template: '<div><slot /></div>' },
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-input': { template: '<div><slot name="prefix" /></div>' },
  'el-button': { template: '<button><slot /></button>' },
}

let pinia

function mountHeader() {
  return shallowMount(Header, {
    global: {
      plugins: [pinia],
      stubs: globalStubs,
    },
  })
}

const forbiddenGuestText = '\u6e38\u5ba2'

describe('Header user display', () => {
beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  pinia = getActivePinia()
})

  it('does not render an anonymous identity while session is empty', () => {
    const wrapper = mountHeader()

    expect(wrapper.text()).toContain('用户')
    expect(wrapper.text()).not.toContain(forbiddenGuestText)
  })

  it('shows loading state while restoring session', async () => {
    const wrapper = mountHeader()
    const userStore = useUserStore()

    userStore.isRestoringSession = true
    await nextTick()

    expect(wrapper.text()).toContain('加载中')
    expect(wrapper.text()).not.toContain(forbiddenGuestText)
  })

  it('uses current user name and role without anonymous fallback', async () => {
    const wrapper = mountHeader()
    const userStore = useUserStore()

    userStore.currentUser = {
      name: '张三',
      role: 'bid-Team',
      roleName: '员工',
      menuPermissions: [],
    }
    await nextTick()

    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('员工')
    expect(wrapper.text()).not.toContain(forbiddenGuestText)
  })

  it('clears session and navigates to login when logout command is selected', async () => {
    const wrapper = mountHeader()
    const userStore = useUserStore()
    userStore.currentUser = {
      name: '张三',
      role: 'bid-Team',
      roleName: '员工',
      menuPermissions: [],
    }

    const dropdown = wrapper.findComponent({ name: 'ElDropdown' })
    await dropdown.vm.$emit('command', 'logout')
    await flushPromises()

    expect(userStore.currentUser).toBeNull()
    expect(navigateToLogin).toHaveBeenCalledTimes(1)
    expect(messageSuccess).toHaveBeenCalledWith('已退出登录')
    expect(messageError).not.toHaveBeenCalled()
  })
})
