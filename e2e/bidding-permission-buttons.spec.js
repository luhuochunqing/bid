import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

function toLocalDateTimeString(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000)
    .toISOString()
    .slice(0, 19)
}

async function apiRequest(path, session, options = {}) {
  const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18080'
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.token}`,
      ...(options.headers || {})
    }
  })
  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${path} -> ${response.status}`)
  }
  return response.json()
}

async function seedTenderForDetail(session, status) {
  const payload = await apiRequest('/api/tenders', session, {
    method: 'POST',
    body: JSON.stringify({
      title: `DETAIL-${status}-${Date.now()}`,
      source: 'Playwright',
      budget: 500000,
      deadline: toLocalDateTimeString(new Date(Date.now() + 14 * 24 * 60 * 60 * 1000)),
      status,
      aiScore: 75,
      riskLevel: 'LOW'
    })
  })
  return payload?.data?.id
}

async function loginAsRole(page, roleProfile) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `btn_perm_${roleProfile}_${suffix}`,
    role: roleProfile,
    fullName: `Btn Perm ${roleProfile}`
  })
  await injectSession(page, session)
  return session
}

test.beforeEach(({ page }) => {
  page.on('console', msg => {
    if (msg.type() === 'error') {
      console.error('FRONTEND CONSOLE ERROR:', msg.text())
    }
  })
  page.on('pageerror', err => {
    console.error('FRONTEND RUNTIME EXCEPTION:', err.stack || err.message)
  })
})

// ---------------------------------------------------------------------------  // @ui-cover:bidding
// §4.2.1 — Batch import button on list page
// canBulkImport = canCreateTender && userRole !== 'sales'
// ---------------------------------------------------------------------------
test.describe('§4.2.1 — bidding list bulk import button visibility', () => {
  test('bid_admin sees the batch import button', async ({ page }) => {
    await loginAsRole(page, 'bid_admin')
    await page.goto('/bidding')
    await page.waitForSelector('.el-table', { timeout: 10000 })
    await expect(page.getByRole('button', { name: '批量导入', exact: true })).toBeVisible()
  })

  test('sales does NOT see the batch import button', async ({ page }) => {
    await loginAsRole(page, 'sales')
    await page.goto('/bidding')
    await page.waitForSelector('.el-table', { timeout: 10000 })
    await expect(page.getByRole('button', { name: '批量导入', exact: true })).not.toBeVisible()
  })
})

// ---------------------------------------------------------------------------
// §4.2.4 — Assign/transfer buttons on detail page
// 分配: visible when status === PENDING_ASSIGNMENT && canManageTenders
// 转派: visible when status ∈ {TRACKING, EVALUATED} && canManageTenders
// ---------------------------------------------------------------------------
test.describe('§4.2.4 — tender detail page assign/transfer button visibility', () => {
  test('bid_admin sees 分配 button on PENDING_ASSIGNMENT tender detail', async ({ page }) => {
    const session = await loginAsRole(page, 'bid_admin')
    const tenderId = await seedTenderForDetail(session, 'PENDING_ASSIGNMENT')
    expect(tenderId).toBeTruthy()

    await page.goto(`/bidding/${tenderId}`)
    await page.waitForSelector('.el-descriptions', { timeout: 10000 })

    await expect(page.getByRole('button', { name: '分配' })).toBeVisible()
    await expect(page.getByRole('button', { name: '转派' })).not.toBeVisible()
  })

  test('bid_admin sees 转派 button on TRACKING tender detail', async ({ page }) => {
    const session = await loginAsRole(page, 'bid_admin')
    const tenderId = await seedTenderForDetail(session, 'TRACKING')
    expect(tenderId).toBeTruthy()

    await page.goto(`/bidding/${tenderId}`)
    await page.waitForSelector('.el-descriptions', { timeout: 10000 })

    await expect(page.getByRole('button', { name: '分配' })).not.toBeVisible()
    await expect(page.getByRole('button', { name: '转派' })).toBeVisible()
  })

  test('sales does NOT see 分配 or 转派 buttons on detail page', async ({ page }) => {
    const session = await loginAsRole(page, 'sales')
    const tenderId = await seedTenderForDetail(session, 'PENDING_ASSIGNMENT')
    expect(tenderId).toBeTruthy()

    await page.goto(`/bidding/${tenderId}`)
    await page.waitForSelector('.el-descriptions', { timeout: 10000 })

    await expect(page.getByRole('button', { name: '分配' })).not.toBeVisible()
    await expect(page.getByRole('button', { name: '转派' })).not.toBeVisible()
  })

  test('bid_admin sees 删除 button on detail page, but sales and bid_lead do not', async ({ page }) => {
    const sessionAdmin = await loginAsRole(page, 'bid_admin')
    const tenderId = await seedTenderForDetail(sessionAdmin, 'PENDING_ASSIGNMENT')
    expect(tenderId).toBeTruthy()

    // 1. bid_admin sees it
    await page.goto(`/bidding/${tenderId}`)
    await page.waitForSelector('.el-descriptions', { timeout: 10000 })
    await expect(page.getByRole('button', { name: '删除' })).toBeVisible()

    // 2. sales does not see it
    await loginAsRole(page, 'sales')
    await page.goto(`/bidding/${tenderId}`)
    await page.waitForSelector('.el-descriptions', { timeout: 10000 })
    await expect(page.getByRole('button', { name: '删除' })).not.toBeVisible()

    // 3. bid_lead does not see it
    await loginAsRole(page, 'bid_lead')
    await page.goto(`/bidding/${tenderId}`)
    await page.waitForSelector('.el-descriptions', { timeout: 10000 })
    await expect(page.getByRole('button', { name: '删除' })).not.toBeVisible()
  })
})


// §V1026 — Evaluation form action buttons after field redesign
// TRACKING status: sales sees "下一步" (nextStep) instead of editEvaluation/save/cancel
// ---------------------------------------------------------------------------
test.describe('§V1026 — TRACKING sales evaluation flow buttons', () => {
  test('sales sees 下一步 button on TRACKING tender detail', async ({ page }) => {
    const session = await loginAsRole(page, 'sales')
    const tenderId = await seedTenderForDetail(session, 'TRACKING')
    expect(tenderId).toBeTruthy()

    // Assign tender to self to become project manager
    await apiRequest(`/api/tenders/${tenderId}`, session, {
      method: 'PUT',
      body: JSON.stringify({ projectManagerId: session.user.id, status: 'TRACKING' })
    })

    await page.goto(`/bidding/${tenderId}`)
    await page.waitForSelector('.el-descriptions', { timeout: 10000 })

    // V1026: sales in TRACKING sees 下一步 (replaces old editEvaluation/save/cancel)
    await expect(page.getByRole('button', { name: '下一步' })).toBeVisible()
  })
})
