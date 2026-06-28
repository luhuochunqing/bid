import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import router from './router/index.js'
import App from './App.vue'
import { installKeyboardNavMode } from './utils/keyboardNavMode.js'

// 导入设计系统 CSS 变量
import './styles/variables.css'
import './styles/common.css'
import './styles/card-b2b.css'
import './styles/micro-interactions.css'
import './styles/attention-guidance.css'
import './styles/form-controls.css'

const app = createApp(App)
const pinia = createPinia()

// 引入全局异常上报机制
import httpClient from './api/client.js'

const reportError = (errorInfo) => {
  try {
    // 异步上报，不阻塞主流程
    httpClient.post('/api/logs/report', errorInfo, {
      skipAuthHeader: true,
      silentError: true,
      skipGlobalErrorMessage: true
    }).catch(() => {})
  } catch (e) {
    console.error('Error reporting failed', e)
  }
}

// Vue 运行时错误捕获
app.config.errorHandler = (err, instance, info) => {
  console.error('Vue Error:', err, info)
  reportError({
    level: 'ERROR',
    message: err.message || 'Unknown Vue Error',
    url: window.location.href,
    stack: err.stack,
    browserInfo: navigator.userAgent,
    route: window.location.hash || window.location.pathname,
    traceId: httpClient.defaults.headers?.['X-Trace-Id'] || ''
  })
}

// 浏览器全局错误捕获 (如 script 加载报错，异步执行报错)
window.addEventListener('error', (event) => {
  reportError({
    level: 'ERROR',
    message: event.message,
    url: window.location.href,
    stack: event.error?.stack || '',
    browserInfo: navigator.userAgent,
    route: window.location.hash || window.location.pathname
  })
})

// 未捕获的 Promise 异常
window.addEventListener('unhandledrejection', (event) => {
  reportError({
    level: 'ERROR',
    message: typeof event.reason === 'object' ? event.reason?.message || 'Unhandled Promise Rejection' : String(event.reason),
    url: window.location.href,
    stack: event.reason?.stack || '',
    browserInfo: navigator.userAgent,
    route: window.location.hash || window.location.pathname
  })
})

// Expose app instance for testing
if (typeof window !== 'undefined') {
  window.__VUE_APP__ = app
  installKeyboardNavMode()
}

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus, {
  locale: zhCn,
})
app.mount('#app')
