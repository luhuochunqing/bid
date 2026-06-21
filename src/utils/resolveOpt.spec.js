import { describe, it, expect } from 'vitest'
import { ref, computed } from 'vue'
import { resolveOpt } from './resolveOpt.js'

describe('resolveOpt', () => {
  it('returns raw primitive values as-is', () => {
    expect(resolveOpt(0)).toBe(0)
    expect(resolveOpt('')).toBe('')
    expect(resolveOpt(false)).toBe(false)
    expect(resolveOpt(42)).toBe(42)
  })

  it('returns nullish values as-is', () => {
    expect(resolveOpt(null)).toBeNull()
    expect(resolveOpt(undefined)).toBeUndefined()
  })

  it('unwraps ref values', () => {
    expect(resolveOpt(ref(42))).toBe(42)
    expect(resolveOpt(ref(false))).toBe(false)
  })

  it('unwraps computed values', () => {
    expect(resolveOpt(computed(() => 42))).toBe(42)
  })

  it('returns plain objects as-is (not refs)', () => {
    const obj = { value: 'not-a-ref' }
    expect(resolveOpt(obj)).toBe(obj)
  })

  it('unwraps string ids consistently for equality checks', () => {
    const idRef = ref(1001)
    expect(String(resolveOpt(idRef))).toBe('1001')
    expect(String(resolveOpt(1001))).toBe('1001')
  })
})
