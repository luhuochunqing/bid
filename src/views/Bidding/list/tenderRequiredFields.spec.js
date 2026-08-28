import { describe, expect, it } from 'vitest'
import { TENDER_REQUIRED_FIELDS, getMissingTenderRequiredFields } from './tenderRequiredFields.js'

describe('tenderRequiredFields', () => {
  it('returns all labels for an empty form', () => {
    expect(getMissingTenderRequiredFields({})).toEqual([
      '项目名称', '招标主体', '总部所在地', '报名截止时间', '开标时间', '客户类型', '优先级',
    ])
  })

  it('treats whitespace-only strings as missing', () => {
    const missing = getMissingTenderRequiredFields({
      title: '   ', purchaser: '', region: '  ', deadline: null,
      bidOpeningTime: undefined, customerType: ' ', priority: '',
    })
    expect(missing).toHaveLength(7)
  })

  it('returns empty array when all required fields are filled', () => {
    const missing = getMissingTenderRequiredFields({
      title: '国家电网采购', purchaser: '国网', region: '北京市',
      deadline: '2026-09-01T10:00:00', bidOpeningTime: '2026-09-03T10:00:00',
      customerType: '央企集团', priority: 'A',
    })
    expect(missing).toEqual([])
  })

  it('keeps the 7-field contract aligned with canSave', () => {
    expect(TENDER_REQUIRED_FIELDS.map(f => f.key)).toEqual([
      'title', 'purchaser', 'region', 'deadline', 'bidOpeningTime', 'customerType', 'priority',
    ])
  })
})
