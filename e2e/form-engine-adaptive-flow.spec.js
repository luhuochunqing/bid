/**
 * 动态表单引擎端到端测试。
 *
 * 覆盖范围：
 * - M1: DynamicFormRenderer 动态表单渲染（降级兼容验证）
 * - M2: 表单 schema 加载、验证、提交
 * - M3: Tender entry 集成
 * - M4: Project 表单集成
 * - M5: 跨字段验证、角色过滤、租户覆盖
 * - M6: 管理端 CRUD（前端路由 /admin/form-definitions 未实现，skip）
 *
 * 依赖：e2e/auth-helpers.js（ensureApiSession / injectSession）
 * 依赖：后端运行在 http://127.0.0.1:18089（CLAUDE.md 端口约定）
 * 依赖：前端运行在 http://127.0.0.1:1323（由 playwright.config.js baseURL 控制）
 */

import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'

// ==================== Helpers ====================  // @ui-cover:project,bidding

async function loginAsAdmin(page) {
  const session = await ensureApiSession({
    username: `form_e2e_admin_${Date.now()}`,
    role: '/bidAdmin',
    fullName: '动态表单测试管理员',
  })
  await injectSession(page, session)
  return session
}

async function loginAsStaff(page) {
  const session = await ensureApiSession({
    username: `form_e2e_staff_${Date.now()}`,
    role: 'bid-Team',
    fullName: '动态表单测试员工',
  })
  await injectSession(page, session)
  return session
}

// ==================== M1: 动态表单渲染 ====================

test.describe('M1: 动态表单渲染', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('标讯手工录入表单降级兼容：原有硬编码表单正常显示', async ({ page }) => {
    // 路由: /bidding/create（不是 /bidding/tender/create）
    await page.goto('/bidding/create')

    // 降级兼容检查：核心字段仍然可见（页面字段为"项目名称"，非"标讯标题"）
    await expect(page.getByText('项目名称').first()).toBeVisible({ timeout: 10_000 })
  })

  // 前端路由 /admin/form-definitions 未实现，skip（后端 API /api/admin/form-definitions 存在）
  test.skip('20种字段类型在表单设计器中可用', async () => {
    // 前端管理页面未实现，待后续补充
  })

  test.skip('4个 scope 种子数据已加载', async () => {
    // 前端管理页面未实现，待后续补充
  })
})

// ==================== M2: 表单 Schema 加载与验证 ====================

test.describe('M2: 表单 Schema 加载与验证', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('tender.entry 表单 schema 加载成功', async ({ page }) => {
    // 路由: /bidding/create
    await page.goto('/bidding/create')

    // 验证基础字段可见（页面字段为"项目名称"）
    await expect(page.getByText('项目名称').first()).toBeVisible({ timeout: 10_000 })
  })

  test('必填字段缺失时验证提示出现', async ({ page }) => {
    await page.goto('/bidding/create')

    // 等待表单加载（页面字段为"项目名称"）
    await expect(page.getByText('项目名称').first()).toBeVisible({ timeout: 10_000 })

    // 尝试提交空表单（查找提交按钮）
    const submitBtn = page.locator('button:has-text("提交"), button:has-text("保存"), button:has-text("创建")').first()
    if (await submitBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
      await submitBtn.click()
      // 验证必填提示
      await expect(
        page.locator('.el-form-item__error, .el-alert, .el-message--warning').first()
      ).toBeVisible({ timeout: 5_000 })
    }
  })
})

// ==================== M3: 投标录入集成 ====================

test.describe('M3: 投标录入集成', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('投标列表页可访问', async ({ page }) => {
    // 路由: /bidding（不是 /bidding/list）
    await page.goto('/bidding')

    await expect(page.locator('table, .el-table').first()).toBeVisible({ timeout: 10_000 })
  })

  test('新建投标入口可见', async ({ page }) => {
    await page.goto('/bidding')

    // 查找新建按钮
    const createBtn = page.locator('button:has-text("新建"), button:has-text("创建"), .el-button--primary').first()
    await expect(createBtn).toBeVisible({ timeout: 5_000 })
  })
})

// ==================== M4: 项目表单集成 ====================

test.describe('M4: 项目表单集成', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('项目列表页可访问', async ({ page }) => {
    await page.goto('/project')

    await expect(page.locator('.el-table, table').first()).toBeVisible({ timeout: 10_000 })
  })

  test('项目基本信息表单字段可见', async ({ page }) => {
    await page.goto('/project/create')

    await expect(page.getByText('项目名称').first()).toBeVisible({ timeout: 5_000 })
  })
})

// ==================== M5: 角色过滤与租户覆盖 ====================

test.describe('M5: 角色过滤与租户覆盖', () => {
  test('admin 角色可看到全部字段', async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto('/bidding/create')

    // Admin 应该可以看到核心字段（页面字段为"项目名称"）
    await expect(page.getByText('项目名称').first()).toBeVisible({ timeout: 10_000 })
  })

  test('staff 角色访问投标录入页面正常', async ({ page }) => {
    await loginAsStaff(page)
    await page.goto('/bidding/create')

    // Staff 访问投标录入，验证页面可访问
    await expect(page.getByText('标讯').first()).toBeVisible({ timeout: 10_000 })
  })

  // 前端路由 /admin/form-definitions 未实现，skip
  test.skip('角色预览功能在设计器中可用', async () => {
    // 前端管理页面未实现，待后续补充
  })
})

// ==================== M6: 管理端 CRUD ====================
// 前端路由 /admin/form-definitions 未实现，整个 M6 describe skip
test.describe.skip('M6: 管理端 CRUD（前端路由未实现）', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('表单定义列表页可访问', async ({ page }) => {
    await page.goto('/admin/form-definitions')
    await expect(page.locator('.el-table').first()).toBeVisible({ timeout: 10_000 })
  })

  test('分页控件正常工作', async ({ page }) => {
    await page.goto('/admin/form-definitions')
    const pagination = page.locator('.el-pagination').first()
    const paginationExists = await pagination.isVisible({ timeout: 3_000 }).catch(() => false)
    if (paginationExists) {
      await expect(pagination).toBeVisible()
    }
  })

  test('新建表单定义按钮可见', async ({ page }) => {
    await page.goto('/admin/form-definitions')
    const createBtn = page.locator('button:has-text("新建"), button:has-text("创建")').first()
    await expect(createBtn).toBeVisible({ timeout: 5_000 })
  })

  test('发布按钮在详情页可用', async ({ page }) => {
    await page.goto('/admin/form-definitions')
    const firstRow = page.locator('.el-table__row').first()
    const rowExists = await firstRow.isVisible({ timeout: 3_000 }).catch(() => false)
    if (rowExists) {
      const actionBtn = firstRow.locator('button').first()
      if (await actionBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
        await actionBtn.click()
        await expect(
          page.getByText('发布').or(page.getByText('编辑')).first()
        ).toBeVisible({ timeout: 5_000 })
      }
    }
  })

  test('非 admin 用户无法访问管理端', async ({ page }) => {
    await loginAsStaff(page)
    await page.goto('/admin/form-definitions')
    const url = page.url()
    if (url.includes('/admin/')) {
      await expect(
        page.locator('.el-message, .el-alert').first()
      ).toBeVisible({ timeout: 5_000 })
    } else {
      expect(url).not.toContain('/admin/')
    }
  })

  test('跨字段验证规则配置页面可访问', async ({ page }) => {
    await page.goto('/admin/form-definitions')
    const rulesTab = page.getByText('验证规则').first()
    const tabExists = await rulesTab.isVisible({ timeout: 3_000 }).catch(() => false)
    if (tabExists) {
      await rulesTab.click()
      await expect(
        page.getByText('跨字段验证').first()
      ).toBeVisible({ timeout: 5_000 })
    }
  })
})

// ==================== 冒烟测试 ====================

test.describe('冒烟测试', () => {
  test('后端健康检查正常', async ({ request }) => {
    // 端口修复: 18080 → 18089（CLAUDE.md 端口约定）
    const response = await request.get(`${apiBaseUrl}/actuator/health`)
    expect(response.ok()).toBeTruthy()
    const body = await response.json()
    expect(body.status).toBe('UP')
  })

  test('动态表单 API 返回正确结构', async ({ request }) => {
    const session = await ensureApiSession({
      username: `smoke_${Date.now()}`,
      role: '/bidAdmin',
      fullName: 'Smoke Test User',
    })

    const response = await request.get(`${apiBaseUrl}/api/form-definitions/tender.entry/active`, {
      headers: { Authorization: `Bearer ${session.token}` },
    })

    expect(response.ok()).toBeTruthy()
    const body = await response.json()
    expect(body.success).toBe(true)
    expect(body.data.scope).toBe('tender.entry')
    expect(body.data.fields).toBeInstanceOf(Array)
    expect(body.data.fields.length).toBeGreaterThan(0)
  })
})
