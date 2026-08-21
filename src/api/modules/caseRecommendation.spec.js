// Input: caseRecommendation API module with mocked HTTP client
// Output: score-item & case-recommend contract coverage (bare payloads must not be double-unwrapped)
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

import httpClient from '@/api/client'
import { caseRecommendationApi } from './caseRecommendation.js'

describe('caseRecommendationApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 后端 ScoreItemCaseRecommendController 直接返回 ResponseEntity<List<ScoreItemDTO>>（裸数组，
  // 不走 {success, data} 统一信封）。client.js 拦截器已解包 response.data，
  // 本模块若再取 .data 会得到 undefined，导致评分项下拉恒空（2026-08 线上 bug 回归锁）。
  it('getScoreItems(): returns bare array as-is without double unwrapping', async () => {
    const bareArray = [
      { id: 1, code: '3.1', dim: '技术评分', detail: '技术方案完整可行' },
      { id: 2, code: '5.2', dim: '服务评分', detail: '售后服务体系完善' }
    ]
    httpClient.get.mockResolvedValue(bareArray)

    const result = await caseRecommendationApi.getScoreItems(10)

    expect(httpClient.get).toHaveBeenCalledWith('/api/projects/10/score-items')
    expect(result).toEqual(bareArray)
    expect(Array.isArray(result)).toBe(true)
  })

  // 后端直接返回 ResponseEntity<ScoreItemCaseRecommendResponse>（裸 {total, items}，不走信封）。
  it('recommendCases(): posts scoreItemId/keyword/page/size and returns bare {total, items} as-is', async () => {
    const barePayload = { total: 1, items: [{ id: 101, matchScore: 92, matchLabel: '优质' }] }
    httpClient.post.mockResolvedValue(barePayload)

    const result = await caseRecommendationApi.recommendCases(10, {
      scoreItemId: 1,
      keyword: '高可用',
      page: 0,
      size: 20
    })

    expect(httpClient.post).toHaveBeenCalledWith('/api/projects/10/cases/recommend', {
      scoreItemId: 1,
      keyword: '高可用',
      page: 0,
      size: 20
    })
    expect(result).toEqual(barePayload)
    expect(result.items).toHaveLength(1)
  })

  it('recommendCases(): defaults null scoreItemId, empty keyword and page 0 / size 10', async () => {
    httpClient.post.mockResolvedValue({ total: 0, items: [] })

    await caseRecommendationApi.recommendCases(10)

    expect(httpClient.post).toHaveBeenCalledWith('/api/projects/10/cases/recommend', {
      scoreItemId: null,
      keyword: '',
      page: 0,
      size: 10
    })
  })

  // 案例详情/复用同样直接返回裸对象（KnowledgeCaseController 返回 ResponseEntity，无信封），
  // 同样不可再取 .data（2026-08 review 阻断项：原实现与旧测试均误判为信封端点）。
  it('getCaseDetail(): returns bare case object as-is without double unwrapping', async () => {
    const bareCase = { id: 42, scoringPointTitle: '评分点', responseText: '应答原文' }
    httpClient.get.mockResolvedValue(bareCase)

    const result = await caseRecommendationApi.getCaseDetail(42)

    expect(httpClient.get).toHaveBeenCalledWith('/api/cases/42')
    expect(result).toEqual(bareCase)
  })

  it('reuseCase(): posts to reuse endpoint and returns bare result map as-is', async () => {
    const bareResult = { caseId: 42, newReuseCount: 2 }
    httpClient.post.mockResolvedValue(bareResult)

    const result = await caseRecommendationApi.reuseCase(42, { scoreItemId: 1 })

    expect(httpClient.post).toHaveBeenCalledWith('/api/cases/42/reuse', { scoreItemId: 1 })
    expect(result).toEqual(bareResult)
  })
})
