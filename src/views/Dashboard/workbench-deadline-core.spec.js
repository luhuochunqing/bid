import { describe, it, expect } from 'vitest'
import {
  normalizeDeadlineStats,
  selectDeadlineMetrics,
} from './workbench-deadline-core.js'

describe('normalizeDeadlineStats', () => {
  it('正常输入返回正确结构', () => {
    const raw = {
      registrationDeadline: { todayCount: '2', weekCount: 5, monthCount: 12 },
      bidOpening: { todayCount: 1, weekCount: '3', monthCount: 8 },
      depositDeadline: { todayCount: 0, weekCount: 2, monthCount: '6' },
    }

    const result = normalizeDeadlineStats(raw)

    expect(result.registrationDeadline).toEqual({
      todayCount: 2, weekCount: 5, monthCount: 12,
    })
    expect(result.bidOpening).toEqual({
      todayCount: 1, weekCount: 3, monthCount: 8,
    })
    expect(result.depositDeadline).toEqual({
      todayCount: 0, weekCount: 2, monthCount: 6,
    })
  })

  it('空输入返回全零', () => {
    const result = normalizeDeadlineStats()

    expect(result.registrationDeadline).toEqual({
      todayCount: 0, weekCount: 0, monthCount: 0,
    })
    expect(result.bidOpening).toEqual({
      todayCount: 0, weekCount: 0, monthCount: 0,
    })
    expect(result.depositDeadline).toEqual({
      todayCount: 0, weekCount: 0, monthCount: 0,
    })
  })

  it('缺失字段返回默认 0', () => {
    const raw = {
      registrationDeadline: { todayCount: 3 },
      bidOpening: null,
      depositDeadline: {},
    }

    const result = normalizeDeadlineStats(raw)

    expect(result.registrationDeadline).toEqual({
      todayCount: 3, weekCount: 0, monthCount: 0,
    })
    expect(result.bidOpening).toEqual({
      todayCount: 0, weekCount: 0, monthCount: 0,
    })
    expect(result.depositDeadline).toEqual({
      todayCount: 0, weekCount: 0, monthCount: 0,
    })
  })
})

describe('selectDeadlineMetrics', () => {
  const deadlineStats = {
    registrationDeadline: { todayCount: 2, weekCount: 5, monthCount: 12 },
    bidOpening: { todayCount: 1, weekCount: 3, monthCount: 8 },
    depositDeadline: { todayCount: 0, weekCount: 2, monthCount: 6 },
  }

  it('analytics 权限返回 4 张卡片', () => {
    const result = selectDeadlineMetrics(['analytics'], deadlineStats)

    expect(result).toHaveLength(4)
    expect(result[0]).toMatchObject({
      key: 'reg_today', deadlineType: 'registrationDeadline', period: 'todayCount', value: '2',
    })
    expect(result[3]).toMatchObject({
      key: 'reg_month', deadlineType: 'registrationDeadline', period: 'monthCount', value: '12',
    })
  })

  it('project 权限返回 3 张卡片', () => {
    const result = selectDeadlineMetrics(['project'], deadlineStats)

    expect(result).toHaveLength(3)
    expect(result[0]).toMatchObject({
      key: 'reg_week', deadlineType: 'registrationDeadline', period: 'weekCount', value: '5',
    })
    expect(result[1]).toMatchObject({
      key: 'opening_today', deadlineType: 'bidOpening', period: 'todayCount', value: '1',
    })
  })

  it('默认权限（无 analytics/project）返回 3 张卡片', () => {
    const result = selectDeadlineMetrics(['bidding'], deadlineStats)

    expect(result).toHaveLength(3)
    expect(result[0]).toMatchObject({ key: 'reg_today' })
    expect(result[1]).toMatchObject({ key: 'opening_week' })
    expect(result[2]).toMatchObject({ key: 'deposit_month' })
  })

  it('空 deadlineStats 返回卡片但值为 "0"', () => {
    const result = selectDeadlineMetrics(['analytics'], {})

    expect(result).toHaveLength(4)
    expect(result[0].value).toBe('0')
    expect(result[1].value).toBe('0')
    expect(result[2].value).toBe('0')
    expect(result[3].value).toBe('0')
  })

  it('null deadlineStats 不抛错并返回 "0" 值卡片', () => {
    expect(() => selectDeadlineMetrics(['analytics'], null)).not.toThrow()
    expect(() => selectDeadlineMetrics(['analytics'], undefined)).not.toThrow()

    const result = selectDeadlineMetrics(['analytics'], null)
    expect(result).toHaveLength(4)
    result.forEach((card) => expect(card.value).toBe('0'))
  })
})
