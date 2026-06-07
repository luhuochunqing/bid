// Input: system settings API responses
// Output: AI model settings state, persistence and connection-test actions
// Pos: Settings AI model settings composable

import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { settingsApi } from '@/api'

export const AI_PROVIDER_OPTIONS = [
  { code: 'openai', name: 'OpenAI', env: 'OPENAI_API_KEY' },
  { code: 'deepseek', name: 'DeepSeek', env: 'DEEPSEEK_API_KEY' },
  { code: 'qwen', name: '通义千问', env: 'DASHSCOPE_API_KEY / QWEN_API_KEY' },
  { code: 'doubao', name: '豆包', env: 'ARK_API_KEY / DOUBAO_API_KEY' },
]

const DEFAULT_SYSTEM_CONFIG = {
  sysName: '西域数智化投标管理平台',
  depositWarnDays: 7,
  qualWarnDays: 30,
  enableAI: true,
}

const DEFAULT_PROVIDER_CONFIG = {
  openai: {
    providerCode: 'openai',
    providerName: 'OpenAI',
    enabled: true,
    baseUrl: 'https://api.openai.com/v1/chat/completions',
    model: 'gpt-4o-mini',
  },
  deepseek: {
    providerCode: 'deepseek',
    providerName: 'DeepSeek',
    enabled: true,
    baseUrl: 'https://api.deepseek.com/chat/completions',
    model: 'deepseek-chat',
  },
  qwen: {
    providerCode: 'qwen',
    providerName: '通义千问',
    enabled: true,
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions',
    model: 'qwen-plus',
  },
  doubao: {
    providerCode: 'doubao',
    providerName: '豆包',
    enabled: true,
    baseUrl: 'https://ark.cn-beijing.volces.com/api/v3/chat/completions',
    model: 'doubao-1-5-pro-32k-250115',
  },
}

const normalizeProvider = (provider = {}) => {
  const providerCode = String(provider.providerCode || '').trim().toLowerCase()
  const defaults = DEFAULT_PROVIDER_CONFIG[providerCode] || {}
  return {
    ...defaults,
    ...provider,
    providerCode,
    providerName: provider.providerName || defaults.providerName || providerCode,
    enabled: Boolean(provider.enabled ?? defaults.enabled ?? true),
    baseUrl: provider.baseUrl || defaults.baseUrl || '',
    model: provider.model || defaults.model || '',
    apiKeyPlaintext: '',
    apiKeyMasked: provider.apiKeyMasked || '',
    apiKeyConfigured: Boolean(provider.apiKeyConfigured),
    lastTestStatus: provider.lastTestStatus || '',
    lastTestMessage: provider.lastTestMessage || '',
    lastTestAt: provider.lastTestAt || '',
  }
}

const normalizeAiModelConfig = (config = {}) => {
  const providerMap = new Map(
    (Array.isArray(config.providers) ? config.providers : [])
      .map(normalizeProvider)
      .map((provider) => [provider.providerCode, provider]),
  )

  return {
    activeProvider: config.activeProvider || 'deepseek',
    providers: AI_PROVIDER_OPTIONS.map((option) => normalizeProvider({
      ...DEFAULT_PROVIDER_CONFIG[option.code],
      ...providerMap.get(option.code),
    })),
  }
}

export function useAiModelSettings() {
  const loading = ref(false)
  const saving = ref(false)
  const testingProvider = ref('')
  const systemConfig = ref({ ...DEFAULT_SYSTEM_CONFIG })
  const aiModelConfig = ref(normalizeAiModelConfig())

  const activeProvider = computed(() => aiModelConfig.value.providers.find(
    (provider) => provider.providerCode === aiModelConfig.value.activeProvider,
  ))

  const load = async () => {
    loading.value = true
    try {
      const result = await settingsApi.getSystemSettings()
      if (!result?.success) throw new Error(result?.msg || '加载 AI 模型配置失败')
      systemConfig.value = { ...DEFAULT_SYSTEM_CONFIG, ...(result.data?.systemConfig || {}) }
      aiModelConfig.value = normalizeAiModelConfig(result.data?.aiModelConfig)
    } catch (error) {
      ElMessage.error(error?.message || '加载 AI 模型配置失败')
    } finally {
      loading.value = false
    }
  }

  const validateProvider = (provider) => {
    if (!provider?.baseUrl?.trim()) throw new Error('请填写 API 地址')
    if (!provider?.model?.trim()) throw new Error('请填写模型名称')
  }

  const buildPayload = () => ({
    systemConfig: {
      ...systemConfig.value,
      enableAI: Boolean(systemConfig.value.enableAI),
    },
    aiModelConfig: {
      activeProvider: aiModelConfig.value.activeProvider,
      providers: aiModelConfig.value.providers.map((provider) => ({
        providerCode: provider.providerCode,
        enabled: provider.enabled,
        baseUrl: provider.baseUrl,
        model: provider.model,
        apiKeyPlaintext: provider.apiKeyPlaintext,
      })),
    },
  })

  const save = async () => {
    saving.value = true
    try {
      const provider = activeProvider.value
      validateProvider(provider)
      const result = await settingsApi.updateSystemSettings(buildPayload())
      if (!result?.success) throw new Error(result?.msg || '保存 AI 模型配置失败')
      systemConfig.value = { ...DEFAULT_SYSTEM_CONFIG, ...(result.data?.systemConfig || {}) }
      aiModelConfig.value = normalizeAiModelConfig(result.data?.aiModelConfig)
      ElMessage.success('AI 模型配置已保存')
    } catch (error) {
      ElMessage.error(error?.message || '保存 AI 模型配置失败')
    } finally {
      saving.value = false
    }
  }

  const testProvider = async (provider) => {
    testingProvider.value = provider.providerCode
    try {
      validateProvider(provider)
      const result = await settingsApi.testAiModelConnection({
        providerCode: provider.providerCode,
        baseUrl: provider.baseUrl,
        model: provider.model,
        apiKeyPlaintext: provider.apiKeyPlaintext,
      })
      if (!result?.success) throw new Error(result?.msg || '测试连接失败')
      provider.lastTestStatus = result.data?.status || ''
      provider.lastTestMessage = result.data?.msg || ''
      provider.lastTestAt = result.data?.testedAt || new Date().toISOString()
      if (provider.lastTestStatus === 'success') {
        ElMessage.success(`${provider.providerName} 连接测试成功`)
      } else {
        ElMessage.error(provider.lastTestMessage || `${provider.providerName} 连接测试失败`)
      }
    } catch (error) {
      ElMessage.error(error?.message || '测试连接失败')
    } finally {
      testingProvider.value = ''
    }
  }

  return {
    loading,
    saving,
    testingProvider,
    systemConfig,
    aiModelConfig,
    activeProvider,
    load,
    save,
    testProvider,
  }
}
