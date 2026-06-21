import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SOURCE_FILTER_OPTIONS, SOURCE_PLATFORM_OPTIONS } from './constants.js'
import { useTenderSourceConfig } from './useTenderSourceConfig.js'
import { tenderSourcesApi } from '@/api/modules/tenderSources'

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  },
}))

vi.mock('@/api/modules/tenderSources', () => ({
  tenderSourcesApi: {
    testConnection: vi.fn(),
    getConfig: vi.fn(),
    saveConfig: vi.fn(),
  },
}))

describe('useTenderSourceConfig', () => {
  it('keeps source configuration options aligned with the source filter', () => {
    expect(SOURCE_PLATFORM_OPTIONS).toBe(SOURCE_FILTER_OPTIONS)
  })

  beforeEach(() => {
    vi.clearAllMocks()
  })

  function createSubject() {
    return useTenderSourceConfig({
      refreshTenderList: vi.fn(),
      searchForm: ref({ keyword: '' }),
      canSyncExternalSource: ref(true),
    })
  }

  it('tests connection with the unified third-party platform label', async () => {
    tenderSourcesApi.testConnection.mockResolvedValue({ data: { success: true } })
    const subject = createSubject()
    subject.sourceConfig.value = {
      ...subject.sourceConfig.value,
      platforms: ['第三方平台'],
      apiEndpoint: 'https://example.com/api',
      apiKey: 'secret',
    }

    await expect(subject.testConnection()).resolves.toBe(true)
    expect(tenderSourcesApi.testConnection).toHaveBeenCalledWith({
      platform: '第三方平台',
      apiEndpoint: 'https://example.com/api',
      apiKey: 'secret',
    })
  })
})
