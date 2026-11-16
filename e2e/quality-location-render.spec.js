// Actual Vue/Element Plus component in Chromium, without API interception or a dev server.
import { test, expect } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

let script
let styles

test.beforeAll(async () => {
  const [{ build }, { default: vue }] = await Promise.all([import('vite'), import('@vitejs/plugin-vue')])
  const directory = resolve('.rehearsal/quality-location')
  await mkdir(directory, { recursive: true })
  const entry = resolve(directory, 'entry.js')
  await writeFile(entry, `
    import { createApp, h } from 'vue'
    import { createRouter, createMemoryHistory } from 'vue-router'
    import ElementPlus from 'element-plus'
    import 'element-plus/dist/index.css'
    import Detail from '../../src/views/Project/stages/components/QualityCheckDetailDialog.vue'
    window.renderQualityItem = item => {
      const router = createRouter({ history: createMemoryHistory(), routes: [] })
      createApp({ render: () => h(Detail, { modelValue: true, item }) })
        .use(router).use(ElementPlus).mount('#app')
    }
  `)
  const bundle = await build({
    configFile: false,
    define: { 'process.env.NODE_ENV': JSON.stringify('production') },
    plugins: [vue()],
    resolve: { alias: { '@': resolve('src') }, dedupe: ['vue', 'vue-router'] },
    logLevel: 'error',
    build: { write: false, minify: false, cssCodeSplit: false,
      lib: { entry, name: 'QualityLocation', formats: ['iife'] } }
  })
  const output = (Array.isArray(bundle) ? bundle : [bundle]).flatMap(result => result.output)
  script = output.find(asset => asset.type === 'chunk').code
  styles = output.filter(asset => asset.type === 'asset' && asset.fileName.endsWith('.css'))
    .map(asset => String(asset.source)).join('\n')
})

async function render(page, item) {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.setContent('<div id="app"></div>')
  await page.addStyleTag({ content: styles })
  await page.addScriptTag({ content: script })
  expect(errors).toEqual([])
  await page.evaluate(value => window.renderQualityItem(value), item)
  const dialog = page.getByRole('dialog', { name: '检查项详情' })
  await expect(dialog).toBeVisible()
  return dialog
}

test('unpaginated evidence displays original-text lookup without a fabricated page', async ({ page }) => {
  const dialog = await render(page, {
    itemCode: 'RESPONSIVENESS_REVIEW', title: '响应性评审条款', status: 'MANUAL_REVIEW',
    conclusion: '需核对响应原文', evidences: [{ sourceType: 'BID', pageNumber: null, quote: '测试标书中的待核对原文' }]
  })
  await expect(dialog).toContainText('未取得原文件页码，请按原文摘录检索')
  await expect(dialog).toContainText('测试标书中的待核对原文')
  await expect(dialog).not.toContainText('阅读器缩放')
  await page.screenshot({ path: test.info().outputPath('unpaginated-location.png'), animations: 'disabled' })
})

test('identical page numbers in separate books retain both source names', async ({ page }) => {
  const dialog = await render(page, {
    itemCode: 'RESPONSIVENESS_REVIEW', title: '响应性评审条款', status: 'MANUAL_REVIEW',
    evidences: [
      { sourceType: 'BID', sourceFileName: '商务.pdf', sourceFileHash: 'business', pageNumber: 2, quote: '商务响应原文' },
      { sourceType: 'BID', sourceFileName: '技术.pdf', sourceFileHash: 'technical', pageNumber: 2, quote: '技术响应原文' }
    ]
  })
  await expect(dialog).toContainText('商务.pdf：第 2 页；技术.pdf：第 2 页')
})

test('qualification location names its original file and local page', async ({ page }) => {
  const dialog = await render(page, {
    itemCode: 'QUALIFICATION_MATCH', title: '资质匹配', status: 'MANUAL_REVIEW', evidences: [],
    details: [{ requirement: '证书要求', matchedCertName: '证书名称', status: 'ATTENTION',
      bidSourceFileName: '资质.pdf', bidPageNumber: 7, bidQuote: '证书证明材料', remark: '需核对有效期' }]
  })
  await expect(dialog).toContainText('资质.pdf：第 7 页')
})
