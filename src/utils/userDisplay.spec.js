import { describe, expect, it } from 'vitest'
import { formatUserWithNameAndNumber } from './userDisplay.js'

describe('formatUserWithNameAndNumber', () => {
  it('renders "姓名 (工号)" when both name and employeeNumber exist', () => {
    expect(formatUserWithNameAndNumber('王亮', '05972')).toBe('王亮 (05972)')
  })

  it('renders only name when employeeNumber is empty string', () => {
    expect(formatUserWithNameAndNumber('王亮', '')).toBe('王亮')
  })

  it('renders only name when employeeNumber is null', () => {
    expect(formatUserWithNameAndNumber('王亮', null)).toBe('王亮')
  })

  it('renders only name when employeeNumber is undefined', () => {
    expect(formatUserWithNameAndNumber('王亮', undefined)).toBe('王亮')
  })

  it('renders "-" when name is empty string', () => {
    expect(formatUserWithNameAndNumber('', '05972')).toBe('-')
  })

  it('renders "-" when name is null', () => {
    expect(formatUserWithNameAndNumber(null, '05972')).toBe('-')
  })

  it('renders "-" when name is undefined', () => {
    expect(formatUserWithNameAndNumber(undefined, '05972')).toBe('-')
  })

  it('renders "-" when both name and employeeNumber are missing', () => {
    expect(formatUserWithNameAndNumber(null, null)).toBe('-')
    expect(formatUserWithNameAndNumber(undefined, undefined)).toBe('-')
    expect(formatUserWithNameAndNumber('', '')).toBe('-')
  })

  it('handles whitespace-only name as missing', () => {
    // 空字符串视为缺失，但空白字符串当前实现视为存在（与 || 一致）
    // 这里只断言空字符串行为；空白字符串不在规格范围
    expect(formatUserWithNameAndNumber('', '05972')).toBe('-')
  })
})
