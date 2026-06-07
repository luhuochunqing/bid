import { describe, it, expect } from 'vitest'
import { getTimeGreeting } from '@/views/Dashboard/workbench-core.js'

describe('getTimeGreeting', () => {
  it('returns "上午好" for morning hours (8)', () => {
    expect(getTimeGreeting(8)).toBe('上午好')
  })

  it('returns "下午好" for afternoon hours (14)', () => {
    expect(getTimeGreeting(14)).toBe('下午好')
  })

  it('returns "晚上好" for evening hours (20)', () => {
    expect(getTimeGreeting(20)).toBe('晚上好')
  })

  it('returns "晚上好" for late night hours (2)', () => {
    expect(getTimeGreeting(2)).toBe('晚上好')
  })

  describe('edge cases', () => {
    it('returns "上午好" for hour 5 (start of morning)', () => {
      expect(getTimeGreeting(5)).toBe('上午好')
    })

    it('returns "上午好" for hour 11 (end of morning)', () => {
      expect(getTimeGreeting(11)).toBe('上午好')
    })

    it('returns "下午好" for hour 12 (start of afternoon)', () => {
      expect(getTimeGreeting(12)).toBe('下午好')
    })

    it('returns "下午好" for hour 17 (end of afternoon)', () => {
      expect(getTimeGreeting(17)).toBe('下午好')
    })

    it('returns "晚上好" for hour 18 (start of evening)', () => {
      expect(getTimeGreeting(18)).toBe('晚上好')
    })
  })
})
