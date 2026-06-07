import { describe, expect, it } from 'vitest'
import {
  CUSTOMER_TYPE_OPTIONS,
  ASSIGN_RULES,
  DEFAULT_SOURCE_CONFIG,
  MANUAL_FORM_RULES,
  PRIORITY_OPTIONS,
  REGION_OPTIONS,
  createManualTenderForm,
} from './constants.js'

describe('bidding list constants', () => {
  it('uses province-level nationwide region options', () => {
    expect(REGION_OPTIONS).toHaveLength(34)
    expect(REGION_OPTIONS).toEqual(expect.arrayContaining([
      '北京',
      '上海',
      '广东',
      '四川',
      '新疆',
      '台湾',
      '香港',
      '澳门',
    ]))
    for (const legacyOption of ['广州', '深圳', '成都', '其他']) {
      expect(REGION_OPTIONS).not.toContain(legacyOption)
    }
  })

  it('does not limit external source sync to sample cities by default', () => {
    expect(DEFAULT_SOURCE_CONFIG.regions).toEqual([])
  })

  it('defines governed manual tender customer dictionaries', () => {
    expect(CUSTOMER_TYPE_OPTIONS).toEqual(['政府机关/事业单位/高校', '央企', '地方国企', '民企', '港澳台及外企'])
      '政府机关',
      '事业单位',
      '高校',
      '央企',
      '地方国企',
      '民企',
      '港澳台及外企',
    expect(PRIORITY_OPTIONS.map(option => option.value)).toEqual(['S', 'A', 'B', 'C'])
  })

  it('requires governed manual tender fields and removes manual industry input', () => {
    const form = createManualTenderForm()

    expect(form).toMatchObject({
      purchaser: '',
      customerType: '',
      priority: '',
      bidOpeningTime: null,
      pastedText: '',
      projectType: '',
      sourcePlatform: '人工录入',
      // 新增联系人拆分字段
      landline: '',
      mail: '',
      landline2: '',
      mail2: '',
      tenderInfo: '',
      crmOpportunityId: null,
    })
    expect(form).not.toHaveProperty('budget')
    expect(form).not.toHaveProperty('industry')
    expect(form).toHaveProperty('purchaser')
    expect(MANUAL_FORM_RULES).toHaveProperty('purchaser')
    expect(MANUAL_FORM_RULES).toHaveProperty('bidOpeningTime')
    expect(MANUAL_FORM_RULES).not.toHaveProperty('industry')
    expect(MANUAL_FORM_RULES).not.toHaveProperty('tenderAgency')
    // 新增格式校验规则
    expect(MANUAL_FORM_RULES).toHaveProperty('phone')
    expect(MANUAL_FORM_RULES).toHaveProperty('landline')
    expect(MANUAL_FORM_RULES).toHaveProperty('mail')
    expect(MANUAL_FORM_RULES).toHaveProperty('description')
    expect(MANUAL_FORM_RULES.description[0].max).toBe(5000)
    expect(ASSIGN_RULES.map(rule => rule.value)).not.toContain('industry')
  })
})
