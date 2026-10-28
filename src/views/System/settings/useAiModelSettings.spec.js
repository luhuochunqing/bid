import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import AiModelSettingsPanel from './AiModelSettingsPanel.vue'

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
          apiKeyConfigured: true,
          secretKeyConfigured: true,
          apiKeyMasked: 'VJ8Z****WlY',
          secretKeyMasked: 'QE4w****7bKC',
          lastTestStatus: 'success',
          lastTestMessage: '鉴权成功',
        },
      },
    })

    const { load, baiduOcrConfig } = useAiModelSettings()
    await load()

    expect(baiduOcrConfig.value.enabled).toBe(true)
    expect(baiduOcrConfig.value.apiKeyConfigured).toBe(true)
    expect(baiduOcrConfig.value.apiKeyMasked).toBe('VJ8Z****WlY')
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
    baiduOcrConfig.value.apiKey = 'test-key'
    baiduOcrConfig.value.secretKey = 'test-secret'

    await testBaiduOcr()

    expect(testBaiduOcrConnection).toHaveBeenCalledWith({
      apiKey: 'test-key',
      secretKey: 'test-secret',
    })
    expect(baiduOcrConfig.value.lastTestStatus).toBe('success')
    expect(elMessage.success).toHaveBeenCalledWith('百度智能云表格识别连接测试成功')
  })

  it('compiles and mounts AiModelSettingsPanel with defineModel bindings', () => {
    const wrapper = mount(AiModelSettingsPanel, {
      props: {
        systemConfig: { enableAI: true },
        aiModelConfig: { activeProvider: 'deepseek', providers: [] },
        baiduOcrConfig: { enabled: false, apiKey: '', secretKey: '' },
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
})
