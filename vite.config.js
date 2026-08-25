import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  cacheDir: process.env.VITE_CACHE_DIR || 'node_modules/.vite',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        api: 'modern-compiler'
      },
      sass: {
        api: 'modern-compiler'
      }
    }
  },
  build: {
    chunkSizeWarningLimit: 980,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined
          }
          if (id.includes('@element-plus/icons-vue')) {
            return 'element-plus-icons'
          }
          if (id.includes('element-plus') || id.includes('@element-plus')) {
            return 'element-plus'
          }
          if (id.includes('echarts')) {
            return 'echarts'
          }
          if (id.includes('vue-router') || id.includes('/vue/') || id.includes('pinia') || id.includes('vuedraggable')) {
            return 'vue-vendor'
          }
          if (id.includes('@wangeditor')) {
            return 'wangeditor'
          }
          if (id.includes('marked') || id.includes('dompurify')) {
            return 'markdown'
          }
          if (id.includes('qrcode')) {
            return 'qrcode'
          }
          return 'vendor'
        }
      }
    }
  },
  server: {
    host: '0.0.0.0',
    port: 1314,
    strictPort: true,
    open: true,
    watch: {
      ignored: ['**/backend/target/**', '**/backend/.runtime/**']
    },
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_API_TARGET || 'http://127.0.0.1:18089',
        changeOrigin: true
      }
    }
  },
  preview: {
    host: '0.0.0.0',
    port: 1314,
    strictPort: true,
    proxy: {
      // E2E preview 代理：build:api 是生产构建（API_BASE_URL='' 同源），
      // preview server 需要代理 /api 到后端，否则 /api/auth/me 返回前端 HTML
      '/api': {
        target: process.env.VITE_PREVIEW_API_TARGET || 'http://127.0.0.1:18089',
        changeOrigin: true
      }
    }
  },
  test: {
    globals: true,
    environment: 'jsdom',
    // jsdom 24 缺 URL.createObjectURL/revokeObjectURL，download.js 的 setTimeout 释放逻辑
    // 会抛未处理异常导致 vitest 整批 exit 1；setup 只补缺失的浏览器 API，不放业务 mock。
    setupFiles: ['src/test-setup.js'],
    include: [
      'src/**/*.{test,spec}.{js,ts,jsx,tsx}',
      'scripts/**/*.{test,spec}.{js,ts,jsx,tsx}',
    ],
    coverage: {
      reporter: ['text', 'json', 'html']
    }
  }
})
