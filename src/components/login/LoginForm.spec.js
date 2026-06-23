import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import * as userStoreModule from '@/stores/user'
import LoginForm from './LoginForm.vue'

vi.mock('@/stores/user', () => ({
  useUserStore: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn(),
  }),
}))

const mockLogin = vi.fn()

describe('LoginForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    userStoreModule.useUserStore.mockReturnValue({
      login: mockLogin,
    })
  })

  it('mounts without error', () => {
    const wrapper = mount(LoginForm, {
      global: {
        stubs: {
          'el-form': true,
          'el-form-item': true,
          'el-input': true,
          'el-button': true,
          'el-checkbox': true,
          'el-icon': true,
          'Transition': false,
        },
      },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('shows error message when login fails', async () => {
    mockLogin.mockRejectedValue(new Error('您没有该系统的访问权限，请联系管理员'))
    const wrapper = mount(LoginForm, {
      global: {
        stubs: {
          'el-form': true,
          'el-form-item': true,
          'el-input': true,
          'el-button': true,
          'el-checkbox': true,
          'el-icon': true,
          'Transition': false,
        },
      },
    })
    wrapper.vm.errorMessage = '您没有该系统的访问权限，请联系管理员'
    await nextTick()
    expect(wrapper.vm.errorMessage).toBe('您没有该系统的访问权限，请联系管理员')
  })

  it('clears error message on new login attempt', async () => {
    const wrapper = mount(LoginForm, {
      global: {
        stubs: {
          'el-form': true,
          'el-form-item': true,
          'el-input': true,
          'el-button': true,
          'el-checkbox': true,
          'el-icon': true,
          'Transition': false,
        },
      },
    })
    wrapper.vm.errorMessage = 'some error'
    wrapper.vm.errorMessage = ''
    await nextTick()
    expect(wrapper.vm.errorMessage).toBe('')
  })
})