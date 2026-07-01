/**
 * Sentry 前端错误追踪配置
 *
 * 捕获：
 * - Vue 组件渲染错误
 * - 未捕获的 Promise rejection
 * - JavaScript 运行时错误（TypeError、ReferenceError）
 * - 资源加载失败（可选）
 *
 * 过滤：
 * - 网络错误（NetworkError、Axios cancel）
 * - 无关的浏览器插件错误
 * - ResizeObserver 等非关键警告
 *
 * 环境变量：
 * - VITE_SENTRY_DSN: Sentry DSN（必填，否则 Sentry 自动禁用）
 * - VITE_SENTRY_ENVIRONMENT: 环境标识（dev/staging/production）
 * - VITE_SENTRY_TRACES_SAMPLE_RATE: 性能追踪采样率（0.0-1.0）
 */
import * as Sentry from '@sentry/vue'
import router from './router/index.js'

/**
 * 需要忽略的错误类型（非系统缺陷）
 * - 网络错误：用户网络问题或后端暂时不可用
 * - Axios 取消：用户主动取消请求
 * - ResizeObserver：浏览器内部警告，不影响功能
 * - 浏览器插件：第三方扩展注入的错误
 */
const IGNORED_ERRORS = [
  // 网络错误
  'NetworkError',
  'AxiosError',
  'ERR_NETWORK',
  'ERR_CANCELED',
  // ResizeObserver 警告（非关键）
  'ResizeObserver loop limit exceeded',
  'ResizeObserver loop completed with undelivered notifications',
  // 浏览器插件错误
  'Non-Error promise rejection captured',
  // 元素 Plus 内部警告（非关键）
  'ElementPlusError',
]

/**
 * 初始化 Sentry
 * @param {App} app - Vue 应用实例
 */
export function initSentry(app) {
  const dsn = import.meta.env.VITE_SENTRY_DSN

  // 无 DSN 时 Sentry 自动禁用（开发环境可能不配置）
  if (!dsn || dsn.trim() === '') {
    console.info('[Sentry] DSN not configured, Sentry disabled')
    return
  }

  const environment = import.meta.env.VITE_SENTRY_ENVIRONMENT || 'dev'
  const tracesSampleRate = parseFloat(import.meta.env.VITE_SENTRY_TRACES_SAMPLE_RATE || '0.1')
  const release = import.meta.env.VITE_APP_VERSION || undefined

  Sentry.init({
    app,
    dsn,
    environment,
    release,
    integrations: [
      // Vue 错误捕获
      Sentry.vueIntegration({
        // 捕获组件渲染错误
        attachProps: true,
        // 捕获生命周期错误
        hooks: ['activate', 'mount', 'update', 'destroy']
      }),
      // 路由追踪（性能监控）
      Sentry.browserTracingIntegration({
        router,
        // 记录路由参数（排除敏感信息）
        routeParamTransformer: (params) => {
          // 移除可能的敏感参数
          const sanitized = { ...params }
          delete sanitized.token
          delete sanitized.password
          return sanitized
        }
      }),
      // 全局错误捕获
      Sentry.globalHandlersIntegration({
        onerror: true,
        onunhandledrejection: true
      }),
      // Breadcrumbs（错误上下文）
      Sentry.breadcrumbsIntegration({
        console: true,    // 记录 console.log/warn/error
        dom: true,        // 记录 DOM 事件（点击、输入）
        fetch: true,      // 记录 API 调用
        xhr: true
      })
    ],

    // 性能追踪采样率
    tracesSampleRate,

    // 过滤错误
    ignoreErrors: IGNORED_ERRORS,

    // 错误发生前的 breadcrumb 数量
    maxBreadcrumbs: 50,

    // beforeSend 回调：附加用户上下文
    beforeSend(event, hint) {
      // 附加用户信息（从 localStorage 读取）
      try {
        const userStr = localStorage.getItem('user')
        if (userStr) {
          const user = JSON.parse(userStr)
          event.setUser({
            id: user.id?.toString(),
            username: user.username,
            email: undefined  // 不收集邮箱
          })
          // 附加角色信息
          event.setTag('roleCode', user.roleCode || 'unknown')
        }
      } catch {
        // 用户信息解析失败不影响上报
      }

      // 附加路由信息
      if (router.currentRoute?.value) {
        event.setTag('route', router.currentRoute.value.path)
        event.setExtra('routeQuery', router.currentRoute.value.query)
      }

      return event
    }
  })

  console.info(`[Sentry] Initialized: env=${environment}, tracesSampleRate=${tracesSampleRate}`)
}

/**
 * 手动上报错误（用于关键业务流程）
 * @param {Error} error - 错误对象
 * @param {Object} context - 额外上下文
 */
export function captureError(error, context = {}) {
  Sentry.withScope((scope) => {
    if (context.tags) {
      Object.entries(context.tags).forEach(([key, value]) => {
        scope.setTag(key, value)
      })
    }
    if (context.extra) {
      Object.entries(context.extra).forEach(([key, value]) => {
        scope.setExtra(key, value)
      })
    }
    Sentry.captureException(error)
  })
}

/**
 * 手动上报消息（用于非异常级别的问题）
 * @param {string} message - 消息内容
 * @param {string} level - 级别（'info' | 'warning' | 'error'）
 */
export function captureMessage(message, level = 'warning') {
  Sentry.captureMessage(message, level)
}