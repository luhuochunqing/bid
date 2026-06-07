// Input: settingsApi for integrationConfig persistence, organizationIntegrationApi for directory test
// Output: YAPI directory configuration form state, load/save/test actions
// Pos: src/views/System/settings/ - organization integration settings composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { settingsApi } from '@/api'
import { organizationIntegrationApi } from '@/api/modules/systemIntegration.js'

const createDefaultYapiConfig = () => ({
  orgDirectoryBaseUrl: '',
  orgDirectoryAuthClientId: '',
  orgDirectoryAuthClientSecret: '',
  orgDirectoryUserDetailPath: '/users/{userId}',
  orgDirectoryDeptDetailPath: '/departments/{deptId}',
  orgDirectoryUserWindowPath: '',
  orgDirectoryDeptWindowPath: '',
  orgEventSdkEnabled: false,
  orgEventConsumerGroup: 'bid-org-consumer-test',
  orgEventServerRegisterUrl: '',
})

const normalizeConfig = (source = {}) => ({
  ...createDefaultYapiConfig(),
  orgDirectoryBaseUrl: String(source.orgDirectoryBaseUrl || ''),
  orgDirectoryAuthClientId: String(source.orgDirectoryAuthClientId || ''),
  orgDirectoryAuthClientSecret: String(source.orgDirectoryAuthClientSecret || ''),
  orgDirectoryUserDetailPath: String(source.orgDirectoryUserDetailPath || createDefaultYapiConfig().orgDirectoryUserDetailPath),
  orgDirectoryDeptDetailPath: String(source.orgDirectoryDeptDetailPath || createDefaultYapiConfig().orgDirectoryDeptDetailPath),
  orgDirectoryUserWindowPath: String(source.orgDirectoryUserWindowPath || ''),
  orgDirectoryDeptWindowPath: String(source.orgDirectoryDeptWindowPath || ''),
  orgEventSdkEnabled: Boolean(source.orgEventSdkEnabled),
  orgEventConsumerGroup: String(source.orgEventConsumerGroup || createDefaultYapiConfig().orgEventConsumerGroup),
  orgEventServerRegisterUrl: String(source.orgEventServerRegisterUrl || ''),
})

export function useOrganizationIntegrationSettings() {
  const loading = ref(false)
  const saving = ref(false)
  const testing = ref(false)
  const form = ref(createDefaultYapiConfig())
  const testResult = ref(null)

  const load = async () => {
    loading.value = true
    try {
      const result = await settingsApi.getSystemSettings()
      if (!result?.success) throw new Error(result?.message || '获取组织架构集成配置失败')
      form.value = normalizeConfig(result.data?.integrationConfig)
    } catch (error) {
      ElMessage.error(error?.message || '获取组织架构集成配置失败')
    } finally {
      loading.value = false
    }
  }

  const buildSavePayload = () => {
    const payload = {
      ...form.value,
      orgDirectoryAuthClientSecret: form.value.orgDirectoryAuthClientSecret || undefined,
    }
    return { integrationConfig: payload }
  }

  const save = async () => {
    saving.value = true
    try {
      const result = await settingsApi.updateSystemSettings(buildSavePayload())
      if (!result?.success) throw new Error(result?.message || '保存 YAPI 目录配置失败')
      form.value = normalizeConfig(result.data?.integrationConfig)
      ElMessage.success('YAPI 目录配置已保存')
    } catch (error) {
      ElMessage.error(error?.message || '保存 YAPI 目录配置失败')
    } finally {
      saving.value = false
    }
  }

  const testConnection = async () => {
    testing.value = true
    testResult.value = null
    try {
      const result = await organizationIntegrationApi.testDirectoryConnection(form.value)
      testResult.value = result?.success !== false
        ? { success: true, message: 'YAPI 连接测试成功' }
        : { success: false, message: result?.message || '连接测试失败' }
    } catch (error) {
      testResult.value = { success: false, message: error?.message || '连接测试请求失败' }
    } finally {
      testing.value = false
    }
  }

  return {
    loading,
    saving,
    testing,
    form,
    testResult,
    load,
    save,
    testConnection,
  }
}
