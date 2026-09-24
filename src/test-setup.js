// Input: vitest jsdom 环境（vite.config.js test.setupFiles 引入）
// Output: 浏览器有而 jsdom 24 缺失的 URL 对象 API 兜底 stub
// Pos: src/ - 全局测试 setup（每个测试文件运行前执行）
// 维护声明: 仅补 jsdom 缺失的浏览器 API，禁止放业务 mock（业务 mock 归各 spec 自身）。
//           新增 stub 前先确认 jsdom 版本确实缺失（升级 jsdom 后可删）。

// Node 25+ exposes a process-level localStorage getter that has no backing store
// unless --localstorage-file is set. Vitest 1.6 does not replace that existing
// global with jsdom's storage, so bind the browser global to the jsdom instance.
if (globalThis.jsdom?.window) {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: globalThis.jsdom.window.localStorage
  })
}

// 背景：src/utils/download.js 下载后在 setTimeout 里调 URL.revokeObjectURL，
// jsdom 24 未实现该 API → 未处理异常让 vitest 整批 exit 1（212 文件全绿也被拦）。
if (typeof URL.createObjectURL !== 'function') {
  URL.createObjectURL = () => 'blob:mocked-by-test-setup'
}
if (typeof URL.revokeObjectURL !== 'function') {
  URL.revokeObjectURL = () => {}
}

// 过滤测试中 Element Plus 未解析组件与指令产生的 Vue warn 噪音（组件在各 spec 自行 stub 或挂载）
const originalWarn = console.warn
console.warn = (...args) => {
  const msg = typeof args[0] === 'string' ? args[0] : ''
  if (
    msg.includes('Failed to resolve component: el-') ||
    msg.includes('Failed to resolve directive:')
  ) {
    return
  }
  originalWarn.apply(console, args)
}
