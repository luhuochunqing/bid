import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import AiModelSettingsPanel from './AiModelSettingsPanel.vue'
import BaiduOcrSettingsCard from './BaiduOcrSettingsCard.vue'

const getSystemSettings = vi.fn()
const updateSystemSettings = vi.fn()
const testAiModelConnection = vi.fn()
const testBaiduOcrConnection = vi.fn()

vi.mock('@/api', () => ({
  settingsApi: {
    getSystemSettings,
    updateSystemSettings,
    testAiModelConnection,
    testBaiduOcrConnection,
  },
}))

const elMessage = {
  success: vi.fn(),
  error: vi.fn(),
}

vi.mock('element-plus', () => ({
  ElMessage: elMessage,
}))

const { useAiModelSettings } = await import('./useAiModelSettings.js')

describe('useAiModelSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads baidu ocr configuration correctly', async () => {
    getSystemSettings.mockResolvedValueOnce({
      success: true,
      data: {
        systemConfig: { enableAI: true },
        aiModelConfig: { activeProvider: 'deepseek', providers: [] },
        baiduOcrConfig: {
          enabled: true,
          usernameConfigured: true,
          passwordConfigured: true,
          usernameMasked: 'VJ8Z****WlY',
          passwordMasked: 'QE4w****7bKC',
          lastTestStatus: 'success',
          lastTestMessage: '鉴权成功',
        },
      },
    })

    const { load, baiduOcrConfig } = useAiModelSettings()
    await load()

    expect(baiduOcrConfig.value.enabled).toBe(true)
    expect(baiduOcrConfig.value.usernameConfigured).toBe(true)
    expect(baiduOcrConfig.value.usernameMasked).toBe('VJ8Z****WlY')
    expect(baiduOcrConfig.value.lastTestStatus).toBe('success')
  })

  it('tests baidu ocr connection successfully', async () => {
    testBaiduOcrConnection.mockResolvedValueOnce({
      success: true,
      data: {
        status: 'success',
        message: '连接测试成功',
        testedAt: '2026-10-28T14:00:00Z',
      },
    })

    const { testBaiduOcr, baiduOcrConfig } = useAiModelSettings()
    baiduOcrConfig.value.username = 'test-key'
    baiduOcrConfig.value.password = 'test-secret'

    await testBaiduOcr()
    expect(elMessage.success).toHaveBeenCalledWith(expect.stringContaining('尚未验证'))

    expect(testBaiduOcrConnection).toHaveBeenCalledWith({
      username: 'test-key',
      password: 'test-secret',
    })
    expect(baiduOcrConfig.value.lastTestStatus).toBe('success')
  })

  it('compiles and mounts AiModelSettingsPanel with defineModel bindings', () => {
    const wrapper = mount(AiModelSettingsPanel, {
      props: {
        systemConfig: { enableAI: true },
        aiModelConfig: { activeProvider: 'deepseek', providers: [] },
        baiduOcrConfig: { enabled: false, username: '', password: '' },
        loading: false,
        saving: false,
        testingProvider: '',
        testingBaiduOcr: false,
        save: vi.fn(),
        testProvider: vi.fn(),
        testBaiduOcr: vi.fn(),
      },
      global: {
        stubs: {
          ElForm: true,
          ElFormItem: true,
          ElSwitch: true,
          ElRadioGroup: true,
          ElRadioButton: true,
          ElButton: true,
          ElIcon: true,
          BaiduOcrSettingsCard: true,
        },
      },
    })
    expect(wrapper.exists()).toBe(true)
  })
  it('saves gateway credentials and clears plaintext after reloading the masked response', async () => {
    updateSystemSettings.mockResolvedValueOnce({ success: true, data: {
      baiduOcrConfig: { enabled: true, usernameConfigured: true, passwordConfigured: true },
    } })
    const { save, baiduOcrConfig } = useAiModelSettings()
    baiduOcrConfig.value.enabled = true
    baiduOcrConfig.value.username = ' gateway-user '
    baiduOcrConfig.value.password = ' padded-password '
    await save()
    expect(updateSystemSettings).toHaveBeenCalledWith(expect.objectContaining({
      baiduOcrConfig: { enabled: true, username: 'gateway-user', password: ' padded-password ' },
    }))
    expect(baiduOcrConfig.value.username).toBe('')
    expect(baiduOcrConfig.value.password).toBe('')
    expect(baiduOcrConfig.value.passwordConfigured).toBe(true)
  })

  it('renders editable OCR account fields and configuration state without Baidu key labels', () => {
    const wrapper = mount(BaiduOcrSettingsCard, {
      props: { modelValue: { enabled: true, usernameConfigured: true, passwordConfigured: true } },
      global: { stubs: {
        ElForm: { template: '<form><slot /></form>' },
        ElRow: { template: '<div><slot /></div>' },
        ElCol: { template: '<div><slot /></div>' },
        ElFormItem: { props: ['label'], template: '<label>{{ label }}<slot /></label>' },
        ElInput: true, ElSwitch: true, ElTag: { template: '<span><slot /></span>' },
        ElButton: { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' }, ElIcon: true,
      } },
    })
    expect(wrapper.text()).toContain('表格文字识别（OCR）')
    expect(wrapper.text()).toContain('用户名')
    expect(wrapper.text()).toContain('密码')
    expect(wrapper.text()).toContain('凭据已配置')
    expect(wrapper.text()).not.toMatch(/百度|API Key|Secret Key/)
    wrapper.find('button').trigger('click')
    expect(wrapper.emitted('test')).toHaveLength(1)
  })

})
