import { describe, expect, it } from 'vitest'
import {
  getProjectTypeLabel,
  getCustomerTypeLabel,
  getMatchLabel,
  getMatchTagType,
  parseStringOrArray,
} from '../caseLabels.js'

describe('caseLabels.js', () => {
  it('getProjectTypeLabel and getCustomerTypeLabel provide valid fallbacks', () => {
    expect(getProjectTypeLabel(null)).toBe('常规项目')
    expect(getCustomerTypeLabel(null)).toBe('通用客户')
    expect(getProjectTypeLabel('IT')).toBe('IT')
    expect(getCustomerTypeLabel('GOVERNMENT')).toBe('政府机关/事业单位/高校')
  })

  it('getMatchLabel handles explicit and computed thresholds', () => {
    expect(getMatchLabel(90, null)).toBe('优质')
    expect(getMatchLabel(80, null)).toBe('优质')
    expect(getMatchLabel(79, null)).toBe('良好')
    expect(getMatchLabel(60, null)).toBe('良好')
    expect(getMatchLabel(59, null)).toBe('一般')
    expect(getMatchLabel(0, null)).toBe('一般')
    expect(getMatchLabel(null, '自定义优质')).toBe('自定义优质')
    expect(getMatchLabel(null, null)).toBe('')
  })

  it('getMatchTagType returns proper element tag types', () => {
    expect(getMatchTagType(90, null)).toBe('danger')
    expect(getMatchTagType(65, null)).toBe('warning')
    expect(getMatchTagType(40, null)).toBe('info')
    expect(getMatchTagType(null, '优质')).toBe('danger')
  })

  it('parseStringOrArray splits strings and trims arrays gracefully', () => {
    expect(parseStringOrArray([' a ', 'b', ''])).toEqual(['a', 'b'])
    expect(parseStringOrArray('评分项匹配、类别一致, 客户类型一致')).toEqual([
      '评分项匹配',
      '类别一致',
      '客户类型一致',
    ])
    expect(parseStringOrArray(null)).toEqual([])
    expect(parseStringOrArray('')).toEqual([])
  })
})
