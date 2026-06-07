// Input: system integration settings composable with mocked settingsApi
// Output: load / save behavior and OA 表单字段映射的单元覆盖
// Pos: src/views/System/settings/ - composable unit tests

import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const getSystemSettings = vi.fn()
const updateSystemSettings = vi.fn()

vi.mock('@/api/modules/settings', () => ({
  settingsApi: {
    getSystemSettings,
    updateSystemSettings,
  },
}))

const elMessage = {
  success: vi.fn(),
  error: vi.fn(),
}

vi.mock('element-plus', () => ({
  ElMessage: elMessage,
}))

const { useSystemIntegrationSettings } = await import('./useSystemIntegrationSettings.js')

function createHarness() {
  return defineComponent({
    template: '<div />',
    setup() {
      return useSystemIntegrationSettings()
    },
  })
}

describe('useSystemIntegrationSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getSystemSettings.mockResolvedValue({
      success: true,
      data: {
        integrationConfig: {
          oaEnabled: true,
          oaUrl: 'https://oa.example.com',
          callbackUrl: 'https://bid.example.com/api/integrations/oa/weaver/callback',
          apiKey: 'oa-secret-123',
          orgEnabled: false,
          orgSystem: 'dingtalk',
          orgAppKey: '',
          orgAppSecret: '',
          ssoEnabled: false,
          aiBaseUrl: '',
          aiModel: '',
          ipWhitelist: '',
        },
      },
    })
    updateSystemSettings.mockResolvedValue({
      success: true,
      data: {
        integrationConfig: {
          oaEnabled: true,
          oaUrl: 'https://oa.example.com',
          callbackUrl: 'https://bid.example.com/api/integrations/oa/weaver/callback',
          apiKey: 'oa-secret-123',
          orgEnabled: false,
          orgSystem: 'dingtalk',
          orgAppKey: '',
          orgAppSecret: '',
          ssoEnabled: false,
          aiBaseUrl: '',
          aiModel: '',
          ipWhitelist: '',
        },
      },
    })
  })

  it('load() loads OA fields and normalizes form', async () => {
    const wrapper = mount(createHarness())
    await wrapper.vm.load()
    await flushPromises()

    expect(getSystemSettings).toHaveBeenCalled()
    expect(wrapper.vm.form).toMatchObject({
      oaEnabled: true,
      oaUrl: 'https://oa.example.com',
      callbackUrl: 'https://bid.example.com/api/integrations/oa/weaver/callback',
      apiKey: '',
    })
    expect(wrapper.vm.secretConfigured).toBe(true)
  })

  it('save() keeps existing secret when input key blank', async () => {
    const wrapper = mount(createHarness())
    await wrapper.vm.load()
    await flushPromises()

    wrapper.vm.form.apiKey = ''
    wrapper.vm.form.oaUrl = 'https://oa-new.example.com'
    await wrapper.vm.save()
    await flushPromises()

    const sentPayload = updateSystemSettings.mock.calls[0][0]
    expect(sentPayload.integrationConfig.oaUrl).toBe('https://oa-new.example.com')
    expect(sentPayload.integrationConfig.apiKey).toBe('oa-secret-123')
  })

  it('save() updates integration config when secret is provided', async () => {
    const wrapper = mount(createHarness())
    await wrapper.vm.load()
    await flushPromises()

    wrapper.vm.form.apiKey = 'new-secret'
    await wrapper.vm.save()
    await flushPromises()

    const sentPayload = updateSystemSettings.mock.calls[0][0]
    expect(sentPayload.integrationConfig.apiKey).toBe('new-secret')
    expect(elMessage.success).toHaveBeenCalledWith('泛微 OA 配置已保存')
  })

  it('load() reports error message on failed response', async () => {
    getSystemSettings.mockResolvedValue({ success: false, msg: 'load failed' })
    const wrapper = mount(createHarness())

    await wrapper.vm.load()
    await flushPromises()

    expect(elMessage.error).toHaveBeenCalledWith('load failed')
  })

  it('save() reports error message on failed response', async () => {
    updateSystemSettings.mockResolvedValue({ success: false, msg: 'save failed' })
    const wrapper = mount(createHarness())
    await wrapper.vm.load()
    await flushPromises()

    await wrapper.vm.save()
    await flushPromises()

    expect(elMessage.error).toHaveBeenCalledWith('save failed')
  })
})
