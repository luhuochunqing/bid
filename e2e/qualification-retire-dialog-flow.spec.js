import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

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

test.describe('§4.1.3.5 下架确认弹窗', () => {
  test('正向下架流程：弹窗含证书信息+必填原因+勾选确认+调接口', async ({ page }) => {
    await loginAsRole(page, '/bidAdmin')
    await page.goto('/knowledge/qualification')
    await page.waitForSelector('.el-table__row, .el-empty', { timeout: 15000 })

    const retireBtn = page.locator('.el-table__row button:has-text("下架")').first()
    test.skip(await retireBtn.count() === 0, '当前无在库资质证书可供下架测试，请先 seed 数据')
    await retireBtn.click()

    const dialog = page.locator('[data-testid="qual-retire-dialog"]')
    await expect(dialog).toBeVisible()
    await expect(dialog.locator('.el-dialog__title')).toHaveText('下架资质证书')

    // 蓝图要求：显示证书名称 + 证书号
    const meta = dialog.locator('[data-testid="qual-retire-meta"]')
    await expect(meta).toBeVisible()
    const nameText = await meta.locator('[data-testid="qual-retire-meta-name"]').textContent()
    const noText = await meta.locator('[data-testid="qual-retire-meta-no"]').textContent()
    expect(nameText).toContain('证书名称：')
    expect(noText).toContain('证书号：')
    expect(nameText).not.toContain('证书名称：—') // 必须有真实值

    // 蓝图要求：必填下架原因，≤200 字符
    const textarea = dialog.locator('[data-testid="qual-retire-reason"]')
    await expect(textarea).toBeVisible()
    await expect(dialog.locator('[data-testid="qual-retire-hint"]')).toContainText('1-200')

    // 蓝图要求：未输入原因时，"确认下架"按钮应 disabled
    const confirmBtn = dialog.locator('[data-testid="qual-retire-submit"]')
    await expect(confirmBtn).toBeDisabled()

    // 输入原因后，未勾选 checkbox，按钮仍 disabled
    await textarea.fill('证书有效期已过')
    await expect(confirmBtn).toBeDisabled()

    // 蓝图要求：勾选 checkbox 后按钮 enabled
    await dialog.locator('[data-testid="qual-retire-confirm"]').click()
    await expect(confirmBtn).toBeEnabled()

    // 确认按钮必须是 danger (红色) 类型
    const buttonType = await confirmBtn.getAttribute('class')
    expect(buttonType).toContain('el-button--danger')

    // 监听 /retire 接口并点击提交
    const retireResp = page.waitForResponse(r => /\/api\/knowledge\/qualifications\/\d+\/retire$/.test(r.url()) && r.request().method() === 'POST', { timeout: 10000 })
    await confirmBtn.click()
    const resp = await retireResp
    expect(resp.status()).toBeLessThan(500)
  })

  test('边界：textarea maxlength=200', async ({ page }) => {
    await loginAsRole(page, '/bidAdmin')
    await page.goto('/knowledge/qualification')
    await page.waitForSelector('.el-table__row, .el-empty', { timeout: 15000 })
    const retireBtn = page.locator('.el-table__row button:has-text("下架")').first()
    test.skip(await retireBtn.count() === 0, '无在库证书')

    await retireBtn.click()
    const dialog = page.locator('[data-testid="qual-retire-dialog"]')
    const textarea = dialog.locator('[data-testid="qual-retire-reason"]')
    const maxlength = await textarea.getAttribute('maxlength')
    expect(maxlength).toBe('200')
  })

  test('边界：取消按钮关闭弹窗且不调接口', async ({ page }) => {
    await loginAsRole(page, '/bidAdmin')
    await page.goto('/knowledge/qualification')
    await page.waitForSelector('.el-table__row, .el-empty', { timeout: 15000 })
    const retireBtn = page.locator('.el-table__row button:has-text("下架")').first()
    test.skip(await retireBtn.count() === 0, '无在库证书')

    await retireBtn.click()
    const dialog = page.locator('[data-testid="qual-retire-dialog"]')
    await expect(dialog).toBeVisible()

    const retireCallPromise = page.waitForResponse(
      r => /\/api\/knowledge\/qualifications\/\d+\/retire$/.test(r.url()) && r.request().method() === 'POST',
      { timeout: 1000 }
    ).then(() => true).catch(() => false)

    await dialog.locator('[data-testid="qual-retire-cancel"]').click()
    await expect(dialog).not.toBeVisible()
    expect(await retireCallPromise).toBe(false)
  })

  test('权限：bid_specialist 看不到下架按钮', async ({ page }) => {
    await loginAsRole(page, 'bid-Team')
    await page.goto('/knowledge/qualification')
    await page.waitForSelector('.el-table__row, .el-empty', { timeout: 15000 })
    const retireBtn = page.locator('.el-table__row button:has-text("下架")')
    await expect(retireBtn).toHaveCount(0)
  })
})
