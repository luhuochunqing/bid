// Input: axios error interceptor from src/api/client.js
// Output: silentError requests skip global Element Plus error toast
// Pos: src/api/ - HTTP client regression tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => {
  const responseHandlers = {}
  const httpClient = {
    post: vi.fn(),
    interceptors: {
      request: {
        use: vi.fn(),
      },
      response: {
        use: vi.fn((fulfilled, rejected) => {
          responseHandlers.fulfilled = fulfilled
          responseHandlers.rejected = rejected
        }),
      },
    },
  }
  return {
    error: vi.fn(),
    warning: vi.fn(),
    httpClient,
    responseHandlers,
  }
})

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => mocks.httpClient),
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    error: mocks.error,
    warning: mocks.warning,
  },
}))

vi.mock('@/router/index.js', () => ({
  default: {
    currentRoute: { value: { path: '/project/12' } },
    push: vi.fn(() => Promise.resolve()),
  },
}))

vi.mock('./config', () => ({
  API_CONFIG: {
    baseURL: '',
    timeout: 1000,
    headers: {},
  },
}))

vi.mock('./session.js', () => ({
  bootstrapLegacyAccessToken: vi.fn(),
  clearSessionState: vi.fn(),
  getAccessToken: vi.fn(),
  setAccessToken: vi.fn(),
}))

vi.mock('./authNormalizer.js', () => ({
  normalizeAuthSessionResponse: vi.fn((response) => response),
}))

vi.mock('./authStoreBridge.js', () => ({
  resetAuthStoreSession: vi.fn(),
  syncAuthStoreSession: vi.fn(),
}))

describe('httpClient response errors', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.resetModules()
  })

  it('skips global error toast when silentError is enabled', async () => {
    const { __resetRateLimitToastController } = await import('./client.js')
    __resetRateLimitToastController()

    const error = {
      config: { silentError: true },
      response: {
        status: 400,
        data: { msg: '未找到可用于拆解任务的标书拆解结果' },
      },
    }

    await expect(mocks.responseHandlers.rejected(error)).rejects.toBe(error)

    expect(mocks.error).not.toHaveBeenCalled()
  })

  it('skips global session-expired toast for handled login credential failures', async () => {
    const { __resetRateLimitToastController } = await import('./client.js')
    __resetRateLimitToastController()

    const error = {
      config: { skipGlobalErrorMessage: true, url: '/api/auth/login' },
      response: {
        status: 401,
        data: { msg: '用户名或密码错误' },
      },
    }

    await expect(mocks.responseHandlers.rejected(error)).rejects.toBe(error)

    expect(mocks.error).not.toHaveBeenCalled()
  })

  it('keeps global error toast for normal business errors', async () => {
    const { __resetRateLimitToastController } = await import('./client.js')
    __resetRateLimitToastController()

    const error = {
      config: {},
      response: {
        status: 400,
        data: { msg: '业务错误' },
      },
    }

    await expect(mocks.responseHandlers.rejected(error)).rejects.toBe(error)

    expect(mocks.error).toHaveBeenCalledWith('业务错误')
  })

  describe('429 rate limit handling', () => {
    it('shows friendly default message when no Retry-After header', async () => {
      const { __resetRateLimitToastController } = await import('./client.js')
      __resetRateLimitToastController()

      const error = {
        config: {},
        response: {
          status: 429,
          data: {},
        },
      }

      await expect(mocks.responseHandlers.rejected(error)).rejects.toBe(error)

      expect(mocks.warning).toHaveBeenCalledTimes(1)
      expect(mocks.warning).toHaveBeenCalledWith('操作太快了，请稍等几秒再试')
    })

    it('shows wait seconds from Retry-After header', async () => {
      const { __resetRateLimitToastController } = await import('./client.js')
      __resetRateLimitToastController()

      const error = {
        config: {},
        response: {
          status: 429,
          data: {},
          headers: { 'retry-after': '5' },
        },
      }

      await expect(mocks.responseHandlers.rejected(error)).rejects.toBe(error)

      expect(mocks.warning).toHaveBeenCalledWith('操作太快了，请等待 5 秒后再试')
    })

    it('preserves AI parse business message when server provides it', async () => {
      const { __resetRateLimitToastController } = await import('./client.js')
      __resetRateLimitToastController()

      const aiMsg = 'AI 服务请求过于频繁，请稍后再试，当前可手动填写'
      const error = {
        config: {},
        response: {
          status: 429,
          data: { msg: aiMsg },
        },
      }

      await expect(mocks.responseHandlers.rejected(error)).rejects.toBe(error)

      expect(mocks.warning).toHaveBeenCalledWith(aiMsg)
    })

    it('shows only one toast for 3 concurrent 429 errors within cooldown', async () => {
      const { __resetRateLimitToastController } = await import('./client.js')
      __resetRateLimitToastController()

      const error = {
        config: {},
        response: {
          status: 429,
          data: {},
        },
      }

      await expect(mocks.responseHandlers.rejected(error)).rejects.toBe(error)
      await expect(mocks.responseHandlers.rejected(error)).rejects.toBe(error)
      await expect(mocks.responseHandlers.rejected(error)).rejects.toBe(error)

      expect(mocks.warning).toHaveBeenCalledTimes(1)
      expect(mocks.warning).toHaveBeenCalledWith('操作太快了，请稍等几秒再试')
    })

    it('skips toast when config.silentRateLimit is true', async () => {
      const { __resetRateLimitToastController } = await import('./client.js')
      __resetRateLimitToastController()

      const error = {
        config: { silentRateLimit: true },
        response: {
          status: 429,
          data: {},
        },
      }

      await expect(mocks.responseHandlers.rejected(error)).rejects.toBe(error)

      expect(mocks.warning).not.toHaveBeenCalled()
    })
  })
})
