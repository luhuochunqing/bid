<template>
  <div v-if="hasError" class="error-boundary-fallback">
    <div class="error-boundary-content">
      <h1>页面出现异常</h1>
      <p>很抱歉，页面渲染时发生了意外错误。请尝试刷新页面。</p>
      <el-button type="primary" @click="handleReload">刷新页面</el-button>
    </div>
  </div>
  <router-view v-else />
</template>

<script setup>
import { onErrorCaptured, ref } from 'vue'
import { ElButton } from 'element-plus'

const hasError = ref(false)

onErrorCaptured((err) => {
  console.error('[ErrorBoundary] Unhandled render error:', err)
  hasError.value = true
  return false
})

function handleReload() {
  window.location.reload()
}
</script>

<style>
/* ========== Global Styles (字体已在 variables.css 中统一导入) ========== */
@import './styles/accessibility.css';
@import './styles/interactions.css';

/* ========== 全局重置 ========== */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  font-family: 'Plus Jakarta Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.error-boundary-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: var(--bg-page);
}

.error-boundary-content {
  text-align: center;
  padding: 48px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
}

.error-boundary-content h1 {
  font-size: 24px;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.error-boundary-content p {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 24px;
}
</style>
