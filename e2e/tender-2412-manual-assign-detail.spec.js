import { test, expect } from '@playwright/test'
import { injectSession, ensureApiSession, apiBaseUrl } from './auth-helpers.js'

/**
 * §2.4.1.2 标讯手动分配 — 详情页【分配】按钮（未匹配项目负责人的 PENDING_ASSIGNMENT 标讯）
 *
 * 蓝图要求（2.4.1.2）：
 * - 标讯创建后，未自动匹配到项目负责人的标讯（状态 PENDING_ASSIGNMENT），详情页必须展示【分配】按钮。
 * - 仅 bid_admin、bid_lead、admin（超级管理员）等有管理权限的角色可见并可操作。
 * - sales / bid_specialist / staff 等角色不可见。
 * - 点击后可打开分配弹窗，选择项目负责人后提交，状态推进为 TRACKING。
 *
 * 测试即文档：本文件是对蓝图 2.4.1.2 手动分配入口的机器可执行描述。
 *
 * @ui-cover:bidding
 *
 * 覆盖范围：
 * - 正向流程：创建未匹配标讯 → 详情页分配按钮可见 → 点击分配 → 状态流转
 * - 权限验证：admin / bid_admin / bid_lead 可见；sales 等不可见
 * - 边界：PENDING_ASSIGNMENT 专属按钮（TRACKING 时应为转派而非分配）
 *
 * 依赖：
 * - 前端修复：actionMatrix.js resolveRoleGroup 支持 'admin' 角色（2026-06 修复）
 * - 后端：TenderAutoAssignmentService + createTender 保持 PENDING 当无 CRM 映射
 * - 真实 API 路径（VITE_API_MODE=api）
 */

test.describe.configure({ mode: 'serial' })

const E2E_PURCHASER_NO_MATCH = 'E2E-NO-CRM-MAPPING-PURCHASER-§2.4.1.2'

function toLocalDateTimeString(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000)
    .toISOString()
    .slice(0, 19)
}

async function apiRequest(path, session, options = {}) {
  const url = `${apiBaseUrl}${path}`
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...(session?.token ? { Authorization: `Bearer ${session.token}` } : {}) },
    ...options,
  })
  if (!res.ok) throw new Error(`apiRequest failed: ${res.status} ${res.statusText} for ${options.method || 'GET'} ${path}`)
  return res.json()
}

/**
 * 使用稳定角色会话池创建“未匹配”的 PENDING_ASSIGNMENT 标讯。
 * 通过传入不匹配任何 CrmProjectMapping 的 purchaserName 保证状态为 PENDING。
 */
async function seedUnmatchedPendingTender(session) {
  const payload = await apiRequest('/api/tenders', session, {
    method: 'POST',
    body: JSON.stringify({
      title: `E2E-2.4.1.2-UNMATCHED-${Date.now()}`,
      source: 'Playwright §2.4.1.2',
      budget: 123456,
      purchaserName: `${E2E_PURCHASER_NO_MATCH}-${Date.now()}`,
      deadline: toLocalDateTimeString(new Date(Date.now() + 14 * 24 * 60 * 60 * 1000)),
      aiScore: 60,
      riskLevel: 'LOW'
    })
  })
  return payload?.data?.id
}

/**
 * 稳定会话登录（推荐用于权限矩阵测试，避免登录风暴）。
 */
async function loginAsRole(page, roleProfile) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({ username: `e2e_${roleProfile}_${suffix}`, role: roleProfile, fullName: `E2E ${roleProfile}` })
  await injectSession(page, session)
  return session
}

// ---------------------------------------------------------------------------
// §2.4.1.2 — 标讯手动分配：详情页分配按钮（未匹配项目负责人场景）
// ---------------------------------------------------------------------------
test.describe('§2.4.1.2 标讯手动分配 — 详情页【分配】按钮（PENDING 未匹配场景）', () => {
  test('admin 角色在未匹配 PENDING_ASSIGNMENT 标讯详情页可见【分配】按钮', async ({ page }) => {
    const session = await loginAsRole(page, 'admin')
    const tenderId = await seedUnmatchedPendingTender(session)
    expect(tenderId).toBeTruthy()

    await page.goto(`/bidding/detail/${tenderId}`)
    await page.waitForSelector('.el-descriptions', { timeout: 15000 })

    // 核心断言：修复后 admin 必须看到分配按钮
    await expect(page.getByRole('button', { name: '分配' })).toBeVisible()
    await expect(page.getByRole('button', { name: '转派' })).not.toBeVisible()
  })

  test('bid_admin 角色在未匹配 PENDING_ASSIGNMENT 标讯详情页可见【分配】按钮', async ({ page }) => {
    const session = await loginAsRole(page, 'bid_admin')
    const tenderId = await seedUnmatchedPendingTender(session)
    expect(tenderId).toBeTruthy()

    await page.goto(`/bidding/detail/${tenderId}`)
    await page.waitForSelector('.el-descriptions', { timeout: 15000 })

    await expect(page.getByRole('button', { name: '分配' })).toBeVisible()
  })

  test('bid_lead 角色在未匹配 PENDING_ASSIGNMENT 标讯详情页可见【分配】按钮', async ({ page }) => {
    const session = await loginAsRole(page, 'bid_lead')
    const tenderId = await seedUnmatchedPendingTender(session)
    expect(tenderId).toBeTruthy()

    await page.goto(`/bidding/detail/${tenderId}`)
    await page.waitForSelector('.el-descriptions', { timeout: 15000 })

    await expect(page.getByRole('button', { name: '分配' })).toBeVisible()
  })

  test('sales 角色在未匹配 PENDING_ASSIGNMENT 标讯详情页不可见【分配】按钮', async ({ page }) => {
    const session = await loginAsRole(page, 'sales')
    const tenderId = await seedUnmatchedPendingTender(session)
    expect(tenderId).toBeTruthy()

    await page.goto(`/bidding/detail/${tenderId}`)
    await page.waitForSelector('.el-descriptions', { timeout: 15000 })

    await expect(page.getByRole('button', { name: '分配' })).not.toBeVisible()
  })

  test('点击【分配】按钮可打开弹窗并完成分配（状态推进 TRACKING）', async ({ page }) => {
    const session = await loginAsRole(page, 'bid_admin')
    const tenderId = await seedUnmatchedPendingTender(session)
    expect(tenderId).toBeTruthy()

    await page.goto(`/bidding/detail/${tenderId}`)
    await page.waitForSelector('.el-descriptions', { timeout: 15000 })

    // 点击分配
    const assignBtn = page.getByRole('button', { name: '分配' })
    await expect(assignBtn).toBeVisible()
    await assignBtn.click()

    // 等待分配弹窗（AssignDialog）
    await page.waitForSelector('.el-dialog:has-text("分配")', { timeout: 10000 })

    // 选择第一个可用候选人（实际环境中至少有一个可分配人员）
    const candidateSelect = page.locator('.el-select').first()
    await candidateSelect.click()
    // 选择第一个选项
    await page.locator('.el-select-dropdown__item').first().click()

    // 提交
    const submitBtn = page.getByRole('button', { name: '确定' }).or(page.getByRole('button', { name: '提交' }))
    await submitBtn.click()

    // 成功后详情页刷新，状态应变为“跟踪中”，分配按钮消失，转派按钮出现
  await expect(page.getByRole('button', { name: '分配' })).not.toBeVisible({ timeout: 10000 }).catch(() => {})
    await expect(page.getByRole('button', { name: '分配' })).not.toBeVisible()
    await expect(page.getByRole('button', { name: '转派' })).toBeVisible()

    // 可选：验证状态标签
    const statusTag = page.locator('.el-tag:has-text("跟踪中")').or(page.locator('.el-tag:has-text("TRACKING")'))
    await expect(statusTag).toBeVisible()
  })
})
