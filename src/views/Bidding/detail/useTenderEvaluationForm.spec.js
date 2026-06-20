import { describe, it, expect } from 'vitest'
import { CUSTOMER_INFO_COLUMNS } from './components/customerInfoMatrixConfig.js'
import { evaluationToForm, buildApiPayload, VT } from './useTenderEvaluationForm.js'

describe('evaluationToForm', () => {
  it('converts EAV customerInfos to flat row format', () => {
    const evaluation = {
      evaluationBasic: { plannedShortlistedCount: 3, unfavorableItems: 'test' },
      evaluationCustomerInfos: [
        { roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'NAME', value: '张三', valueType: 'TEXT' },
        { roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'CONTACT_INFO', value: '13800138000', valueType: 'TEXT' },
        { roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'CONTACTED', value: 'true', valueType: 'DROPDOWN' },
        { roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'INFO_CLEAR_WINNER_BID', value: 'true', valueType: 'SWITCH' },
        { roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'INFO_WIN_RATE_IMPACT', value: '4', valueType: 'DROPDOWN6' },
      ],
      evaluationRecommendation: { shouldBid: true, reason: '建议投标' },
    }

    const result = evaluationToForm(evaluation)

    expect(result.customerInfo).toHaveLength(1)
    const row = result.customerInfo[0]
    expect(row.roleKey).toBe('OTHER_KEY_DECISION_MAKER_1')
    expect(row.NAME).toBe('张三')
    expect(row.CONTACT_INFO).toBe('13800138000')
    expect(row.CONTACTED).toBe(true)
    expect(row.INFO_CLEAR_WINNER_BID).toBe(true)
    expect(row.INFO_WIN_RATE_IMPACT).toBe('4')
  })

  it('converts tender 285 external-role EAV rows to one flat visible row', () => {
    const evaluation = {
      evaluationBasic: {},
      evaluationCustomerInfos: [
        { roleKey: 'EXTERNAL_ROLE_1', infoKey: 'CAN_GET_KEY_INFO', value: 'true', valueType: 'TEXT' },
        { roleKey: 'EXTERNAL_ROLE_1', infoKey: 'CAN_REMOVE_ADVERSE', value: 'true', valueType: 'TEXT' },
        { roleKey: 'EXTERNAL_ROLE_1', infoKey: 'CAN_SYNC_EVAL', value: 'true', valueType: 'TEXT' },
        { roleKey: 'EXTERNAL_ROLE_1', infoKey: 'CONTACT_INFO', value: '18888888888', valueType: 'TEXT' },
        { roleKey: 'EXTERNAL_ROLE_1', infoKey: 'INFO_TENDENCY_BASIS', value: '客户明确偏向西域', valueType: 'TEXT' },
      ],
      evaluationRecommendation: {},
    }

    const result = evaluationToForm(evaluation)

    expect(result.customerInfo).toEqual([
      expect.objectContaining({
        roleKey: 'EXTERNAL_ROLE_1',
        roleLabel: '外部对接人1',
        CAN_GET_KEY_INFO: true,
        CAN_REMOVE_ADVERSE: true,
        CAN_SYNC_EVAL: true,
        CONTACT_INFO: '18888888888',
        INFO_TENDENCY_BASIS: '客户明确偏向西域',
      }),
    ])
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
  it('uses explicit valueTypes for all 14 customer info fields', () => {
    const row = CUSTOMER_INFO_COLUMNS.reduce((acc, col) => {
      acc[col.key] = col.key === 'INFO_CLEAR_WINNER_BID' ? true : `value-${col.key}`
      return acc
    }, { roleKey: 'OTHER_KEY_DECISION_MAKER_1' })
    const form = {
      basic: makeEmptyBasic(),
      customerInfo: [row],
      recommendation: { shouldBid: true, reason: '' },
    }

    const payload = buildApiPayload(form)

    expect(CUSTOMER_INFO_COLUMNS).toHaveLength(14)
    expect(payload.evaluationCustomerInfos).toHaveLength(14)
    for (const col of CUSTOMER_INFO_COLUMNS) {
      expect(VT[col.key]).toBeDefined()
      expect(payload.evaluationCustomerInfos).toContainEqual(
        expect.objectContaining({
          roleKey: 'OTHER_KEY_DECISION_MAKER_1',
          infoKey: col.key,
          valueType: VT[col.key],
        })
      )
    }
  })

  it('converts flat customerInfo rows to EAV format with correct valueTypes', () => {
    const form = {
      basic: makeEmptyBasic(),
      customerInfo: [
        {
          roleKey: 'OTHER_KEY_DECISION_MAKER_1',
          NAME: '张三',
          CONTACT_INFO: '13800138000',
          POSITION: '1',
          CONTACTED: true,
          INFO_CLEAR_WINNER_BID: true,
          INFO_WIN_RATE_IMPACT: '4',
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
      expect.objectContaining({ roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'POSITION', value: '1', valueType: 'ENUM14' })
    )
    expect(eavRows).toContainEqual(
      expect.objectContaining({ roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'CONTACTED', value: 'true', valueType: 'DROPDOWN' })
    )
    expect(eavRows).toContainEqual(
      expect.objectContaining({ roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'INFO_CLEAR_WINNER_BID', value: 'true', valueType: 'SWITCH' })
    )
    expect(eavRows).toContainEqual(
      expect.objectContaining({ roleKey: 'OTHER_KEY_DECISION_MAKER_1', infoKey: 'INFO_WIN_RATE_IMPACT', value: '4', valueType: 'DROPDOWN6' })
    )
  })

  it('keeps numeric index values required by integration contract', () => {
    const form = {
      basic: makeEmptyBasic(),
      customerInfo: [
        {
          roleKey: 'EXTERNAL_ROLE_1',
          POSITION: '1',
          CONTACT_METHOD: '3',
          TENDENCY: '2',
          INFO_WIN_RATE_IMPACT: '4',
          INFO_CLEAR_WINNER_BID: true,
          CONTACTED: false,
        },
      ],
      recommendation: { shouldBid: true, reason: '' },
    }

    const eavRows = buildApiPayload(form).evaluationCustomerInfos

    expect(eavRows).toContainEqual(
      expect.objectContaining({ roleKey: 'EXTERNAL_ROLE_1', infoKey: 'POSITION', value: '1', valueType: 'ENUM14' })
    )
    expect(eavRows).toContainEqual(
      expect.objectContaining({ roleKey: 'EXTERNAL_ROLE_1', infoKey: 'CONTACT_METHOD', value: '3', valueType: 'ENUM7' })
    )
    expect(eavRows).toContainEqual(
      expect.objectContaining({ roleKey: 'EXTERNAL_ROLE_1', infoKey: 'TENDENCY', value: '2', valueType: 'DROPDOWN' })
    )
    expect(eavRows).toContainEqual(
      expect.objectContaining({ roleKey: 'EXTERNAL_ROLE_1', infoKey: 'INFO_WIN_RATE_IMPACT', value: '4', valueType: 'DROPDOWN6' })
    )
    expect(eavRows).toContainEqual(
      expect.objectContaining({ roleKey: 'EXTERNAL_ROLE_1', infoKey: 'INFO_CLEAR_WINNER_BID', value: 'true', valueType: 'SWITCH' })
    )
    expect(eavRows).toContainEqual(
      expect.objectContaining({ roleKey: 'EXTERNAL_ROLE_1', infoKey: 'CONTACTED', value: 'false', valueType: 'DROPDOWN' })
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

  // CO-262: buildApiPayload 必须透传 projectPlanGapFiles，否则 CRM 回填的 GAP 附件无法持久化
  it('CO-262: 透传 projectPlanGapFiles 到 evaluationBasic payload', () => {
    const form = {
      basic: {
        ...makeEmptyBasic(),
        projectPlanGapFiles: [
          { fileName: 'GAP附件', fileUrl: 'https://crm.example.com/gap.pdf' },
        ],
      },
      customerInfo: [],
      recommendation: { shouldBid: true, reason: '' },
    }

    const payload = buildApiPayload(form)

    expect(payload.evaluationBasic.projectPlanGapFiles).toEqual([
      { fileName: 'GAP附件', fileUrl: 'https://crm.example.com/gap.pdf' },
    ])
  })

  it('CO-262: projectPlanGapFiles 为空数组时 payload 中也是空数组', () => {
    const form = {
      basic: makeEmptyBasic(),
      customerInfo: [],
      recommendation: { shouldBid: true, reason: '' },
    }

    const payload = buildApiPayload(form)

    expect(payload.evaluationBasic.projectPlanGapFiles).toEqual([])
  })

  it('CO-262: projectPlanGapFiles 缺失时 payload 中补空数组', () => {
    const form = {
      basic: {
        plannedShortlistedCount: null,
        mroOfficeFlowAmount: null,
        customerRevenue: null,
        unfavorableItems: '',
        riskAssessment: '',
        contingencyPlan: '',
        processKnowledge: '',
        supportNotes: '',
        projectPlanGap: '',
        // projectPlanGapFiles 故意缺失
      },
      customerInfo: [],
      recommendation: { shouldBid: true, reason: '' },
    }

    const payload = buildApiPayload(form)

    expect(payload.evaluationBasic.projectPlanGapFiles).toEqual([])
  })
})

describe('evaluationToForm - CO-262 projectPlanGapFiles', () => {
  it('从后端 evaluationBasic.projectPlanGapFiles 回填到 form.basic', () => {
    const evaluation = {
      evaluationBasic: {
        plannedShortlistedCount: 1,
        projectPlanGapFiles: [
          { fileName: 'GAP附件', fileUrl: 'https://crm.example.com/gap.pdf' },
        ],
      },
      evaluationCustomerInfos: [],
      evaluationRecommendation: {},
    }

    const result = evaluationToForm(evaluation)

    expect(result.basic.projectPlanGapFiles).toEqual([
      { fileName: 'GAP附件', fileUrl: 'https://crm.example.com/gap.pdf' },
    ])
  })

  it('后端未返回 projectPlanGapFiles 时回填空数组', () => {
    const evaluation = {
      evaluationBasic: { plannedShortlistedCount: 1 },
      evaluationCustomerInfos: [],
      evaluationRecommendation: {},
    }

    const result = evaluationToForm(evaluation)

    expect(result.basic.projectPlanGapFiles).toEqual([])
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
