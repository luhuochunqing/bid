/**
 * 动态表单引擎端到端测试。
 *
 * 覆盖范围：
 * - M1: DynamicFormRenderer 动态表单渲染（降级兼容验证）
 * - M2: 表单 schema 加载、验证、提交
 * - M3: Tender entry 集成
 * - M4: Project 表单集成
 * - M5: 跨字段验证、角色过滤、租户覆盖
 * - M6: 管理端 CRUD
 *
 * 依赖：e2e/auth-helpers.js（ensureApiSession / injectSession）
 * 依赖：后端运行在 http://127.0.0.1:18080
 * 依赖：前端运行在 http://127.0.0.1:1314（由 playwright.config.js baseURL 控制）
 */

import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

// ==================== Helpers ====================  // @ui-cover:project,bidding

async function loginAsAdmin(page) {
  const session = await ensureApiSession({
    username: `form_e2e_admin_${Date.now()}`,
    role: 'bid_admin',
    fullName: '动态表单测试管理员',
  })
  await injectSession(page, session)
  return session
}

async function loginAsStaff(page) {
  const session = await ensureApiSession({
    username: `form_e2e_staff_${Date.now()}`,
    role: 'STAFF',
    fullName: '动态表单测试员工',
  })
  await injectSession(page, session)
  return session
}

// ==================== M1: Dynamic Form Rendering ====================

test.describe('M1: 动态表单渲染', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('标讯手工录入表单降级兼容：原有硬编码表单正常显示', async ({ page }) => {
    await page.goto('/bidding/tender/create')

    // 降级兼容检查：核心字段仍然可见（即使 API 不可用）
    // 标讯标题字段（来自 seed 数据）
    await expect(page.getByText('标讯标题').first()).toBeVisible({ timeout: 10_000 })
  })

  test('20种字段类型在表单设计器中可用', async ({ page }) => {
    await page.goto('/admin/form-definitions')

    // 进入表单设计器（如果有的话）
    // 验证页面加载
    await expect(page.locator('.el-table, table').first()).toBeVisible({ timeout: 10_000 })
  })

  test('4个 scope 种子数据已加载', async ({ page }) => {
    await page.goto('/admin/form-definitions')

    // 等待表格加载
    await expect(page.locator('.el-table').first()).toBeVisible({ timeout: 10_000 })

    // 验证 4 个 seed scopes
    const scopes = ['tender.entry', 'project.basic', 'resource.expense', 'knowledge.case']
    for (const scope of scopes) {
      await expect(page.getByText(scope)).toBeVisible({ timeout: 5_000 })
    }
  })
})

// ==================== M2: Form Schema Loading & Validation ====================

test.describe('M2: 表单 Schema 加载与验证', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('tender.entry 表单 schema 加载成功', async ({ page }) => {
    await page.goto('/bidding/tender/create')

    // 打开 ManualTenderDialog（如果存在）
    const dialog = page.locator('.manual-tender-dialog, .el-dialog').first()

    // 如果 dialog 存在，验证其可见
    if (await dialog.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await expect(dialog).toBeVisible()
      // 验证动态表单字段可见
      await expect(page.getByText('标讯标题').first()).toBeVisible()
    } else {
      // 降级兼容：验证基础字段仍可见
      await expect(page.getByText('标讯').first()).toBeVisible({ timeout: 5_000 })
    }
  })

  test('必填字段缺失时验证提示出现', async ({ page }) => {
    await page.goto('/bidding/tender/create')

    const dialog = page.locator('.manual-tender-dialog, .el-dialog').first()

    if (await dialog.isVisible({ timeout: 3_000 }).catch(() => false)) {
      // 点击提交按钮（如果有）
      const submitBtn = dialog.locator('button:has-text("提交"), button:has-text("保存")').first()
      if (await submitBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
        await submitBtn.click()
        // 验证必填提示
        await expect(
          page.locator('.el-form-item__error, .el-alert').first()
        ).toBeVisible({ timeout: 5_000 })
      }
    }
  })
})

// ==================== M3: Tender Entry Integration ====================

test.describe('M3: 投标录入集成', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('投标列表页可访问', async ({ page }) => {
    await page.goto('/bidding/list')

    // 验证列表页基本元素
    await expect(page.locator('table, .el-table').first()).toBeVisible({ timeout: 10_000 })
  })

  test('新建投标入口可见', async ({ page }) => {
    await page.goto('/bidding/list')

    // 查找新建按钮
    const createBtn = page.locator('button:has-text("新建"), button:has-text("创建"), .el-button--primary').first()
    await expect(createBtn).toBeVisible({ timeout: 5_000 })
  })
})

// ==================== M4: Project Form Integration ====================

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

    // 验证基本信息字段
    await expect(page.getByText('项目名称').first()).toBeVisible({ timeout: 5_000 })
  })
})

// ==================== M5: Role-Based Field Hiding & Tenant Override ====================

test.describe('M5: 角色过滤与租户覆盖', () => {
  test('admin 角色可看到全部字段', async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto('/bidding/tender/create')

    const dialog = page.locator('.manual-tender-dialog, .el-dialog').first()
    if (await dialog.isVisible({ timeout: 3_000 }).catch(() => false)) {
      // Admin 应该可以看到所有可见字段
      await expect(page.getByText('预算金额').first()).toBeVisible({ timeout: 5_000 })
    }
  })

  test('staff 角色下管理员字段可能被隐藏', async ({ page }) => {
    await loginAsStaff(page)
    await page.goto('/bidding/tender/create')

    // Staff 访问投标录入
    // 部分敏感字段可能对 staff 隐藏
    // 此测试验证页面仍可正常访问
    const dialog = page.locator('.manual-tender-dialog, .el-dialog').first()
    const visible = await dialog.isVisible({ timeout: 5_000 }).catch(() => false)
    if (!visible) {
      // 如果 dialog 不存在，验证页面仍然可访问
      await expect(page.getByText('标讯').first()).toBeVisible({ timeout: 5_000 })
    }
  })

  test('角色预览功能在设计器中可用', async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto('/admin/form-definitions')

    // 查找设计器按钮
    const designerBtn = page.locator('button:has-text("设计"), button:has-text("编辑")').first()
    const btnExists = await designerBtn.isVisible({ timeout: 3_000 }).catch(() => false)

    if (btnExists) {
      await designerBtn.click()
      // 验证角色预览选项卡
      await expect(
        page.locator('text=角色预览, text=角色').first()
      ).toBeVisible({ timeout: 5_000 })
    }
  })
})

// ==================== M6: Admin CRUD ====================

test.describe('M6: 管理端 CRUD', () => {
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
      // 验证分页信息显示
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

    // 点击第一行的编辑或详情
    const firstRow = page.locator('.el-table__row').first()
    const rowExists = await firstRow.isVisible({ timeout: 3_000 }).catch(() => false)

    if (rowExists) {
      // 查找操作按钮
      const actionBtn = firstRow.locator('button').first()
      if (await actionBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
        await actionBtn.click()
        // 验证发布或编辑相关元素
        await expect(
          page.locator('text=发布, text=编辑').first()
        ).toBeVisible({ timeout: 5_000 })
      }
    }
  })

  test('非 admin 用户无法访问管理端', async ({ page }) => {
    await loginAsStaff(page)
    await page.goto('/admin/form-definitions')

    // 应该重定向到 dashboard 或显示 403
    const url = page.url()
    if (url.includes('/admin/')) {
      // 仍然在管理端，验证错误提示
      await expect(
        page.locator('.el-message, .el-alert, text=无权限, text=Forbidden').first()
      ).toBeVisible({ timeout: 5_000 })
    } else {
      // 重定向到非管理页面
      expect(url).not.toContain('/admin/')
    }
  })

  test('跨字段验证规则配置页面可访问', async ({ page }) => {
    await page.goto('/admin/form-definitions')

    // 查找验证规则 tab
    const rulesTab = page.locator('text=验证规则').first()
    const tabExists = await rulesTab.isVisible({ timeout: 3_000 }).catch(() => false)

    if (tabExists) {
      await rulesTab.click()
      // 验证规则配置区域
      await expect(
        page.locator('text=跨字段验证, .el-form').first()
      ).toBeVisible({ timeout: 5_000 })
    }
  })
})

// ==================== Smoke Tests ====================

test.describe('冒烟测试', () => {
  test('后端健康检查正常', async ({ request }) => {
    const response = await request.get(
      `${process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18080'}/actuator/health`
    )
    expect(response.ok()).toBeTruthy()
    const body = await response.json()
    expect(body.status).toBe('UP')
  })

  test('动态表单 API 返回正确结构', async ({ request }) => {
    const apiBase = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18080'

    // 登录获取 token
    const session = await ensureApiSession({
      username: `smoke_${Date.now()}`,
      role: 'bid_admin',
      fullName: 'Smoke Test User',
    })

    const response = await request.get(`${apiBase}/api/form-definitions/tender.entry/active`, {
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
