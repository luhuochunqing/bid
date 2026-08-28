import { describe, it, expect } from 'vitest'
import { customerTypeOptions, projectTypeOptions } from './useProjectSearch.js'

describe('useProjectSearch filter options', () => {
  it('customerTypeOptions contains exactly 5 standard customer types without "其他" or "OTHER"', () => {
    expect(customerTypeOptions).toEqual([
      { value: 'GOVERNMENT', label: '政府机关/事业单位/高校' },
      { value: 'CENTRAL_SOE', label: '央企' },
      { value: 'LOCAL_SOE', label: '地方国企' },
      { value: 'PRIVATE', label: '民企' },
      { value: 'FOREIGN', label: '港澳台及外企' },
    ])
    expect(customerTypeOptions.some((opt) => opt.value === 'OTHER' || opt.label === '其他')).toBe(false)
  })

  it('projectTypeOptions contains standard project types including "其他"', () => {
    expect(projectTypeOptions).toEqual([
      { value: 'OFFICE', label: '办公' },
      { value: 'COMPREHENSIVE', label: '综合' },
      { value: 'COLLECTIVE', label: '集采' },
      { value: 'INDUSTRIAL', label: '工业品' },
      { value: 'OTHER', label: '其他' },
    ])
  })
})
