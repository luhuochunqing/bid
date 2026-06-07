// Input: system settings API wrappers for integrationConfig persistence
// Output: OA integration form state and load/save actions
// Pos: System settings integration section

import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { settingsApi } from '@/api'

const createDefaultIntegrationConfig = () => ({
  oaEnabled: false,
  oaUrl: '',
  callbackUrl: '',
  apiKey: '',
  orgEnabled: false,
  orgSystem: 'dingtalk',
  orgAppKey: '',
  orgAppSecret: '',
  ssoEnabled: false,
  aiBaseUrl: '',
  aiModel: '',
  ipWhitelist: '',
})

const normalizeConfig = (source = {}) => ({
  ...createDefaultIntegrationConfig(),
  ...source,
  oaEnabled: Boolean(source.oaEnabled),
  ssoEnabled: Boolean(source.ssoEnabled),
  oaUrl: String(source.oaUrl || '').trim(),
  callbackUrl: String(source.callbackUrl || '').trim(),
  apiKey: source.apiKey == null ? '' : String(source.apiKey),
  orgEnabled: source.orgEnabled == null ? false : Boolean(source.orgEnabled),
  orgSystem: String(source.orgSystem || createDefaultIntegrationConfig().orgSystem),
  orgAppKey: source.orgAppKey == null ? '' : String(source.orgAppKey),
  orgAppSecret: source.orgAppSecret == null ? '' : String(source.orgAppSecret),
  aiBaseUrl: String(source.aiBaseUrl || '').trim(),
  aiModel: String(source.aiModel || '').trim(),
  ipWhitelist: String(source.ipWhitelist || '').trim(),
})

const buildFormFromConfig = (config) => ({
  oaEnabled: Boolean(config.oaEnabled),
  oaUrl: config.oaUrl || '',
  callbackUrl: config.callbackUrl || '',
  apiKey: '',
})

const sanitize = (value) => (value ?? '').trim()

export function useSystemIntegrationSettings() {
  const loading = ref(false)
  const saving = ref(false)
  const form = ref({
    oaEnabled: false,
    oaUrl: '',
    callbackUrl: '',
    apiKey: '',
  })
  const rawConfig = ref(createDefaultIntegrationConfig())
  const secretConfigured = computed(() => {
    const secret = sanitize(rawConfig.value.apiKey)
    return Boolean(secret) && secret !== 'sk_xiyu_bid_server_default'
  })

  const load = async () => {
    loading.value = true
    try {
      const result = await settingsApi.getSystemSettings()
      if (!result?.success) throw new Error(result?.msg || '获取系统集成配置失败')
      rawConfig.value = normalizeConfig(result.data?.integrationConfig)
      form.value = buildFormFromConfig(rawConfig.value)
    } catch (error) {
      ElMessage.error(error?.message || '获取系统集成配置失败')
    } finally {
      loading.value = false
    }
  }

  const buildPayload = () => {
    const payload = {
      ...rawConfig.value,
      oaEnabled: form.value.oaEnabled,
      oaUrl: sanitize(form.value.oaUrl),
      callbackUrl: sanitize(form.value.callbackUrl),
    }
    if (form.value.apiKey && form.value.apiKey.trim()) {
      payload.apiKey = form.value.apiKey.trim()
    }
    return {
      integrationConfig: payload,
    }
  }

  const save = async () => {
    saving.value = true
    try {
      const result = await settingsApi.updateSystemSettings(buildPayload())
      if (!result?.success) throw new Error(result?.msg || '保存系统集成配置失败')
      rawConfig.value = normalizeConfig(result.data?.integrationConfig)
      form.value = buildFormFromConfig(rawConfig.value)
      ElMessage.success('泛微 OA 配置已保存')
    } catch (error) {
      ElMessage.error(error?.message || '保存系统集成配置失败')
    } finally {
      saving.value = false
    }
  }

  return {
    loading,
    saving,
    form,
    rawConfig,
    secretConfigured,
    load,
    save,
  }
}
