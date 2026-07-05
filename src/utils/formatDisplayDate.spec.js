// Input: formatDisplayDate.js display helpers for CO-472 (T separator fix)
// Output: unit tests covering ISO 8601 / date-only / empty / invalid inputs
// Pos: src/utils/ - Helper test
import { describe, it, expect } from 'vitest'
import { formatDisplayDateTime, formatDisplayDate } from './formatDisplayDate.js'

describe('formatDisplayDateTime', () => {
  it('把 ISO 8601 带 T 分隔符转为空格分隔', () => {
    expect(formatDisplayDateTime('2026-07-05T11:33:30')).toBe('2026-07-05 11:33:30')
  })

  it('截断微秒部分', () => {
    expect(formatDisplayDateTime('2026-07-05T11:33:30.123456')).toBe('2026-07-05 11:33:30')
  })

  it('截断时区偏移部分（保留字符串前 19 位，不转时区）', () => {
    expect(formatDisplayDateTime('2026-07-05T11:33:30+00:00')).toBe('2026-07-05 11:33:30')
  })

  it('已是空格分隔的字符串保持不变', () => {
    expect(formatDisplayDateTime('2026-07-05 11:33:30')).toBe('2026-07-05 11:33:30')
  })

  it('仅日期字符串补成 00:00:00 不合适，保留为日期部分（按实现：仅日期不匹配 datetime 正则走 fallback 路径）', () => {
    // 仅日期格式应使用 formatDisplayDate；这里确保 datetime 函数对仅日期也能给出合理输出
    expect(formatDisplayDateTime('2026-07-05')).toBe('2026-07-05')
  })

  it('空值返回默认 fallback "-"', () => {
    expect(formatDisplayDateTime(null)).toBe('-')
    expect(formatDisplayDateTime(undefined)).toBe('-')
    expect(formatDisplayDateTime('')).toBe('-')
  })

  it('支持自定义 fallback', () => {
    expect(formatDisplayDateTime(null, '—')).toBe('—')
    expect(formatDisplayDateTime('', '暂无')).toBe('暂无')
  })

  it('非日期字符串返回 fallback', () => {
    expect(formatDisplayDateTime('hello')).toBe('-')
    expect(formatDisplayDateTime('not-a-date')).toBe('-')
  })

  it('Date 对象按本地时区格式化', () => {
    const d = new Date('2026-07-05T11:33:30')
    const pad = (n) => String(n).padStart(2, '0')
    const expected = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
    expect(formatDisplayDateTime(d)).toBe(expected)
  })
})

describe('formatDisplayDate', () => {
  it('从 ISO 8601 提取日期部分', () => {
    expect(formatDisplayDate('2026-07-05T11:33:30')).toBe('2026-07-05')
  })

  it('已是日期格式保持不变', () => {
    expect(formatDisplayDate('2026-07-05')).toBe('2026-07-05')
  })

  it('空值返回默认 fallback', () => {
    expect(formatDisplayDate(null)).toBe('-')
    expect(formatDisplayDate(undefined)).toBe('-')
    expect(formatDisplayDate('')).toBe('-')
  })

  it('支持自定义 fallback', () => {
    expect(formatDisplayDate(null, '—')).toBe('—')
  })

  it('非日期字符串返回 fallback', () => {
    expect(formatDisplayDate('hello')).toBe('-')
  })
})
