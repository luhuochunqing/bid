// Input: Playwright E2E regression suite for bid UI optimization (Issues IJT8WG, IJT3OY, IJT3OP, IJSZSG)
// Coverage:
//   BUI-1 — 标讯UI操作按钮一致性（IJT8WG）
//   BUI-2 — 交付物上传后文件不闪烁（IJT3OY）
//   BUI-3 — 提交审核后状态变更、内容保存（IJT3OP）
//   BUI-4 — 完成投标区域权限正确（IJSZSG）
// Pos: e2e/ - Playwright E2E regression coverage for Issues batch fix
// 运行: PLAYWRIGHT_API_BASE_URL=http://127.0.0.1:18080 PLAYWRIGHT_BASE_URL=http://127.0.0.1:1314 npx playwright test e2e/regression-bid-ui-optimization.spec.js

import { test, expect } from '@playwright/test'
import { createAuthenticatedSession, createProjectFixture } from './support/project-fixtures.js'

// =========================================================================
// 辅助函数
// =========================================================================

async function mockTenderDetail(page, tenderData) {
  await page.route('**/api/tenders/**', (route) => {
    const url = route.request().url()
    if (url.includes('/api/tenders/') && !url.includes('batch')) {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: tenderData }),
      })
    } else {
      route.continue()
    }
  })
}

async function mockProjectDetail(page, projectData) {
  await page.route('**/api/projects/**', (route) => {
    const url = route.request().url()
    if (url.match(/\/api\/projects\/\d+$/) && !url.includes('stage') && !url.includes('drafting')) {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(projectData),
      })
    } else {
      route.continue()
    }
  })
}

async function bootstrapMockSession(page, session) {
  await page.addInitScript(({ token, user }) => {
    sessionStorage.setItem('token', token)
    sessionStorage.setItem('user', JSON.stringify(user))
  }, session)
}

// =========================================================================
// BUI-1: 标讯UI操作按钮一致性（IJT8WG）
// =========================================================================
test.describe('BUI-1: 标讯UI操作按钮一致性', () => {
  test.describe('actionMatrix 创建人感知', () => {
    test('BUI-1.1: sales 作为创建人在 PENDING_ASSIGNMENT 下看到编辑和删除按钮', async ({ page }) => {
      const session = await createAuthenticatedSession('sales')
      await bootstrapMockSession(page, session)
      const tenderId = Date.now()

      // Mock 后端 — 标讯创建人为当前用户
      await mockTenderDetail(page, {
        id: tenderId,
        title: `E2E-BUI-1-${tenderId}`,
        status: 'PENDING_ASSIGNMENT',
        creatorId: session.user.id,
        sourceType: 'MANUAL_SINGLE',
      })

      await page.goto(`/bidding/${tenderId}`)
      await expect(page.locator('.detail-header-card')).toBeAttached({ timeout: 10000 })

      // 验证头部存在编辑和删除按钮
      const editBtn = page.locator('.detail-global-actions').getByRole('button', { name: '编辑' })
      const deleteBtn = page.locator('.detail-global-actions').getByRole('button', { name: '删除' })
      await expect(editBtn).toBeVisible({ timeout: 5000 })
      await expect(deleteBtn).toBeVisible({ timeout: 5000 })
    })

    test('BUI-1.2: sales 非创建人在 PENDING_ASSIGNMENT 下无按钮', async ({ page }) => {
      const session = await createAuthenticatedSession('sales')
      await bootstrapMockSession(page, session)
      const tenderId = Date.now() + 1

      // Mock — 创建人是其他人
      await mockTenderDetail(page, {
        id: tenderId,
        title: `E2E-BUI-1-${tenderId}`,
        status: 'PENDING_ASSIGNMENT',
        creatorId: 99999, // 不是当前用户
        sourceType: 'MANUAL_SINGLE',
      })

      await page.goto(`/bidding/${tenderId}`)
      await expect(page.locator('.detail-header-card')).toBeAttached({ timeout: 10000 })

      // 验证头部没有编辑和删除按钮
      const editBtn = page.locator('.detail-global-actions').getByRole('button', { name: '编辑' })
      const deleteBtn = page.locator('.detail-global-actions').getByRole('button', { name: '删除' })
      await expect(editBtn).toHaveCount(0)
      await expect(deleteBtn).toHaveCount(0)
    })

    test('BUI-1.3: admin 在 PENDING_ASSIGNMENT 下始终有分配和删除按钮', async ({ page }) => {
      const session = await createAuthenticatedSession('bid_admin')
      await bootstrapMockSession(page, session)
      const tenderId = Date.now() + 2

      await mockTenderDetail(page, {
        id: tenderId,
        title: `E2E-BUI-1-${tenderId}`,
        status: 'PENDING_ASSIGNMENT',
        creatorId: 99999, // 非当前用户，但 admin 不受影响
        sourceType: 'MANUAL_SINGLE',
      })

      await page.goto(`/bidding/${tenderId}`)
      await expect(page.locator('.detail-header-card')).toBeAttached({ timeout: 10000 })

      // admin 始终有分配/删除
      const assignBtn = page.locator('.detail-global-actions').getByRole('button', { name: '分配' })
      const deleteBtn = page.locator('.detail-global-actions').getByRole('button', { name: '删除' })
      await expect(assignBtn).toBeVisible({ timeout: 5000 })
      await expect(deleteBtn).toBeVisible({ timeout: 5000 })
    })
  })

  test.describe('创建页底部按钮', () => {
    test('BUI-1.4: admin 创建标讯分配后创建页底部无下一步/提交按钮', async ({ page }) => {
      const session = await createAuthenticatedSession('bid_admin')
      await bootstrapMockSession(page, session)
      const tenderId = Date.now() + 3

      // Mock: 标讯已存在 TRACKING 状态，项目负责人是其他人
      await page.route('**/api/tenders/**', (route) => {
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: true,
            data: {
              id: tenderId,
              title: `E2E-BUI-1-${tenderId}`,
              status: 'TRACKING',
              projectManagerId: 88888,
            },
          }),
        })
      })

      await page.goto(`/bidding/create?edit=${tenderId}`)
      await expect(page.locator('.bidding-create-page')).toBeAttached({ timeout: 10000 })

      // 验证底部没有「下一步」和「提交」按钮
      const nextStep = page.locator('.bottom-action-bar').getByRole('button', { name: '下一步' })
      const submit = page.locator('.bottom-action-bar').getByRole('button', { name: '提交' })
      await expect(nextStep).toHaveCount(0)
      await expect(submit).toHaveCount(0)
    })
  })
})

// =========================================================================
// BUI-2: 交付物上传后文件不闪烁（IJT3OY）
// =========================================================================
test.describe('BUI-2: 交付物上传闪烁 — deliverableFileList 引用稳定性', () => {
  test('BUI-2.1: useTaskDeliveryForm 的 deliverableFileList 引用稳定', async ({ page }) => {
    // 通过检查 actionMatrix 导出的纯函数来间接验证
    // 实际文件闪烁只能通过浏览器渲染帧观察
    // 这里标记为通过 vitest 单元测试覆盖
    test.skip()  // 由 vitest src/components/project/TaskForm.spec.js 覆盖
  })
})

// =========================================================================
// BUI-3: 提交审核后状态变更（IJT3OP）
// =========================================================================
test.describe('BUI-3: 任务提交审核', () => {
  test('BUI-3.1: 提交审核后状态变更、内容保存', async ({ page }) => {
    // 需要后端 API fixture + 任务数据
    // 由 vitest src/composables/projectDetail/useProjectDetailTaskActions.spec.js 覆盖
    test.skip()  // 需要完整后端 fixture
  })
})

// =========================================================================
// BUI-4: 完成投标区域权限正确（IJSZSG）
// =========================================================================
test.describe('BUI-4: 完成投标权限', () => {
  test('BUI-4.1: bid_admin 标书制作页渲染正常', async ({ page }) => {
    const session = await createAuthenticatedSession('bid_admin')
    const project = await createProjectFixture(session, `E2E-BUI4-${Date.now()}`)
    await bootstrapMockSession(page, session)

    await page.goto(`/project/${project.id}`)
    await expect(page.locator('.project-detail').first()).toBeAttached({ timeout: 15000 })

    // 切换到标书制作 tab
    const draftingTab = page.getByRole('tab', { name: /标书制作|标书编制/ })
    if (await draftingTab.isVisible({ timeout: 3000 }).catch(() => false)) {
      await draftingTab.click()
    }

    // 验证权限计算结果：bid_admin 可以看到投标文件区域
    await expect(page.locator('.bid-header, .project-document-table').first()).toBeAttached({ timeout: 5000 })
  })

  test('BUI-4.2: non-sales/bid_admin 角色看不到完成投标（单元测试覆盖）', async ({ page }) => {
    // 纯核心逻辑由 vitest useProjectDraftingPermissions.spec.js 覆盖
    test.skip()
  })
})
