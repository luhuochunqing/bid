import { describe, it, expect } from 'vitest'
import { formatBytes } from './formatBytes'

describe('formatBytes', () => {
  it('无效值返回 fallback', () => {
    expect(formatBytes(0)).toBe('—')
    expect(formatBytes(null)).toBe('—')
    expect(formatBytes(undefined)).toBe('—')
    expect(formatBytes(-1)).toBe('—')
    expect(formatBytes(NaN)).toBe('—')
  })

  it('支持自定义 fallback', () => {
    expect(formatBytes(0, '-')).toBe('-')
    expect(formatBytes(null, 'N/A')).toBe('N/A')
  })

  it('B 级别显示整数', () => {
    expect(formatBytes(1)).toBe('1 B')
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(1023)).toBe('1023 B')
  })

  it('KB 级别保留 2 位小数', () => {
    expect(formatBytes(1024)).toBe('1.00 KB')
    expect(formatBytes(1536)).toBe('1.50 KB')
    // 1048575 / 1024 = 1023.999...，toFixed(2) 四舍五入为 1024.00
    expect(formatBytes(1024 * 1024 - 1)).toBe('1024.00 KB')
  })

  it('MB 级别保留 2 位小数', () => {
    expect(formatBytes(1024 * 1024)).toBe('1.00 MB')
    expect(formatBytes(1024 * 1024 * 1.5)).toBe('1.50 MB')
    expect(formatBytes(1024 * 1024 * 100)).toBe('100.00 MB')
  })

  it('GB 级别保留 2 位小数', () => {
    expect(formatBytes(1024 * 1024 * 1024)).toBe('1.00 GB')
    expect(formatBytes(1024 * 1024 * 1024 * 2.5)).toBe('2.50 GB')
  })

  it('上限为 GB，不再向上升级', () => {
    // TB 级别仍然显示 GB（避免数组越界）
    const tb = 1024 * 1024 * 1024 * 1024
    expect(formatBytes(tb)).toBe('1024.00 GB')
  })
})
