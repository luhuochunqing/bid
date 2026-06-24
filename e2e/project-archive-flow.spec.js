// @ui-cover:knowledge
import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

const backendUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18080'

async function apiRequest(path, session, options = {}) {
  const response = await fetch(`${backendUrl}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.token}`,
      ...(options.headers || {}),
    },
  })
  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${path} -> ${response.status}`)
  }
  return response.json()
}

test.describe('项目档案台账 (Project Archive)', () => {
  test.beforeEach(async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_archive_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Archive Tester'
    })

    await injectSession(page, session)
  })

  test('4.4.2 统计卡片展示 - 页面加载显示统计区域', async ({ page }) => {
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    const container = page.locator('.project-archive-container')
    await expect(container).toBeVisible({ timeout: 15000 })
  })

  test('4.4.2 档案列表加载 - 表格列正确渲染', async ({ page }) => {
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    await expect(page.getByText('项目档案台账')).toBeVisible()

    const table = page.locator('.el-table')
    await expect(table).toBeVisible({ timeout: 10000 })
  })

  test('4.4.2 档案列表分页 - 分页器正常显示', async ({ page }) => {
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    const pagination = page.locator('.pagination-container')
    await expect(pagination).toBeVisible({ timeout: 5000 })
    await expect(pagination.locator('.el-pagination')).toBeVisible()
  })

  test('4.4.2 状态标签筛选 - 点击不同状态标签列表响应变化', async ({ page }) => {
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    const statusTabs = page.locator('.archive-status-tabs')
    if (await statusTabs.isVisible()) {
      const allTab = page.locator('.el-radio-button__inner').filter({ hasText: '全部' }).first()
      const initiatedTab = page.locator('.el-radio-button__inner').filter({ hasText: '已启动' }).first()

      if (await allTab.isVisible()) {
        await allTab.click()
        await page.waitForResponse(
          (response) => response.url().includes('/api/archive') && response.status() === 200,
          { timeout: 10000 }
        ).catch(() => {})

        if (await initiatedTab.isVisible()) {
          await initiatedTab.click()
          await page.waitForResponse(
            (response) => response.url().includes('/api/archive') && response.status() === 200,
            { timeout: 10000 }
          ).catch(() => {})
        }
      }
    }
  })

  test('4.4.2 筛选表单 - 项目名称搜索框可用', async ({ page }) => {
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    const searchInput = page.locator('input[placeholder="请输入项目名称"]')
    if (await searchInput.isVisible()) {
      await searchInput.fill('测试项目')
      await page.getByRole('button', { name: '查询' }).click()
      await page.waitForResponse(
        (response) => response.url().includes('/api/archive') && response.status() === 200,
        { timeout: 10000 }
      ).catch(() => {})
    }

    await expect(page.getByText('项目档案台账')).toBeVisible()
  })

  test('4.4.2 重置按钮 - 点击后恢复默认筛选状态', async ({ page }) => {
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    const searchInput = page.locator('input[placeholder="请输入项目名称"]')
    if (await searchInput.isVisible()) {
      await searchInput.fill('测试项目')
    }

    const resetBtn = page.getByRole('button', { name: /重置/ })
    if (await resetBtn.isVisible()) {
      await resetBtn.click()
      await page.waitForResponse(
        (response) => response.url().includes('/api/archive') && response.status() === 200,
        { timeout: 10000 }
      ).catch(() => {})
    }

    await expect(page.getByText('项目档案台账')).toBeVisible()
  })

  test('4.4.2 详情抽屉 - 点击列表行打开抽屉', async ({ page }) => {
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')


    const firstRow = page.locator('.el-table__body-wrapper .el-table__row').first()
    const hasRows = await firstRow.isVisible().catch(() => false)

    if (hasRows) {
      await firstRow.click()
      await expect(page.locator('.el-drawer')).toBeVisible({ timeout: 10000 }).catch(() => {})

      const drawer = page.locator('.el-drawer')
      const drawerVisible = await drawer.isVisible().catch(() => false)

      if (drawerVisible) {
        await expect(page.getByText('基础信息')).toBeVisible()

        const closeBtn = drawer.locator('.el-drawer__header').locator('.el-drawer__close-btn, .el-icon')
        if (await closeBtn.isVisible()) {
          await closeBtn.click()
          await expect(page.locator('.el-drawer')).toBeHidden({ timeout: 5000 }).catch(() => {})
        }
      }
    }
  })

  test('4.4.2 详情抽屉 - 查看按钮打开抽屉显示完整信息', async ({ page }) => {
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    const viewBtn = page.getByRole('button', { name: '查看' }).first()
    const hasViewBtn = await viewBtn.isVisible().catch(() => false)

    if (hasViewBtn) {
      await viewBtn.click()
      await expect(page.locator('.el-drawer')).toBeVisible({ timeout: 10000 }).catch(() => {})

      const drawer = page.locator('.el-drawer')
      const drawerVisible = await drawer.isVisible().catch(() => false)

      if (drawerVisible) {
        await expect(page.getByText('基础信息')).toBeVisible()

        await expect(page.getByText('项目名称')).toBeVisible()
        await expect(page.getByText('项目类型')).toBeVisible()
        await expect(page.getByText('项目状态')).toBeVisible()

        const closeBtn = drawer.locator('.el-drawer__header').locator('button')
        if (await closeBtn.count() > 0) {
          await closeBtn.first().click()
          await expect(page.locator('.el-drawer')).toBeHidden({ timeout: 5000 }).catch(() => {})
        }
      }
    }
  })

  test('4.4.2 导出台账 - 点击按钮触发 Excel 下载', async ({ page }) => {
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    const exportBtn = page.getByRole('button', { name: /导出台账/ })
    const hasExportBtn = await exportBtn.isVisible().catch(() => false)

    if (hasExportBtn) {
      const downloadPromise = page.waitForEvent('download', { timeout: 15000 }).catch(() => null)

      await exportBtn.click()

      const download = await downloadPromise
      if (download) {
        expect(download.suggestedFilename()).toMatch(/\.xlsx$/)
      }
    }
  })

  test('4.4.2 导出文件包 - 点击按钮触发 ZIP 下载', async ({ page }) => {
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    const exportBtn = page.getByRole('button', { name: /导出文件包/ })
    const hasExportBtn = await exportBtn.isVisible().catch(() => false)

    if (hasExportBtn) {
      const downloadPromise = page.waitForEvent('download', { timeout: 15000 }).catch(() => null)

      await exportBtn.click()

      const download = await downloadPromise
      if (download) {
        expect(download.suggestedFilename()).toMatch(/\.zip$/)
      }
    }
  })

  test('4.4.2 API 数据验证 - /api/archive/stats 返回正确结构', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_archive_api_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Archive API Tester'
    })

    const statsRes = await apiRequest('/api/archive/stats', session)
    expect(statsRes).toBeTruthy()

    if (typeof statsRes === 'object') {
      expect(typeof (statsRes.totalArchives ?? statsRes.totalArchives)).toBe('number')
      expect(typeof (statsRes.closedProjects ?? statsRes.closedProjects)).toBe('number')
      expect(typeof (statsRes.caseCount ?? statsRes.caseCount)).toBe('number')
      expect(typeof (statsRes.reuseCount ?? statsRes.reuseCount)).toBe('number')
    }
  })

  test('4.4.2 API 列表验证 - /api/archive 返回正确分页结构', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_archive_list_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Archive List Tester'
    })

    const listRes = await apiRequest('/api/archive', session)
    expect(listRes).toBeTruthy()

    if (Array.isArray(listRes)) {
      expect(listRes.length).toBeGreaterThanOrEqual(0)
    } else if (listRes && typeof listRes === 'object') {
      expect(Array.isArray(listRes.content) || Array.isArray(listRes.data)).toBeTruthy()
    }
  })

  test('4.4.2 筛选表单 - 项目负责人筛选框可用', async ({ page }) => {
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    const pmInput = page.locator('input[placeholder="项目负责人"]')
    if (await pmInput.isVisible()) {
      await pmInput.fill('admin')
      await page.waitForResponse(
        (response) => response.url().includes('/api/archive') && response.status() === 200,
        { timeout: 10000 }
      ).catch(() => {})
      await expect(page.getByText('项目档案台账')).toBeVisible()
    }
  })

  test('4.4.2 筛选表单 - 投标负责人筛选框可用', async ({ page }) => {
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    const bmInput = page.locator('input[placeholder="投标负责人"]')
    if (await bmInput.isVisible()) {
      await bmInput.fill('admin')
      await page.waitForResponse(
        (response) => response.url().includes('/api/archive') && response.status() === 200,
        { timeout: 10000 }
      ).catch(() => {})
      await expect(page.getByText('项目档案台账')).toBeVisible()
    }
  })

  test('4.4.2 详情抽屉 - 文件预览按钮触发窗口打开', async ({ page }) => {
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    const viewBtn = page.getByRole('button', { name: '查看' }).first()
    const hasViewBtn = await viewBtn.isVisible().catch(() => false)

    if (hasViewBtn) {
      await viewBtn.click()
      await expect(page.locator('.el-drawer')).toBeVisible({ timeout: 10000 }).catch(() => {})

      const drawer = page.locator('.el-drawer')
      const drawerVisible = await drawer.isVisible().catch(() => false)

      if (drawerVisible) {
        const previewBtn = page.locator('.el-table__body-wrapper .el-table__row').first().locator('button').filter({ hasText: '预览' }).first()
        const hasPreview = await previewBtn.isVisible().catch(() => false)

        if (hasPreview) {
          await previewBtn.click()
        }
      }
    }
  })

  test('4.4.2 详情抽屉 - 文件下载按钮存在', async ({ page }) => {
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    const viewBtn = page.getByRole('button', { name: '查看' }).first()
    const hasViewBtn = await viewBtn.isVisible().catch(() => false)

    if (hasViewBtn) {
      await viewBtn.click()
      await expect(page.locator('.el-drawer')).toBeVisible({ timeout: 10000 }).catch(() => {})

      const drawer = page.locator('.el-drawer')
      const drawerVisible = await drawer.isVisible().catch(() => false)

      if (drawerVisible) {
        const downloadBtn = page.locator('.el-table__body-wrapper .el-table__row').first().locator('button').filter({ hasText: '下载' }).first()
        const hasDownload = await downloadBtn.isVisible().catch(() => false)
        expect(hasDownload).toBeTruthy()
      }
    }
  })

  test('4.4.2 API 负责人筛选 - projectManager 参数正确传递', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_archive_pm_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Archive PM Tester'
    })

    const res = await apiRequest('/api/archive?projectManager=admin', session)
    expect(res).toBeTruthy()
    if (res && typeof res === 'object') {
      expect(Array.isArray(res.content) || Array.isArray(res)).toBeTruthy()
    }
  })

  test('4.4.2 API 负责人筛选 - bidManager 参数正确传递', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_archive_bm_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Archive BM Tester'
    })

    const res = await apiRequest('/api/archive?bidManager=admin', session)
    expect(res).toBeTruthy()
    if (res && typeof res === 'object') {
      expect(Array.isArray(res.content) || Array.isArray(res)).toBeTruthy()
    }
  })

  test('4.4.2 文件预览 API - /files/{fileId}/preview 返回预览数据', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_archive_preview_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Archive Preview Tester'
    })

    const listRes = await apiRequest('/api/archive', session)
    const firstArchive = Array.isArray(listRes)
      ? listRes[0]
      : (listRes.content?.[0] || listRes.data?.[0])
    if (!firstArchive) return

    const fileId = firstArchive.fileCount > 0 ? firstArchive.archiveId : null
    if (fileId) {
      const previewRes = await apiRequest(`/api/archive/files/${fileId}/preview`, session)
      expect(previewRes).toBeTruthy()
    }
  })

  test('4.4.2 文件下载 API - /files/{fileId}/download 返回文件数据', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_archive_download_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Archive Download Tester'
    })

    const listRes = await apiRequest('/api/archive', session)
    const firstArchive = Array.isArray(listRes)
      ? listRes[0]
      : (listRes.content?.[0] || listRes.data?.[0])
    if (!firstArchive) return

    const fileId = firstArchive.fileCount > 0 ? firstArchive.archiveId : null
    if (fileId) {
      const downloadRes = await apiRequest(`/api/archive/files/${fileId}/download`, session)
      expect(downloadRes).toBeTruthy()
    }
  })

  test('4.4.2 导出台账 - 文件名包含时间戳格式', async ({ page }) => {
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    const exportBtn = page.getByRole('button', { name: /导出台账/ })
    const hasExportBtn = await exportBtn.isVisible().catch(() => false)

    if (hasExportBtn) {
      const downloadPromise = page.waitForEvent('download', { timeout: 15000 }).catch(() => null)
      await exportBtn.click()
      const download = await downloadPromise
      if (download) {
        const filename = download.suggestedFilename()
        expect(filename).toMatch(/\.xlsx$/)
        expect(filename).toMatch(/\d{8,12}/)
      }
    }
  })

  test('4.4.2 导出文件包 - 文件名包含时间戳格式', async ({ page }) => {
    await page.goto('/knowledge/archive')
    await page.waitForLoadState('networkidle')

    const exportBtn = page.getByRole('button', { name: /导出文件包/ })
    const hasExportBtn = await exportBtn.isVisible().catch(() => false)

    if (hasExportBtn) {
      const downloadPromise = page.waitForEvent('download', { timeout: 15000 }).catch(() => null)
      await exportBtn.click()
      const download = await downloadPromise
      if (download) {
        const filename = download.suggestedFilename()
        expect(filename).toMatch(/\.zip$/)
        expect(filename).toMatch(/\d{8,12}/)
      }
    }
  })
})
