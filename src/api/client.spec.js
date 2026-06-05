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
    await import('./client.js')

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
    await import('./client.js')

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
    await import('./client.js')

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
})
