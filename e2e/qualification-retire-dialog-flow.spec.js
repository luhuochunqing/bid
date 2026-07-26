import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

/**
 * §4.1.3.5 下架确认弹窗 E2E（重写版，匹配实际 UI）
 *
 * 实际 UI：
 *   资质表格行内"下架"按钮（data-testid="qual-row-retire"，仅 status !== 'retired' 时显示）
 *   点击后打开 RetireConfirmDialog（data-testid="qual-retire-dialog"）：
 *     - 证书信息展示（data-testid="qual-retire-meta" + name/no 子元素）
 *     - 下架原因 textarea（data-testid="qual-retire-reason", maxlength=200）
 *     - 提示文案"4-200 字符"（data-testid="qual-retire-hint"）
 *     - 确认 checkbox（data-testid="qual-retire-confirm"）
 *     - 取消按钮（data-testid="qual-retire-cancel"）
 *     - 确认下架按钮（data-testid="qual-retire-submit", type=danger, disabled until reason≥4 && checked）
 *
 * 后端接口：POST /api/knowledge/qualifications/{id}/retire
 */

async function loginAsRole(page, role) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_${role}_${suffix}`,
    role,
    fullName: `E2E ${role} 测试`
  })
  await injectSession(page, session)
  return session
}

async function createQualificationForRetire(token) {
  // 通过 API 创建一条资质，便于后续触发表格"下架"按钮
  const certNo = `RET-${Date.now()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`
  const res = await fetch('http://127.0.0.1:18089/api/knowledge/qualifications', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({
      name: `E2E 下架测试证书-${certNo}`,
      level: 'A',
      certificateNo: certNo,
      issueDate: '2024-01-15',
      expiryDate: '2027-12-31',
      issuer: '中国计量认证中心',
      agency: 'E2E 代理认证公司',
      agencyContact: '13800138000',
      certScope: 'E2E 下架流程测试'
    })
  })
  if (!res.ok) {
    throw new Error(`create qualification failed: ${res.status} ${await res.text()}`)
  }
  return certNo
}

async function openRetireDialog(page, certName) {
  await page.goto('/knowledge/qualification')
  await page.waitForSelector('.el-table__row, .el-empty', { timeout: 15000 })
  // 等列表加载
  await page.waitForResponse(
    r => /\/api\/knowledge\/qualifications/.test(r.url()) && r.request().method() === 'GET',
    { timeout: 5000 }
  ).catch(() => {})

  // 用证书名称定位行，点击行内的"下架"按钮
  const row = page.locator('[data-testid="qual-table"] .el-table__row').filter({ hasText: certName }).first()
  await row.waitFor({ state: 'visible', timeout: 10000 })
  const retireBtn = row.locator('[data-testid="qual-row-retire"]')
  await expect(retireBtn, `行内应可见"下架"按钮（证书：${certName}）`).toBeVisible({ timeout: 5000 })
  await retireBtn.click()

  const dialog = page.locator('[data-testid="qual-retire-dialog"]')
  await expect(dialog).toBeVisible({ timeout: 5000 })
  return dialog
}

test.describe('§4.1.3.5 下架确认弹窗', () => {
  test('正向下架流程：弹窗含证书信息+必填原因+勾选确认+调接口', async ({ page }) => {
    const session = await loginAsRole(page, '/bidAdmin')
    const certNo = await createQualificationForRetire(session.token)
    const certName = `E2E 下架测试证书-${certNo}`
    const dialog = await openRetireDialog(page, certName)

    // 标题
    await expect(dialog.locator('.el-dialog__title')).toHaveText('下架资质证书')

    // 证书信息展示
    const meta = dialog.locator('[data-testid="qual-retire-meta"]')
    await expect(meta).toBeVisible()
    const nameText = await meta.locator('[data-testid="qual-retire-meta-name"]').textContent()
    const noText = await meta.locator('[data-testid="qual-retire-meta-no"]').textContent()
    expect(nameText, '证书名称应展示真实值').toContain(certName)
    expect(noText, '证书号应展示真实值').toContain(certNo)
    expect(nameText, '不应是占位符').not.toContain('证书名称：—')

    // 必填原因 textarea + 提示
    const textarea = dialog.locator('[data-testid="qual-retire-reason"]')
    await expect(textarea).toBeVisible()
    await expect(dialog.locator('[data-testid="qual-retire-hint"]')).toContainText('4-200')

    // 未输入原因时，"确认下架"按钮应 disabled
    const confirmBtn = dialog.locator('[data-testid="qual-retire-submit"]')
    await expect(confirmBtn).toBeDisabled()

    // 输入原因（< 4 字符）后，按钮仍 disabled（长度不足）
    await textarea.fill('证书')
    await expect(confirmBtn, '原因不足 4 字符时按钮应仍 disabled').toBeDisabled()

    // 输入足够长度的原因后，未勾选 checkbox，按钮仍 disabled
    await textarea.fill('证书有效期已过，需要下架处理')
    await expect(confirmBtn, '未勾选 checkbox 时按钮应仍 disabled').toBeDisabled()

    // 勾选 checkbox 后按钮 enabled
    await dialog.locator('[data-testid="qual-retire-confirm"]').click()
    await expect(confirmBtn).toBeEnabled()

    // 确认按钮必须是 danger (红色) 类型
    const buttonClass = await confirmBtn.getAttribute('class')
    expect(buttonClass, '确认按钮应为 danger 类型').toContain('el-button--danger')

    // 监听 /retire 接口并点击提交
    const retireResp = page.waitForResponse(
      r => /\/api\/knowledge\/qualifications\/\d+\/retire$/.test(r.url()) && r.request().method() === 'POST',
      { timeout: 10000 }
    )
    await confirmBtn.click()
    const resp = await retireResp
    expect(resp.status(), 'retire 接口应返回 < 500').toBeLessThan(500)
  })

  test('边界：textarea maxlength=200', async ({ page }) => {
    const session = await loginAsRole(page, '/bidAdmin')
    const certNo = await createQualificationForRetire(session.token)
    const certName = `E2E 下架测试证书-${certNo}`
    const dialog = await openRetireDialog(page, certName)

    const textarea = dialog.locator('[data-testid="qual-retire-reason"]')
    const maxlength = await textarea.getAttribute('maxlength')
    expect(maxlength, 'maxlength 应为 200').toBe('200')
  })

  test('边界：取消按钮关闭弹窗且不调接口', async ({ page }) => {
    const session = await loginAsRole(page, '/bidAdmin')
    const certNo = await createQualificationForRetire(session.token)
    const certName = `E2E 下架测试证书-${certNo}`
    const dialog = await openRetireDialog(page, certName)

    // 监听 /retire 接口（不应被调用）
    const retireCallPromise = page.waitForResponse(
      r => /\/api\/knowledge\/qualifications\/\d+\/retire$/.test(r.url()) && r.request().method() === 'POST',
      { timeout: 3000 }
    ).then(() => true).catch(() => false)

    await dialog.locator('[data-testid="qual-retire-cancel"]').click()
    await expect(dialog).not.toBeVisible({ timeout: 5000 })
    expect(await retireCallPromise, '取消时不应调 /retire 接口').toBe(false)
  })

  test('权限：bid_specialist (CO-494) 现在可以看到下架按钮', async ({ page }) => {
    // CO-494: 投标专员资质模块权限增加，下架/恢复等管理操作已对投标专员放开
    // 先用 /bidAdmin 创建一条资质
    const adminSession = await loginAsRole(page, '/bidAdmin')
    const certNo = await createQualificationForRetire(adminSession.token)
    const certName = `E2E 下架测试证书-${certNo}`

    // 切到 bid_specialist 身份
    await loginAsRole(page, 'bid-Team')
    await page.goto('/knowledge/qualification')
    await page.waitForSelector('.el-table__row, .el-empty', { timeout: 15000 })
    await page.waitForResponse(
      r => /\/api\/knowledge\/qualifications/.test(r.url()) && r.request().method() === 'GET',
      { timeout: 5000 }
    ).catch(() => {})

    // 投标专员现在可以管理资质，应看到下架按钮
    const row = page.locator('[data-testid="qual-table"] .el-table__row').filter({ hasText: certName }).first()
    await row.waitFor({ state: 'visible', timeout: 10000 })
    const retireBtn = row.locator('[data-testid="qual-row-retire"]')
    await expect(retireBtn, '投标专员应看到下架按钮（CO-494）').toBeVisible({ timeout: 5000 })
  })
})
