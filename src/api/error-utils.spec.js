// Input: error-utils helpers
// Output: unit tests for isRateLimitError and notifyErrorUnlessRateLimit
// Pos: src/api/ - error utility tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ElMessage } from 'element-plus'
import { isRateLimitError, notifyErrorUnlessRateLimit } from './error-utils.js'

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn()
  }
}))

describe('error-utils', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('isRateLimitError', () => {
    it('returns true for HTTP 429 axios error', () => {
      expect(isRateLimitError({ response: { status: 429 } })).toBe(true)
    })

    it('returns false for other status codes', () => {
      expect(isRateLimitError({ response: { status: 500 } })).toBe(false)
      expect(isRateLimitError({ response: { status: 403 } })).toBe(false)
    })

    it('returns false for null/undefined/missing response', () => {
      expect(isRateLimitError(null)).toBe(false)
      expect(isRateLimitError(undefined)).toBe(false)
      expect(isRateLimitError(new Error('network'))).toBe(false)
    })
  })

  describe('notifyErrorUnlessRateLimit', () => {
    it('does not show toast for 429 errors', () => {
      notifyErrorUnlessRateLimit({ response: { status: 429, data: { msg: '限流' } } }, 'fallback')
      expect(ElMessage.error).not.toHaveBeenCalled()
    })

    it('shows server msg for non-429 errors', () => {
      notifyErrorUnlessRateLimit({ response: { status: 500, data: { msg: '服务器错误' } } }, 'fallback')
      expect(ElMessage.error).toHaveBeenCalledWith('服务器错误')
    })

    it('shows server message field when msg is absent', () => {
      notifyErrorUnlessRateLimit({ response: { status: 400, data: { message: '参数错误' } } }, 'fallback')
      expect(ElMessage.error).toHaveBeenCalledWith('参数错误')
    })

    it('falls back to error message when no server message', () => {
      notifyErrorUnlessRateLimit({ message: 'network error' }, 'fallback')
      expect(ElMessage.error).toHaveBeenCalledWith('network error')
    })

    it('falls back to provided message when nothing else is available', () => {
      notifyErrorUnlessRateLimit({}, '加载失败')
      expect(ElMessage.error).toHaveBeenCalledWith('加载失败')
    })
  })
})
