// Input: customerOpportunityApi backed by mocked httpClient
// Output: normalized customer opportunity contract coverage
// Pos: src/api/modules/__tests__/ - API module unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn() }
}))

import httpClient from '@/api/client'
import {
  customerOpportunityApi,
  normalizeCustomerInsight,
  normalizeCustomerPrediction,
  normalizeCustomerPurchase,
} from '../customerOpportunity.js'

describe('customerOpportunityApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('normalizes insight, purchase, and prediction payloads to the page contract', () => {
    expect(normalizeCustomerInsight({ purchaserHash: 'hash-1', purchaserName: '华北集团', opportunityScore: '88' })).toEqual({
      customerId: 'hash-1',
      customerName: '华北集团',
      region: '',
      industry: '',
      salesRep: '',
      opportunityScore: 88,
      predictedNextWindow: '',
      status: 'watch',
      mainCategories: [],
      avgBudget: 0,
      cycleType: '',
    })

    expect(normalizeCustomerPurchase({ id: 11, purchaserHash: 'hash-1', createdAt: '2026-04-10', budget: '120' })).toEqual({
      recordId: 11,
      customerId: 'hash-1',
      publishDate: '2026-04-10',
      title: '',
      category: '',
      budget: 120,
      isKey: false,
      extractedTags: [],
    })

    expect(normalizeCustomerPrediction({ id: 9, purchaserHash: 'hash-1', evidenceRecords: ['1', '2', 'bad'], confidence: '0.72' })).toEqual({
      opportunityId: 9,
      customerId: 'hash-1',
      suggestedProjectName: '待智能研判',
      predictedCategory: '---',
      predictedBudgetMin: 0,
      predictedBudgetMax: 0,
      predictedWindow: '待判断',
      confidence: 0.72,
      reasoningSummary: '当前数据不足，暂无法生成高置信度预测。',
      evidenceRecords: [1, 2],
      convertedProjectId: null,
    })
  })

  it('keeps getCustomerInsights response normalized for downstream page state', async () => {
    httpClient.get.mockResolvedValue({
      success: true,
      data: [{ purchaserHash: 'hash-2', purchaserName: '华东制造中心', opportunityScore: '91', status: 'recommend' }],
    })

    const response = await customerOpportunityApi.getCustomerInsights({ status: 'recommend' })

    expect(httpClient.get).toHaveBeenCalledWith('/api/customer-opportunities/insights', {
      params: { status: 'recommend' }
    })
    expect(response.data).toEqual([
      {
        customerId: 'hash-2',
        customerName: '华东制造中心',
        region: '',
        industry: '',
        salesRep: '',
        opportunityScore: 91,
        predictedNextWindow: '',
        status: 'recommend',
        mainCategories: [],
        avgBudget: 0,
        cycleType: '',
      },
    ])
  })
})
