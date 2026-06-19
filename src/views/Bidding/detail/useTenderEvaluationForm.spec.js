import { describe, it, expect } from 'vitest'
import { evaluationToForm, buildApiPayload } from './useTenderEvaluationForm.js'

describe('evaluationToForm', () => {
  it('converts EAV customerInfos to flat row format', () => {
    const evaluation = {
      evaluationBasic: { plannedShortlistedCount: 3, unfavorableItems: 'test' },
      evaluationCustomerInfos: [
        { roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'NAME', value: '张三', valueType: 'TEXT' },
        { roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'CONTACT_INFO', value: '13800138000', valueType: 'TEXT' },
        { roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'CONTACTED', value: '是', valueType: 'DROPDOWN' },
        { roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'INFO_CLEAR_WINNER_BID', value: 'true', valueType: 'SWITCH' },
        { roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'INFO_WIN_RATE_IMPACT', value: 'HIGH', valueType: 'DROPDOWN6' },
      ],
      evaluationRecommendation: { shouldBid: true, reason: '建议投标' },
    }

    const result = evaluationToForm(evaluation)

    expect(result.customerInfo).toHaveLength(1)
    const row = result.customerInfo[0]
    expect(row.roleKey).toBe('OTHER_KEY_DECISION_MAKER_1')
    expect(row.NAME).toBe('张三')
    expect(row.CONTACT_INFO).toBe('13800138000')
    expect(row.CONTACTED).toBe('是')
    expect(row.INFO_CLEAR_WINNER_BID).toBe('true')
    expect(row.INFO_WIN_RATE_IMPACT).toBe('HIGH')
  })

  it('groups multiple roleKeys into separate rows', () => {
    const evaluation = {
      evaluationBasic: {},
      evaluationCustomerInfos: [
        { roleKey: 'PROJECT_HIGHEST_DECISION_MAKER', infoKey: 'NAME', value: '李总', valueType: 'TEXT' },
        { roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'NAME', value: '王经理', valueType: 'TEXT' },
      ],
      evaluationRecommendation: {},
    }

    const result = evaluationToForm(evaluation)

    expect(result.customerInfo).toHaveLength(2)
    expect(result.customerInfo[0].roleKey).toBe('PROJECT_HIGHEST_DECISION_MAKER')
    expect(result.customerInfo[0].NAME).toBe('李总')
    expect(result.customerInfo[1].roleKey).toBe('OTHER_KEY_DECISION_MAKER_1')
    expect(result.customerInfo[1].NAME).toBe('王经理')
  })

  it('handles empty customerInfos', () => {
    const result = evaluationToForm({
      evaluationBasic: {},
      evaluationCustomerInfos: [],
      evaluationRecommendation: {},
    })
    expect(result.customerInfo).toEqual([])
  })

  it('handles null evaluation', () => {
    const result = evaluationToForm(null)
    expect(result.customerInfo).toEqual([])
    expect(result.basic.unfavorableItems).toBe('')
  })

  it('preserves legacy customerInfo field name', () => {
    const evaluation = {
      evaluationBasic: {},
      customerInfo: [
        { roleKey: 'EXPERT_1', infoKey: 'NAME', value: '赵专家', valueType: 'TEXT' },
      ],
      evaluationRecommendation: {},
    }

    const result = evaluationToForm(evaluation)
    expect(result.customerInfo[0].NAME).toBe('赵专家')
  })
})

describe('buildApiPayload', () => {
  it('converts flat customerInfo rows to EAV format with correct valueTypes', () => {
    const form = {
      basic: makeEmptyBasic(),
      customerInfo: [
        {
          roleKey: 'OTHER_KEY_DECISION_MAKER_1',
          NAME: '张三',
          CONTACT_INFO: '13800138000',
          POSITION: '采购总监',
          CONTACTED: '是',
          INFO_CLEAR_WINNER_BID: true,
          INFO_WIN_RATE_IMPACT: 'HIGH',
        },
      ],
      recommendation: { shouldBid: true, reason: '' },
    }

    const payload = buildApiPayload(form)

    const eavRows = payload.evaluationCustomerInfos
    expect(eavRows).toContainEqual(
      expect.objectContaining({ roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'NAME', value: '张三', valueType: 'TEXT' })
    )
    expect(eavRows).toContainEqual(
      expect.objectContaining({ roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'CONTACT_INFO', value: '13800138000', valueType: 'TEXT' })
    )
    expect(eavRows).toContainEqual(
      expect.objectContaining({ roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'POSITION', value: '采购总监', valueType: 'ENUM14' })
    )
    expect(eavRows).toContainEqual(
      expect.objectContaining({ roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'CONTACTED', value: '是', valueType: 'DROPDOWN' })
    )
    expect(eavRows).toContainEqual(
      expect.objectContaining({ roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'INFO_CLEAR_WINNER_BID', value: 'true', valueType: 'SWITCH' })
    )
    expect(eavRows).toContainEqual(
      expect.objectContaining({ roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'INFO_WIN_RATE_IMPACT', value: 'HIGH', valueType: 'DROPDOWN6' })
    )
  })

  it('skips null/empty values in customerInfo', () => {
    const form = {
      basic: makeEmptyBasic(),
      customerInfo: [
        { roleKey: 'OTHER_KEY_DECISION_MAKER_1', NAME: '张三', CONTACT_INFO: '', CONTACTED: null },
      ],
      recommendation: { shouldBid: true, reason: '' },
    }

    const payload = buildApiPayload(form)
    const eavRows = payload.evaluationCustomerInfos

    expect(eavRows.some(r => r.infoKey === 'NAME')).toBe(true)
    expect(eavRows.some(r => r.infoKey === 'CONTACT_INFO')).toBe(false)
    expect(eavRows.some(r => r.infoKey === 'CONTACTED')).toBe(false)
  })

  it('preserves external role rows when building EAV payload', () => {
    const form = {
      basic: makeEmptyBasic(),
      customerInfo: [
        { roleKey: 'EXTERNAL_ROLE_1', NAME: '张三', CONTACT_INFO: '18888888888' },
      ],
      recommendation: { shouldBid: true, reason: '' },
    }

    const payload = buildApiPayload(form)

    expect(payload.evaluationCustomerInfos).toContainEqual(
      expect.objectContaining({ roleKey: 'EXTERNAL_ROLE_1', infoKey: 'NAME', value: '张三', valueType: 'TEXT' })
    )
    expect(payload.evaluationCustomerInfos).toContainEqual(
      expect.objectContaining({ roleKey: 'EXTERNAL_ROLE_1', infoKey: 'CONTACT_INFO', value: '18888888888', valueType: 'TEXT' })
    )
  })
})

function makeEmptyBasic() {
  return {
    plannedShortlistedCount: null,
    mroOfficeFlowAmount: null,
    customerRevenue: null,
    unfavorableItems: '',
    riskAssessment: '',
    contingencyPlan: '',
    processKnowledge: '',
    supportNotes: '',
    projectPlanGap: '',
    projectPlanGapFiles: [],
  }
}
