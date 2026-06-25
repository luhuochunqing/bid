import { describe, it, expect } from 'vitest'
import { hexToSoftBackground } from './color'

describe('hexToSoftBackground', () => {
  it('converts a 6-digit hex to rgba with 0.12 opacity', () => {
    expect(hexToSoftBackground('#409eff')).toBe('rgba(64, 158, 255, 0.12)')
  })

  it('converts a 3-digit hex by doubling each digit', () => {
    expect(hexToSoftBackground('#f56')).toBe('rgba(255, 85, 102, 0.12)')
  })

  it('handles lowercase hex', () => {
    expect(hexToSoftBackground('#409eff')).toBe('rgba(64, 158, 255, 0.12)')
  })

  it('handles uppercase hex', () => {
    expect(hexToSoftBackground('#409EFF')).toBe('rgba(64, 158, 255, 0.12)')
  })

  it('handles hex without # prefix', () => {
    expect(hexToSoftBackground('409eff')).toBe('rgba(64, 158, 255, 0.12)')
  })

  it('returns fallback for null/undefined', () => {
    expect(hexToSoftBackground(null)).toBe('var(--bg-subtle)')
    expect(hexToSoftBackground(undefined)).toBe('var(--bg-subtle)')
  })

  it('returns fallback for empty string', () => {
    expect(hexToSoftBackground('')).toBe('var(--bg-subtle)')
  })

  it('returns fallback for invalid hex', () => {
    expect(hexToSoftBackground('not-a-color')).toBe('var(--bg-subtle)')
  })
})