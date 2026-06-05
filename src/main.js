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
