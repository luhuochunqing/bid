// Input: weComIntegrationApi calls for WeChat Work config management
// Output: reactive form state + load / save / testConn / sendTest actions
// Pos: src/views/System/settings/ - settings composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { weComIntegrationApi } from '@/api/modules/systemIntegration'

const createDefaultForm = () => ({
  corpId: '',
  agentId: null,
  corpSecret: '',
  ssoEnabled: false,
  messageEnabled: false,
  notifyUserIds: '',
})

const buildSavePayload = (form) => {
  const payload = {
    corpId: form.corpId,
    agentId: form.agentId,
    ssoEnabled: form.ssoEnabled,
    messageEnabled: form.messageEnabled,
    notifyUserIds: (form.notifyUserIds ?? '').trim(),
  }
  if (form.corpSecret) {
    payload.corpSecret = form.corpSecret
  }
  return payload
}

const applyConfigToForm = (data) => ({
  corpId: data.corpId ?? '',
  agentId: data.agentId ?? null,
  corpSecret: '',
  ssoEnabled: Boolean(data.ssoEnabled),
  messageEnabled: Boolean(data.messageEnabled),
  notifyUserIds: data.notifyUserIds ?? '',
})

export function useWeComSettings() {
  const loading = ref(false)
  const saving = ref(false)
  const testing = ref(false)
  const sending = ref(false)
  const form = ref(createDefaultForm())
  const secretConfigured = ref(false)
  const testResult = ref(null)
  const testSendResult = ref(null)

  const load = async () => {
    loading.value = true
    try {
      const result = await weComIntegrationApi.getConfig()
      if (!result?.success) throw new Error(result?.msg || '获取企业微信配置失败')
      form.value = applyConfigToForm(result.data)
      secretConfigured.value = Boolean(result.data?.secretConfigured)
    } catch (error) {
      ElMessage.error(error?.message || '获取企业微信配置失败')
    } finally {
      loading.value = false
    }
  }

  const save = async () => {
    saving.value = true
    try {
      const result = await weComIntegrationApi.saveConfig(buildSavePayload(form.value))
      if (!result?.success) throw new Error(result?.msg || '保存企业微信配置失败')
      form.value = applyConfigToForm(result.data)
      secretConfigured.value = Boolean(result.data?.secretConfigured)
      ElMessage.success('企业微信配置已保存')
    } catch (error) {
      ElMessage.error(error?.message || '保存企业微信配置失败')
    } finally {
      saving.value = false
    }
  }

  const testConn = async () => {
    testing.value = true
    try {
      const result = await weComIntegrationApi.testConnection()
      if (!result?.success) throw new Error(result?.msg || '连接测试请求失败')
      testResult.value = { ...result.data }
    } catch (error) {
      testResult.value = { success: false, message: error?.message || '连接测试失败', probedAt: null }
    } finally {
      testing.value = false
    }
  }

  const sendTest = async (payload = {}) => {
    sending.value = true
    try {
      const data = await weComIntegrationApi.sendTestMessage(payload)
      testSendResult.value = { ...data }
    } catch (error) {
      testSendResult.value = { success: false, errmsg: error?.message || '发送测试消息失败' }
    } finally {
      sending.value = false
    }
  }

  return {
    loading,
    saving,
    testing,
    sending,
    form,
    secretConfigured,
    testResult,
    testSendResult,
    load,
    save,
    testConn,
    sendTest,
  }
}

