import { describe, it, expect } from 'vitest'
import {
  normalizeTenderStatus,
  normalizeTenderForCreate,
  buildTenderUpdatePayload,
  normalizeBatchResult,
  normalizeAiDimensions,
  normalizeAiRisks,
  formatBudgetWan,
  formatTenderDate,
  formatTenderDateTime,
  formatTenderDisplayField,
  getTenderDateTimeParts,
  buildWinProbabilityView,
  safeTenderUrl,
  toBackendStatus
} from './bidding-utils.js'

describe('normalizeTenderStatus', () => {
  it.each([
    ['PENDING_ASSIGNMENT', '待分配'],
    ['TRACKING', '跟踪中'],
    ['BIDDING', '投标中'],
    ['ABANDONED', '已放弃']
  ])('maps backend status "%s" to "%s"', (input, expected) => {
    expect(normalizeTenderStatus(input)).toBe(expected)
  })

  it('returns the original value for unknown status', () => {
    expect(normalizeTenderStatus('UNKNOWN_STATUS')).toBe('UNKNOWN_STATUS')
  })

  it('returns "未知" for null', () => {
    expect(normalizeTenderStatus(null)).toBe('未知')
  })

  it('returns "未知" for undefined', () => {
    expect(normalizeTenderStatus(undefined)).toBe('未知')
  })

  it('returns "未知" for empty string', () => {
    expect(normalizeTenderStatus('')).toBe('未知')
  })
})

describe('normalizeTenderForCreate', () => {
  it('normalizes a full form data object', () => {
    const formData = {
      title: '测试标讯',
      source: '中国招标网',
      budget: 500000,
      deadline: '2026-06-01',
      aiScore: 85,
      riskLevel: 'HIGH',
      originalUrl: 'https://example.com/tender/1',
      externalId: 'EXT-001'
    }
    const result = normalizeTenderForCreate(formData)
    expect(result).toEqual({
      title: '测试标讯',
      source: '中国招标网',
      budget: 500000,
      deadline: '2026-06-01',
      status: 'PENDING_ASSIGNMENT',
      aiScore: 85,
      riskLevel: 'HIGH',
      originalUrl: 'https://example.com/tender/1',
      externalId: 'EXT-001'
    })
  })

  it('applies defaults for partial form data', () => {
    const result = normalizeTenderForCreate({ title: '部分数据' })
    expect(result).toEqual({
      title: '部分数据',
      source: '人工录入',
      budget: 0,
      deadline: null,
      status: 'PENDING_ASSIGNMENT',
      aiScore: 0,
      riskLevel: null,
      originalUrl: '',
      externalId: ''
    })
  })

  it('applies defaults for empty form data', () => {
    const result = normalizeTenderForCreate({})
    expect(result.title).toBe('')
    expect(result.source).toBe('人工录入')
    expect(result.budget).toBe(0)
    expect(result.deadline).toBeNull()
    expect(result.status).toBe('PENDING_ASSIGNMENT')
    expect(result.aiScore).toBe(0)
    expect(result.riskLevel).toBeNull()
    expect(result.originalUrl).toBe('')
    expect(result.externalId).toBe('')
  })

  it('converts string budget to number', () => {
    const result = normalizeTenderForCreate({ budget: '123456' })
    expect(result.budget).toBe(123456)
  })

  it('handles null budget as 0', () => {
    const result = normalizeTenderForCreate({ budget: null })
    expect(result.budget).toBe(0)
  })

  it('converts string aiScore to number', () => {
    const result = normalizeTenderForCreate({ aiScore: '75' })
    expect(result.aiScore).toBe(75)
  })
})

describe('buildTenderUpdatePayload', () => {
  it('includes only a single changed field', () => {
    const result = buildTenderUpdatePayload({ status: 'TRACKING' })
    expect(result).toEqual({ status: 'TRACKING' })
  })

  it('includes multiple changed fields', () => {
    const result = buildTenderUpdatePayload({
      title: '新标题',
      budget: 200000,
      riskLevel: 'MEDIUM'
    })
    expect(result).toEqual({
      title: '新标题',
      budget: 200000,
      riskLevel: 'MEDIUM'
    })
  })

  it('returns empty object for empty changes', () => {
    const result = buildTenderUpdatePayload({})
    expect(result).toEqual({})
  })

  it('converts string budget to number', () => {
    const result = buildTenderUpdatePayload({ budget: '99999' })
    expect(result.budget).toBe(99999)
  })

  it('converts string aiScore to number', () => {
    const result = buildTenderUpdatePayload({ aiScore: '60' })
    expect(result.aiScore).toBe(60)
  })

  it('includes all supported fields when all are present', () => {
    const changes = {
      status: 'BIDDING',
      title: '完整更新',
      budget: 300000,
      deadline: '2026-07-01',
      aiScore: 90,
      riskLevel: 'LOW',
      originalUrl: 'https://example.com/updated'
    }
    const result = buildTenderUpdatePayload(changes)
    expect(result).toEqual(changes)
  })

  it('ignores fields not in the allowed set', () => {
    const result = buildTenderUpdatePayload({ id: 999, createdAt: '2026-01-01' })
    expect(result).toEqual({})
  })
})

describe('display and URL helpers', () => {
  it('formats backend yuan budgets as ten-thousand-yuan display values', () => {
    expect(formatBudgetWan(15800000)).toBe('1,580')
    expect(formatBudgetWan(4500000)).toBe('450')
    expect(formatBudgetWan(12500)).toBe('1.25')
  })

  it('formats tender dates from LocalDate and ISO datetime values', () => {
    expect(formatTenderDate('2026-05-31')).toBe('2026-05-31')
    expect(formatTenderDate('2026-05-31T18:00:00')).toBe('2026-05-31')
    expect(formatTenderDate('2026-05-31T18:00:00Z')).toBe('2026-05-31')
  })

  it('formats tender date times from ISO datetime values', () => {
    expect(formatTenderDateTime('2026-05-31T18:00:00')).toBe('2026-05-31 18:00')
    expect(formatTenderDateTime('2026-05-31T18:00:00Z')).toBe('2026-05-31 18:00')
    expect(formatTenderDateTime('2026-05-31')).toBe('2026-05-31')
  })

  it('splits tender date time into separate date and time display parts', () => {
    expect(getTenderDateTimeParts('2026-05-31T18:00:00')).toEqual({
      date: '2026-05-31',
      time: '18:00',
      text: '2026-05-31 18:00',
      hasTime: true
    })
    expect(getTenderDateTimeParts('2026-05-31')).toEqual({
      date: '2026-05-31',
      time: '',
      text: '2026-05-31',
      hasTime: false
    })
  })

  it('uses placeholder for empty or invalid tender date values', () => {
    expect(formatTenderDate(null)).toBe('--')
    expect(formatTenderDate('')).toBe('--')
    expect(formatTenderDate('2026-02-30')).toBe('--')
    expect(formatTenderDateTime(undefined)).toBe('--')
    expect(formatTenderDateTime('not-a-date')).toBe('--')
    expect(formatTenderDateTime('2026-05-31T25:00:00')).toBe('--')
    expect(getTenderDateTimeParts('not-a-date')).toEqual({
      date: '--',
      time: '',
      text: '--',
      hasTime: false
    })
  })

  it('allows only http and https tender URLs', () => {
    expect(safeTenderUrl('https://example.com/tender')).toBe('https://example.com/tender')
    expect(safeTenderUrl('http://example.com/tender')).toBe('http://example.com/tender')
    expect(safeTenderUrl('javascript:alert(1)')).toBe('')
    expect(safeTenderUrl('/relative/path')).toBe('')
  })

  it('marks empty tender detail fields as not extracted without guessing values', () => {
    expect(formatTenderDisplayField('上海')).toEqual({
      text: '上海',
      isMissing: false,
      tooltip: ''
    })
    expect(formatTenderDisplayField('')).toEqual({
      text: '未提取',
      isMissing: true,
      tooltip: '真实 API 暂无该字段，未做推断填充'
    })
    expect(formatTenderDisplayField(null, '暂无行业')).toMatchObject({
      text: '暂无行业',
      isMissing: true
    })
  })

  it('maps match score to stars and percent probability display', () => {
    expect(buildWinProbabilityView(100)).toMatchObject({
      rate: 5,
      percent: 100,
      label: '100%',
      sourceScore: 100
    })
    expect(buildWinProbabilityView(86)).toMatchObject({
      rate: 4,
      percent: 80,
      label: '80%',
      sourceScore: 86
    })
    expect(buildWinProbabilityView(null)).toMatchObject({
      rate: 0,
      percent: 0,
      label: '暂无',
      sourceScore: 0,
      hasScore: false
    })
    expect(buildWinProbabilityView(0)).toMatchObject({
      rate: 0,
      percent: 0,
      label: '暂无',
      sourceScore: 0,
      hasScore: false
    })
  })
})

describe('normalizeBatchResult', () => {
  it('returns success message when all items succeed', () => {
    const response = { data: { success: true, totalCount: 3, successCount: 3, failureCount: 0 } }
    const result = normalizeBatchResult(response)
    expect(result.ok).toBe(true)
    expect(result.message).toBe('操作成功，共处理 3 条')
  })

  it('returns partial success message with error details', () => {
    const response = {
      data: {
        success: false,
        totalCount: 3,
        successCount: 2,
        failureCount: 1,
        errors: [{ itemId: 3, errorMessage: '权限不足' }]
      }
    }
    const result = normalizeBatchResult(response)
    expect(result.ok).toBe(false)
    expect(result.message).toContain('2 条成功')
    expect(result.message).toContain('1 条失败')
    expect(result.message).toContain('权限不足')
  })

  it('returns failure message when all items fail', () => {
    const response = {
      data: {
        success: false,
        totalCount: 2,
        successCount: 0,
        failureCount: 2,
        errors: [
          { itemId: 1, errorMessage: '已被认领' },
          { itemId: 2, errorMessage: '不存在' }
        ]
      }
    }
    const result = normalizeBatchResult(response)
    expect(result.ok).toBe(false)
    expect(result.message).toContain('已被认领')
    expect(result.message).toContain('不存在')
  })

  it('returns failure message for null response', () => {
    const result = normalizeBatchResult(null)
    expect(result.ok).toBe(false)
    expect(result.message).toBe('操作失败：无响应')
  })

  it('returns failure message for undefined response', () => {
    const result = normalizeBatchResult(undefined)
    expect(result.ok).toBe(false)
    expect(result.message).toBe('操作失败：无响应')
  })

  it('handles response without nested data property', () => {
    const response = { success: true, totalCount: 1, successCount: 1, failureCount: 0 }
    const result = normalizeBatchResult(response)
    expect(result.ok).toBe(true)
    expect(result.message).toBe('操作成功，共处理 1 条')
  })

  it('returns generic failure for all-failure with no error details', () => {
    const response = { data: { success: false, successCount: 0, failureCount: 1, errors: [] } }
    const result = normalizeBatchResult(response)
    expect(result.ok).toBe(false)
    expect(result.message).toContain('未知错误')
  })
})

describe('normalizeAiDimensions', () => {
  it('normalizes a typical dimension array', () => {
    const input = [
      { name: '技术匹配', score: 85 },
      { name: '价格竞争力', score: 60 },
      { name: '资质符合度', score: 45 }
    ]
    const result = normalizeAiDimensions(input)
    expect(result).toEqual([
      { name: '技术匹配', score: 85, percentage: '85%', level: 'high', description: '', suggestion: '' },
      { name: '价格竞争力', score: 60, percentage: '60%', level: 'medium', description: '', suggestion: '' },
      { name: '资质符合度', score: 45, percentage: '45%', level: 'low', description: '', suggestion: '' }
    ])
  })

  it('returns empty array for empty input', () => {
    expect(normalizeAiDimensions([])).toEqual([])
  })

  it('returns empty array for null input', () => {
    expect(normalizeAiDimensions(null)).toEqual([])
  })

  it('returns empty array for undefined input', () => {
    expect(normalizeAiDimensions(undefined)).toEqual([])
  })

  it('handles missing name with default', () => {
    const result = normalizeAiDimensions([{ score: 70 }])
    expect(result[0].name).toBe('未知维度')
  })

  it('handles missing score with default 0', () => {
    const result = normalizeAiDimensions([{ name: '测试维度' }])
    expect(result[0].score).toBe(0)
    expect(result[0].percentage).toBe('0%')
    expect(result[0].level).toBe('low')
  })

  it('classifies boundary scores correctly', () => {
    const input = [
      { name: 'A', score: 80 },
      { name: 'B', score: 79 },
      { name: 'C', score: 60 },
      { name: 'D', score: 59 }
    ]
    const result = normalizeAiDimensions(input)
    expect(result[0].level).toBe('high')
    expect(result[1].level).toBe('medium')
    expect(result[2].level).toBe('medium')
    expect(result[3].level).toBe('low')
  })
})

describe('normalizeAiRisks', () => {
  it('normalizes risks with all three levels', () => {
    const input = [
      { level: 'HIGH', desc: '高风险项', action: '立即处理' },
      { level: 'MEDIUM', desc: '中等风险', action: '关注' },
      { level: 'LOW', desc: '低风险', action: '备注' }
    ]
    const result = normalizeAiRisks(input)
    expect(result).toEqual([
      { level: 'HIGH', type: 'danger', description: '高风险项', action: '立即处理' },
      { level: 'MEDIUM', type: 'warning', description: '中等风险', action: '关注' },
      { level: 'LOW', type: 'info', description: '低风险', action: '备注' }
    ])
  })

  it('returns empty array for empty input', () => {
    expect(normalizeAiRisks([])).toEqual([])
  })

  it('returns empty array for null input', () => {
    expect(normalizeAiRisks(null)).toEqual([])
  })

  it('returns empty array for undefined input', () => {
    expect(normalizeAiRisks(undefined)).toEqual([])
  })

  it('defaults missing level to LOW with info type', () => {
    const result = normalizeAiRisks([{ desc: '未分级风险', action: '待定' }])
    expect(result[0].level).toBe('LOW')
    expect(result[0].type).toBe('info')
  })

  it('handles unknown level gracefully', () => {
    const result = normalizeAiRisks([{ level: 'CRITICAL', desc: '未知级别' }])
    expect(result[0].type).toBe('info')
  })

  it('defaults missing desc and action to empty strings', () => {
    const result = normalizeAiRisks([{ level: 'HIGH' }])
    expect(result[0].description).toBe('')
    expect(result[0].action).toBe('')
  })
})

describe('toBackendStatus', () => {
  it.each([
    ['new', 'PENDING_ASSIGNMENT'],
    ['pending', 'PENDING_ASSIGNMENT'],
    ['following', 'TRACKING'],
    ['tracking', 'TRACKING'],
    ['bidding', 'BIDDING'],
    ['bidded', 'BIDDING'],
    ['abandoned', 'ABANDONED']
  ])('maps English frontend status "%s" to backend "%s"', (input, expected) => {
    expect(toBackendStatus(input)).toBe(expected)
  })

  it.each([
    ['待处理', 'PENDING_ASSIGNMENT'],
    ['跟踪中', 'TRACKING'],
    ['已投标', 'BIDDING'],
    ['已放弃', 'ABANDONED']
  ])('maps Chinese text "%s" to backend "%s"', (input, expected) => {
    expect(toBackendStatus(input)).toBe(expected)
  })

  it('passes through unknown values unchanged', () => {
    expect(toBackendStatus('CUSTOM_STATUS')).toBe('CUSTOM_STATUS')
  })

  it('passes through undefined unchanged', () => {
    expect(toBackendStatus(undefined)).toBeUndefined()
  })

  it('passes through null unchanged', () => {
    expect(toBackendStatus(null)).toBeNull()
  })
})
