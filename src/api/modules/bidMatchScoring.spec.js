// Input: bidMatchScoring API module with mocked HTTP client
// Output: endpoint and normalizer coverage for custom bid matching scoring
// Pos: src/api/modules/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}))

import httpClient from '@/api/client'
import { bidMatchScoringApi, normalizeMatchScoringModel, normalizeMatchScore } from './bidMatchScoring.js'

describe('bidMatchScoringApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getModels(): reads model list and normalizes dynamic dimensions', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: [
        {
          id: 7,
          modelName: '销售优先模型',
          version: 'v3',
          active: true,
          dimensions: [{
            code: 'customerBudget',
            label: '客户预算',
            enabled: true,
            weight: '35',
            rules: [{
              code: 'budgetKeyword',
              name: '预算关键词',
              type: 'KEYWORD',
              evidenceKey: 'tender.searchText',
              keywords: ['预算覆盖'],
              weight: 100,
            }],
          }],
        },
      ],
    })

    const result = await bidMatchScoringApi.getModels()

    expect(httpClient.get).toHaveBeenCalledWith('/api/bid-match/models')
    expect(result.data[0]).toMatchObject({
      id: 7,
      name: '销售优先模型',
      version: 'v3',
      active: true,
      dimensions: [
        {
          key: 'customerBudget',
          name: '客户预算',
          enabled: true,
          weight: 35,
          rules: [expect.objectContaining({ key: 'budgetKeyword', evidenceKey: 'tender.searchText' })],
        },
      ],
    })
  })

  it('saveModel(): keeps no-code rules and sends the controlled payload', async () => {
    httpClient.put.mockResolvedValue({ success: true, data: { id: 7, name: '模型A', dimensions: [] } })

    await bidMatchScoringApi.saveModel({
      id: 7,
      name: '模型A',
      enabled: true,
      dimensions: [
        {
          key: 'deliveryRisk',
          name: '交付风险',
          enabled: true,
          weight: 100,
          rules: [{
            key: 'deliveryText',
            name: '交付关键词',
            type: 'KEYWORD',
            evidenceKey: 'case.searchText',
            keywords: ['deliveryTeam'],
            weight: 100,
            enabled: true,
          }],
        },
      ],
    })

    expect(httpClient.put).toHaveBeenCalledWith('/api/bid-match/models', {
      id: 7,
      name: '模型A',
      description: '',
      dimensions: [
        {
          code: 'deliveryRisk',
          name: '交付风险',
          enabled: true,
          weight: 100,
          rules: [{
            code: 'deliveryText',
            name: '交付关键词',
            type: 'KEYWORD',
            evidenceKey: 'case.searchText',
            keywords: ['deliveryTeam'],
            minValue: null,
            maxValue: null,
            weight: 100,
            enabled: true,
          }],
        },
      ],
    })
  })

  it('activateModel(): activates a model by id', async () => {
    httpClient.post.mockResolvedValue({ success: true, data: { id: 'm1', active: true } })

    const result = await bidMatchScoringApi.activateModel('m1')

    expect(httpClient.post).toHaveBeenCalledWith('/api/bid-match/models/m1/activate')
    expect(result.data.active).toBe(true)
  })

  it('generateScore(): posts tender id and returns normalized ready score', async () => {
    httpClient.post.mockResolvedValue({
      success: true,
      data: {
        scoreId: 'score-1',
        tenderId: 'T9001',
        overallScore: '86',
        modelVersion: '2026.04',
        state: 'completed',
        dimensionScores: [
          { dimensionKey: 'profit', dimensionName: '利润空间', score: 91, evidence: [{ title: '预算测算' }] },
        ],
      },
    })

    const result = await bidMatchScoringApi.generateScore('T9001')

    expect(httpClient.post).toHaveBeenCalledWith('/api/tenders/T9001/match-score/evaluate', {})
    expect(result.data).toMatchObject({
      id: 'score-1',
      tenderId: 'T9001',
      totalScore: 86,
      status: 'READY',
      dimensions: [{ key: 'profit', name: '利润空间', score: 91 }],
    })
  })

  it('getLatestScore() and getScoreHistory(): read tender scoring records', async () => {
    httpClient.get
      .mockResolvedValueOnce({ success: true, data: { tenderId: 'T9001', status: 'NOT_CONFIGURED' } })
      .mockResolvedValueOnce({ success: true, data: [{ scoreId: 'old', score: 72 }] })

    const latest = await bidMatchScoringApi.getLatestScore('T9001')
    const history = await bidMatchScoringApi.getScoreHistory('T9001')

    expect(httpClient.get).toHaveBeenNthCalledWith(1, '/api/tenders/T9001/match-score/latest')
    expect(httpClient.get).toHaveBeenNthCalledWith(2, '/api/tenders/T9001/match-score/history')
    expect(latest.data.status).toBe('NOT_CONFIGURED')
    expect(history.data[0].totalScore).toBe(72)
  })
})

describe('bidMatchScoring normalizers', () => {
  it('normalizes model aliases without assuming fixed dimension names', () => {
    const model = normalizeMatchScoringModel({
      id: 'm',
      enabled: true,
      dimensions: [{ id: 'customSignal', title: '自定义信号', weight: '100', enabled: 1, rules: [] }],
    })

    expect(model.dimensions).toEqual([
      expect.objectContaining({ key: 'customSignal', name: '自定义信号', weight: 100, enabled: true }),
    ])
  })

  it('normalizes failed score payloads and evidence arrays', () => {
    const score = normalizeMatchScore({
      id: 's1',
      status: 'FAILED',
      failureReason: '评分服务异常',
      dimensions: [{ code: 'x', label: '任意维度', score: '12', evidence: '单条证据' }],
    })

    expect(score.status).toBe('FAILED')
    expect(score.failureReason).toBe('评分服务异常')
    expect(score.dimensions[0]).toMatchObject({
      key: 'x',
      name: '任意维度',
      score: 12,
      evidence: [{ title: '单条证据', content: '' }],
    })
  })
})
