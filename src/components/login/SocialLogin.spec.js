import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import SocialLogin from './SocialLogin.vue'

vi.mock('@/api/modules/auth', () => ({
  authApi: {
    getWeComAuthorizeParams: vi.fn(),
  },
}))

import { authApi } from '@/api/modules/auth'

describe('SocialLogin', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 默认 window.location.origin
    Object.defineProperty(window, 'location', {
      value: { origin: 'https://bid.example.com', href: '' },
      writable: true,
    })
  })

  it('点击企微登录按钮 → 构造的 URL 使用 oauth2/authorize 公式（工作台跳转 + 扫码兼容）', async () => {
    authApi.getWeComAuthorizeParams.mockResolvedValue({
      data: {
        state: 'test_state_123',
        appid: 'ww_test_corp_id',
        agentid: '1000002',
      },
    })

    const wrapper = mount(SocialLogin, {
      global: {
        stubs: {
          'el-button': {
            template: '<button @click="$emit(\'click\')"><slot /></button>',
          },
        },
      },
    })

    await wrapper.find('.wecom-button').trigger('click')
    // 等异步 URL 构造 + window.location.href 赋值
    await new Promise((r) => setTimeout(r, 0))

    expect(authApi.getWeComAuthorizeParams).toHaveBeenCalled()
    expect(window.location.href).toContain(
      'https://open.weixin.qq.com/connect/oauth2/authorize'
    )
    expect(window.location.href).toContain('appid=ww_test_corp_id')
    expect(window.location.href).toContain('agentid=1000002')
    expect(window.location.href).toContain('response_type=code')
    expect(window.location.href).toContain('scope=snsapi_base')
    expect(window.location.href).toContain('state=test_state_123')
    expect(window.location.href).toContain('#wechat_redirect')
    // redirect_uri 必须是编码后的 origin + /login
    expect(window.location.href).toContain(
      'redirect_uri=' + encodeURIComponent('https://bid.example.com/login')
    )
  })

  it('appid 或 agentid 缺失 → 不跳转，提示管理员', async () => {
    authApi.getWeComAuthorizeParams.mockResolvedValue({
      data: {
        state: 's',
        appid: '',
        agentid: '',
      },
    })

    const wrapper = mount(SocialLogin, {
      global: {
        stubs: {
          'el-button': {
            template: '<button @click="$emit(\'click\')"><slot /></button>',
          },
        },
      },
    })

    await wrapper.find('.wecom-button').trigger('click')
    await new Promise((r) => setTimeout(r, 0))

    expect(window.location.href).toBe('')
  })
})
