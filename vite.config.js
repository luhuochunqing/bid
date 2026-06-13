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
          if (id.includes('element-plus') || id.includes('@element-plus')) {
            return 'element-plus'
          }
          if (id.includes('echarts')) {
            return 'echarts'
          }
          if (id.includes('vue-router') || id.includes('/vue/') || id.includes('pinia') || id.includes('vuedraggable')) {
            return 'vue-vendor'
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
    }
  },
  preview: {
    host: '0.0.0.0',
    port: 1314,
    strictPort: true
  },
  test: {
    globals: true,
    environment: 'jsdom',
    include: [
      'src/**/*.{test,spec}.{js,ts,jsx,tsx}',
      'scripts/**/*.{test,spec}.{js,ts,jsx,tsx}',
    ],
    coverage: {
      reporter: ['text', 'json', 'html']
    }
  }
})
