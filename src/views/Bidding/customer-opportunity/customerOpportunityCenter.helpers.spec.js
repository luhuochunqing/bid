import { describe, expect, it } from 'vitest'
import {
  buildBoardSummaries,
  buildOpportunityProjectPayload,
  normalizeConfidence,
  normalizeCustomerPrediction,
} from './customerOpportunityCenter.helpers.js'

describe('customerOpportunityCenter.helpers', () => {
  it('buildOpportunityProjectPayload should create a project request from a selected customer', () => {
    const payload = buildOpportunityProjectPayload(
      {
        customerId: 'CUST-001',
        customerName: '华东某集团',
        region: '上海',
        industry: '制造',
        mainCategories: ['A类', 'B类'],
        prediction: {
          opportunityId: 77,
          suggestedProjectName: '华东某集团 2026采购项目',
          predictedBudgetMin: 120,
          predictedBudgetMax: 160,
          predictedWindow: '2026-04',
          confidence: 0.86,
          reasoningSummary: '采购节奏稳定，建议提前立项',
          evidenceRecords: [201, 202],
        },
      },
      { id: 9 },
      new Date('2026-04-21T08:00:00Z'),
    )

    expect(payload).toMatchObject({
      name: '华东某集团 2026采购项目',
      tenderId: 201,
      managerId: 9,
      teamMembers: [9],
      sourceModule: 'customer-opportunity-center',
      sourceCustomerId: 'CUST-001',
      sourceCustomer: '华东某集团',
      sourceOpportunityId: '77',
      customer: '华东某集团',
      budget: 140,
      industry: '制造',
      region: '上海',
      description: '采购节奏稳定，建议提前立项',
      remark: '预测时间窗口：2026-04；置信度：86%',
      tagsJson: '["A类","B类"]',
    })
    expect(payload.startDate).toBe('2026-04-21T09:00:00')
    expect(payload.endDate).toBe('2026-04-28T18:00:00')
  })

  it('normalizeCustomerPrediction should coerce evidence ids and defaults', () => {
    expect(normalizeCustomerPrediction({ opportunityId: 1, evidenceRecords: ['11', '12', 'bad'], confidence: '0.72' })).toMatchObject({
      opportunityId: 1,
      evidenceRecords: [11, 12],
      confidence: 0.72,
      suggestedProjectName: '待智能研判',
    })
  })

  it('buildBoardSummaries should reflect converted opportunities', () => {
    const summaries = buildBoardSummaries({
      customerInsights: [{ opportunityScore: 91 }],
      customerPredictions: [{ predictedWindow: '2026-04', convertedProjectId: 2 }],
    })

    expect(summaries[0].value).toBe('1')
    expect(summaries[1].value).toBe('1')
    expect(summaries[3].value).toBe('1')
  })

  it('normalizeConfidence should clamp ratio values', () => {
    expect(normalizeConfidence(1.12)).toBe(100)
    expect(normalizeConfidence(-0.5)).toBe(0)
  })
})
