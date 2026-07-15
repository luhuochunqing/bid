// CO-583 业绩列表分组排序测试
// 需求：前端按 groupCompany 拼音 ASC → signingDate ASC → expiryDate ASC 排序，空 groupCompany 排最后
// Pos: src/views/Knowledge/__tests__/ - Performance list sort test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, it, expect } from 'vitest'
import { sortPerformanceByGroupPinyin } from '../performanceSort.js'

describe('CO-583 sortPerformanceByGroupPinyin', () => {
  it('按 groupCompany 拼音升序排列（山东能源 < 中核集团 < 中粮集团）', () => {
    const records = [
      { id: 1, groupCompany: '中粮集团', signingDate: '2024-01-01', expiryDate: '2025-01-01' },
      { id: 2, groupCompany: '山东能源', signingDate: '2024-01-01', expiryDate: '2025-01-01' },
      { id: 3, groupCompany: '中核集团', signingDate: '2024-01-01', expiryDate: '2025-01-01' }
    ]
    const sorted = sortPerformanceByGroupPinyin(records)
    expect(sorted.map(r => r.groupCompany)).toEqual(['山东能源', '中核集团', '中粮集团'])
  })

  it('同组内按 signingDate 升序排列', () => {
    const records = [
      { id: 1, groupCompany: '中核集团', signingDate: '2024-03-01', expiryDate: '2025-01-01' },
      { id: 2, groupCompany: '中核集团', signingDate: '2024-01-01', expiryDate: '2025-01-01' },
      { id: 3, groupCompany: '中核集团', signingDate: '2024-02-01', expiryDate: '2025-01-01' }
    ]
    const sorted = sortPerformanceByGroupPinyin(records)
    expect(sorted.map(r => r.signingDate)).toEqual(['2024-01-01', '2024-02-01', '2024-03-01'])
  })

  it('同组同 signingDate 按 expiryDate 升序排列', () => {
    const records = [
      { id: 1, groupCompany: '中核集团', signingDate: '2024-01-01', expiryDate: '2025-03-01' },
      { id: 2, groupCompany: '中核集团', signingDate: '2024-01-01', expiryDate: '2025-01-01' },
      { id: 3, groupCompany: '中核集团', signingDate: '2024-01-01', expiryDate: '2025-02-01' }
    ]
    const sorted = sortPerformanceByGroupPinyin(records)
    expect(sorted.map(r => r.expiryDate)).toEqual(['2025-01-01', '2025-02-01', '2025-03-01'])
  })

  it('空 groupCompany 排在最后', () => {
    const records = [
      { id: 1, groupCompany: '', signingDate: '2024-01-01', expiryDate: '2025-01-01' },
      { id: 2, groupCompany: '中核集团', signingDate: '2024-01-01', expiryDate: '2025-01-01' },
      { id: 3, groupCompany: null, signingDate: '2024-01-01', expiryDate: '2025-01-01' },
      { id: 4, groupCompany: '山东能源', signingDate: '2024-01-01', expiryDate: '2025-01-01' }
    ]
    const sorted = sortPerformanceByGroupPinyin(records)
    // 前两个应是有值的集团（按拼音：山东能源 < 中核集团），后两个是空值
    expect(sorted[0].groupCompany).toBe('山东能源')
    expect(sorted[1].groupCompany).toBe('中核集团')
    expect(sorted[2].groupCompany).toBe('')
    expect(sorted[3].groupCompany).toBeNull()
  })

  it('signingDate 为 null 时排在该组内最后', () => {
    const records = [
      { id: 1, groupCompany: '中核集团', signingDate: null, expiryDate: '2025-01-01' },
      { id: 2, groupCompany: '中核集团', signingDate: '2024-01-01', expiryDate: '2025-01-01' }
    ]
    const sorted = sortPerformanceByGroupPinyin(records)
    expect(sorted[0].id).toBe(2)
    expect(sorted[1].id).toBe(1)
  })

  it('不修改原数组（pure function）', () => {
    const records = [
      { id: 1, groupCompany: '中粮集团', signingDate: '2024-01-01', expiryDate: '2025-01-01' },
      { id: 2, groupCompany: '中核集团', signingDate: '2024-01-01', expiryDate: '2025-01-01' }
    ]
    const original = [...records]
    sortPerformanceByGroupPinyin(records)
    expect(records).toEqual(original)
  })

  it('空数组返回空数组', () => {
    expect(sortPerformanceByGroupPinyin([])).toEqual([])
  })
})
