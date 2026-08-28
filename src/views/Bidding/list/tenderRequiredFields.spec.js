import { describe, expect, it } from 'vitest'
import {
  TENDER_REQUIRED_FIELDS,
  getMissingTenderRequiredFields,
  buildSaveDisabledReason,
} from './tenderRequiredFields.js'

const FULL_FORM = {
  title: '国家电网采购', purchaser: '国网', region: '北京市',
  deadline: '2026-09-01T10:00:00', bidOpeningTime: '2026-09-03T10:00:00',
  customerType: '央企集团', priority: 'A', projectType: '物资采购',
}

describe('tenderRequiredFields', () => {
  it('returns all labels for an empty form', () => {
    expect(getMissingTenderRequiredFields({})).toEqual([
      '项目名称', '招标主体', '总部所在地', '报名截止时间', '开标时间', '客户类型', '优先级', '项目类型',
    ])
  })

  it('treats whitespace-only strings as missing', () => {
    const missing = getMissingTenderRequiredFields({
      title: '   ', purchaser: '', region: '  ', deadline: null,
      bidOpeningTime: undefined, customerType: ' ', priority: '', projectType: '\t',
    })
    expect(missing).toHaveLength(8)
  })

  it('returns empty array when all required fields are filled', () => {
    expect(getMissingTenderRequiredFields(FULL_FORM)).toEqual([])
  })

  // 口径契约：必须与 constants.js MANUAL_FORM_RULES 的 required 字段集一致（含 projectType）
  it('keeps the 8-field contract aligned with MANUAL_FORM_RULES', () => {
    expect(TENDER_REQUIRED_FIELDS.map(f => f.key)).toEqual([
      'title', 'purchaser', 'region', 'deadline', 'bidOpeningTime', 'customerType', 'priority', 'projectType',
    ])
  })
})

describe('buildSaveDisabledReason', () => {
  it('prioritizes parsing state over missing fields', () => {
    expect(buildSaveDisabledReason({ parsingDocument: true, form: {} }))
      .toBe('标讯附件正在上传或识别中，请稍候')
  })

  it('lists missing required fields joined with 、', () => {
    expect(buildSaveDisabledReason({ form: { title: '标题', projectType: '物资采购' } }))
      .toBe('还差必填项：招标主体、总部所在地、报名截止时间、开标时间、客户类型、优先级')
  })

  it('returns empty string (tooltip hidden) when nothing is missing', () => {
    expect(buildSaveDisabledReason({ form: FULL_FORM })).toBe('')
  })
})
