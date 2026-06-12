// Input: qualification store with mocked API module
// Output: qualification store coverage for list loading
// Pos: src/stores/ - Pinia store unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const { qualificationsApi } = vi.hoisted(() => ({
  qualificationsApi: {
    getList: vi.fn(),
    create: vi.fn(),
    delete: vi.fn()
  }
}))

vi.mock('@/api', () => ({
  qualificationsApi,
  isFeatureUnavailableResponse: vi.fn((response) => Boolean(response?.code === 'FEATURE_UNAVAILABLE')),
  getFeaturePlaceholder: vi.fn((response) => response?.title ? ({
    title: response.title,
    message: response.message,
    hint: response.hint
  }) : null)
}))

import { useQualificationStore } from './qualification.js'

describe('useQualificationStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadQualifications(): stores the real qualification list result', async () => {
    qualificationsApi.getList.mockResolvedValue({
      success: true,
      data: [{ id: 1, name: '高新技术企业证书' }]
    })

    const store = useQualificationStore()
    const result = await store.loadQualifications()

    expect(result.success).toBe(true)
    expect(store.qualifications).toEqual([{ id: 1, name: '高新技术企业证书' }])
    expect(store.listFeaturePlaceholder).toBeNull()
  })
})
