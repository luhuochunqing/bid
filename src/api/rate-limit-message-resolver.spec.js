import { describe, expect, it } from 'vitest'
import {
  DEFAULT_RATE_LIMIT_MESSAGE,
  resolveRateLimitMessage,
} from './rate-limit-message-resolver.js'

describe('resolveRateLimitMessage', () => {
  it('returns non-rate-limit result for non-429 status', () => {
    expect(resolveRateLimitMessage({ status: 500 })).toEqual({
      isRateLimit: false,
      message: '',
      waitSeconds: null,
    })
  })

  it('returns default friendly message when no Retry-After and no server msg', () => {
    expect(resolveRateLimitMessage({ status: 429 })).toEqual({
      isRateLimit: true,
      message: DEFAULT_RATE_LIMIT_MESSAGE,
      waitSeconds: null,
    })
  })

  it('returns server msg when provided', () => {
    const serverMsg = '系统繁忙，请稍后再试'
    expect(
      resolveRateLimitMessage({ status: 429, data: { msg: serverMsg } })
    ).toEqual({
      isRateLimit: true,
      message: serverMsg,
      waitSeconds: null,
    })
  })

  it('shows wait seconds when Retry-After header is present', () => {
    expect(
      resolveRateLimitMessage({
        status: 429,
        headers: { 'retry-after': '5' },
      })
    ).toEqual({
      isRateLimit: true,
      message: '操作太快了，请等待 5 秒后再试',
      waitSeconds: 5,
    })
  })

  it('preserves server msg over default even with Retry-After', () => {
    const aiMsg = 'AI 服务请求过于频繁，请稍后再试，当前可手动填写'
    expect(
      resolveRateLimitMessage({
        status: 429,
        data: { msg: aiMsg },
        headers: { 'retry-after': '10' },
      })
    ).toEqual({
      isRateLimit: true,
      message: aiMsg,
      waitSeconds: 10,
    })
  })

  it('falls back to default for invalid Retry-After', () => {
    expect(
      resolveRateLimitMessage({
        status: 429,
        headers: { 'retry-after': 'abc' },
      })
    ).toEqual({
      isRateLimit: true,
      message: DEFAULT_RATE_LIMIT_MESSAGE,
      waitSeconds: null,
    })
  })

  it('rounds up fractional Retry-After values', () => {
    expect(
      resolveRateLimitMessage({
        status: 429,
        headers: { 'Retry-After': '2.3' },
      })
    ).toEqual({
      isRateLimit: true,
      message: '操作太快了，请等待 3 秒后再试',
      waitSeconds: 3,
    })
  })
})
