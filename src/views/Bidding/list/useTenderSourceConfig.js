// Input: tender source config API, search state
// Output: source config state plus persistent save/sync actions (server-side)
// Pos: src/views/Bidding/list/ - External tender source composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DEFAULT_FETCH_RESULT, DEFAULT_SOURCE_CONFIG, EXTERNAL_PLATFORM_SOURCE_LABEL } from './constants.js'
import {
  summarizeExternalSyncResult,
} from './helpers.js'
import { tenderSourcesApi } from '@/api/modules/tenderSources'

export function useTenderSourceConfig({
  externalSyncApi = null,
  refreshTenderList,
  searchForm,
  canSyncExternalSource,
}) {
  const showSourceConfig = ref(false)
  const savingConfig = ref(false)
  const testingConnection = ref(false)
  const fetchingTenders = ref(false)
  const lastSyncTime = ref('暂未同步')
  const sourceConfig = ref({ ...DEFAULT_SOURCE_CONFIG })
  const fetchResult = ref({ ...DEFAULT_FETCH_RESULT })

  /**
   * 从后端加载配置。
   * 在对话框打开时调用。
   */
  const loadSavedConfig = async () => {
    try {
      const response = await tenderSourcesApi.getConfig()
      if (response?.data) {
        const config = response.data
        // 将后端返回的配置映射到前端格式
        sourceConfig.value = {
          platforms: Array.isArray(config.platforms) ? config.platforms : [],
          apiEndpoint: config.apiEndpoint || '',
          apiKey: '', // 密钥不从前端返回
          keywords: config.keywords ? (Array.isArray(config.keywords) ? config.keywords : String(config.keywords).split(',').filter(Boolean)) : [],
          regions: Array.isArray(config.regions) ? config.regions : [],
          minBudget: Number(config.budgetMin || 0),
          maxBudget: Number(config.budgetMax || 1000),
          autoSync: Boolean(config.autoSync),
          syncInterval: config.syncIntervalMinutes ? Math.round(config.syncIntervalMinutes / 60) : 6,
          autoSave: true,
          enableDedupe: config.autoDedupe !== false,
        }
      }
    } catch {
      // 后端无配置时使用默认值
      ElMessage.warning('配置加载失败，已使用默认配置')
      sourceConfig.value = { ...DEFAULT_SOURCE_CONFIG }
    }
  }

  /**
   * 保存配置到后端。
   */
  const saveSourceConfig = async () => {
    if (sourceConfig.value.platforms.length === 0) {
      ElMessage.warning('请至少选择一个标讯源平台')
      return false
    }

    savingConfig.value = true
    try {
      const payload = {
        platforms: sourceConfig.value.platforms,
        apiEndpoint: sourceConfig.value.apiEndpoint,
        apiKey: sourceConfig.value.apiKey || undefined,
        keywords: Array.isArray(sourceConfig.value.keywords)
          ? sourceConfig.value.keywords.join(',')
          : sourceConfig.value.keywords || '',
        regions: sourceConfig.value.regions,
        budgetMin: Number(sourceConfig.value.minBudget || 0),
        budgetMax: Number(sourceConfig.value.maxBudget || 1000),
        autoSync: Boolean(sourceConfig.value.autoSync),
        syncIntervalMinutes: sourceConfig.value.autoSync
          ? (sourceConfig.value.syncInterval || 6) * 60
          : 1440,
        autoDedupe: sourceConfig.value.enableDedupe !== false,
      }

      await tenderSourcesApi.saveConfig(payload)
      ElMessage.success('标讯源配置已保存')
      showSourceConfig.value = false
      return true
    } catch (error) {
      const errorMsg = error?.response?.data?.msg || error?.message || '保存失败，请重试'
      ElMessage.error(errorMsg)
      return false
    } finally {
      savingConfig.value = false
    }
  }

  const testConnection = async () => {
    if (sourceConfig.value.platforms.length === 0) {
      ElMessage.warning('请先选择标讯源平台')
      return false
    }

    if (!sourceConfig.value.platforms.includes(EXTERNAL_PLATFORM_SOURCE_LABEL)) {
      ElMessage.warning(`仅支持测试「${EXTERNAL_PLATFORM_SOURCE_LABEL}」平台的连接`)
      return false
    }

    if (!sourceConfig.value.apiEndpoint || !sourceConfig.value.apiKey) {
      ElMessage.warning('请先填写API端点和密钥')
      return false
    }

    testingConnection.value = true
    try {
      const response = await tenderSourcesApi.testConnection({
        platform: EXTERNAL_PLATFORM_SOURCE_LABEL,
        apiEndpoint: sourceConfig.value.apiEndpoint,
        apiKey: sourceConfig.value.apiKey,
      })

      if (response?.data?.success) {
        ElMessage.success('连接测试成功')
        return true
      } else {
        ElMessage.error(response?.data?.msg || '连接失败，请检查API端点和密钥')
        return false
      }
    } catch (error) {
      const errorMessage = error?.response?.data?.msg || error?.message || '连接失败，请检查API端点和密钥'
      ElMessage.error(errorMessage)
      return false
    } finally {
      testingConnection.value = false
    }
  }

  const syncExternalTenders = async () => {
    if (!canSyncExternalSource.value) {
      ElMessage.error('当前账号无权同步外部标讯')
      return null
    }
    if (sourceConfig.value.platforms.length === 0) {
      ElMessage.warning('请先配置标讯源')
      showSourceConfig.value = true
      return null
    }
    if (typeof externalSyncApi !== 'function') {
      ElMessage.warning('外部同步接口暂不可用')
      return null
    }

    fetchingTenders.value = true
    try {
      const response = await externalSyncApi({
        keyword: searchForm.value?.keyword || '',
        pageSize: 20,
      })
      fetchResult.value = summarizeExternalSyncResult(response)
      lastSyncTime.value = new Date().toLocaleString('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      })
      await refreshTenderList()
      ElMessage.success(`标讯同步完成：新增 ${fetchResult.value.saved} 条，跳过 ${fetchResult.value.skipped} 条`)
      return response
    } catch {
      ElMessage.error('获取标讯失败，请检查网络或后端服务')
      return null
    } finally {
      fetchingTenders.value = false
    }
  }

  return {
    showSourceConfig,
    sourceConfig,
    savingConfig,
    testingConnection,
    fetchingTenders,
    lastSyncTime,
    fetchResult,
    loadSavedConfig,
    saveSourceConfig,
    testConnection,
    syncExternalTenders,
  }
}
