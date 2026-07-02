import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

// 捕获 Sentry.init 调用参数，以便取出 beforeSend 回调做行为测试。
// 这是回归 CO-sentry-beforesend 的关键测试：原 bug 是 beforeSend 调用
// event.setTag/setExtra/setUser（这些方法在 Event 对象上不存在），
// 导致 TypeError 直接冒泡到 SDK，污染 Sentry issue 流。
let capturedInitOptions = null
const sentryMock = {
  init: vi.fn((opts) => { capturedInitOptions = opts }),
  vueIntegration: vi.fn((x) => x),
  browserTracingIntegration: vi.fn((x) => x),
  globalHandlersIntegration: vi.fn((x) => x),
  breadcrumbsIntegration: vi.fn((x) => x),
  withScope: vi.fn(),
  captureException: vi.fn(),
  captureMessage: vi.fn()
}
vi.mock('@sentry/vue', () => ({
  default: sentryMock,
  ...sentryMock
}))

// mock router，使 currentRoute 可由单测控制
const mockRouter = { currentRoute: { value: null } }
vi.mock('./router/index.js', () => ({
  default: mockRouter
}))

// 动态 import，确保 mock 生效后再加载 sentry.js
let initSentry
beforeEach(async () => {
  vi.resetModules()
  // 重新设置 mock（resetModules 后需要重置 captured）
  capturedInitOptions = null
  // 重新加载 router mock 的当前值
  const mod = await import('./sentry.js')
  initSentry = mod.initSentry
})

afterEach(() => {
  localStorage.clear()
  mockRouter.currentRoute.value = null
  vi.unstubAllEnvs()
})

function initWithDsn() {
  vi.stubEnv('VITE_SENTRY_DSN', 'https://fake@example.com/1')
  vi.stubEnv('VITE_SENTRY_ENVIRONMENT', 'test')
  initSentry({})
  expect(capturedInitOptions).toBeTruthy()
  return capturedInitOptions.beforeSend
}

describe('initSentry / beforeSend 回归测试', () => {
  it('无 DSN 时跳过初始化（不调用 Sentry.init）', () => {
    vi.stubEnv('VITE_SENTRY_DSN', '')
    initSentry({})
    expect(capturedInitOptions).toBeNull()
  })

  it('有 DSN 时调用 Sentry.init 并注册 beforeSend', () => {
    const beforeSend = initWithDsn()
    expect(typeof beforeSend).toBe('function')
  })

  it('localStorage 有用户信息时附加 user 与 roleCode tag', () => {
    const beforeSend = initWithDsn()
    localStorage.setItem('user', JSON.stringify({
      id: 42,
      username: 'alice',
      roleCode: 'bid-Team'
    }))
    const event = { tags: { existing: 'keep' } }
    const result = beforeSend(event, {})
    expect(result.user).toEqual({ id: '42', username: 'alice', email: undefined })
    expect(result.tags.existing).toBe('keep')   // 既有 tag 保留
    expect(result.tags.roleCode).toBe('bid-Team')
  })

  it('localStorage 用户信息缺失 roleCode 时 fallback 为 unknown', () => {
    const beforeSend = initWithDsn()
    localStorage.setItem('user', JSON.stringify({ id: 1, username: 'bob' }))
    const event = {}
    const result = beforeSend(event, {})
    expect(result.tags.roleCode).toBe('unknown')
  })

  it('localStorage 为空时不设置 user，也不抛错', () => {
    const beforeSend = initWithDsn()
    const event = {}
    const result = beforeSend(event, {})
    expect(result.user).toBeUndefined()
  })

  it('localStorage 内容非法 JSON 时不抛错（被 try/catch 吞掉）', () => {
    const beforeSend = initWithDsn()
    localStorage.setItem('user', '{not json')
    const event = {}
    expect(() => beforeSend(event, {})).not.toThrow()
  })

  it('router.currentRoute 有值时附加 route tag 与 routeQuery extra', () => {
    const beforeSend = initWithDsn()
    mockRouter.currentRoute.value = {
      path: '/project/128',
      query: { tab: 'docs' }
    }
    const event = {}
    const result = beforeSend(event, {})
    expect(result.tags.route).toBe('/project/128')
    expect(result.extra.routeQuery).toEqual({ tab: 'docs' })
  })

  // === 关键回归：原 bug 的根因 ===
  // Event 对象没有 setTag/setExtra/setUser 方法；旧代码直接调用
  // event.setTag('route', ...) 会抛 TypeError: event.setTag is not a function，
  // 且因为路由附加块没有 try/catch，错误冒泡到 Sentry SDK 被当作新错误上报。
  it('回归：beforeSend 不会因 event.setTag 不存在而抛 TypeError', () => {
    const beforeSend = initWithDsn()
    mockRouter.currentRoute.value = { path: '/login', query: {} }
    const event = {}  // 干净的 Event 对象，没有任何 setXxx 方法
    expect(() => beforeSend(event, {})).not.toThrow()
  })

  it('回归：beforeSend 在 user 与 route 都触发时仍返回 event', () => {
    const beforeSend = initWithDsn()
    localStorage.setItem('user', JSON.stringify({ id: 7, username: 'carol', roleCode: 'admin' }))
    mockRouter.currentRoute.value = { path: '/dashboard', query: {} }
    const event = {}
    const result = beforeSend(event, {})
    expect(result).toBe(event)
    expect(result.user.username).toBe('carol')
    expect(result.tags.roleCode).toBe('admin')
    expect(result.tags.route).toBe('/dashboard')
  })

  it('回归：router.currentRoute.value 读取异常时不抛错（route 块也有 try/catch）', () => {
    const beforeSend = initWithDsn()
    // 用 getter 抛错模拟 router 异常
    Object.defineProperty(mockRouter.currentRoute, 'value', {
      get() { throw new Error('router destroyed') },
      configurable: true
    })
    const event = {}
    expect(() => beforeSend(event, {})).not.toThrow()
    // 恢复
    Object.defineProperty(mockRouter.currentRoute, 'value', {
      value: null,
      configurable: true,
      writable: true
    })
  })
})
