import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

/**
 * 4.1.3.6 资质详情抽屉 E2E
 *
 * 蓝图 4.1.3.6 要求：
 * 1. 入口：点击列表行（除操作按钮外）打开抽屉
 * 2. 类型：el-drawer size="800px"，从右滑入
 * 3. 顶部：证书名称 + 状态Tag + [编辑] [下架/恢复] [×]
 * 4. Tab 1 基本信息：证书名称/等级/认证机构/代理机构/证书号/代理联系方式/发证日期/证书有效期/认证范围/证书审核提醒
 * 5. 附件区：4 按钮（预览/下载/替换/删除）+ 无附件时"上传附件"按钮
 * 6. Tab 2 操作日志占位（4.1.3.7 实现）
 *
 * 演示数据 0 条资质 → 测前 API 创建一条
 */

async function loginAsBidAdmin(page) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_qd_${suffix}`,
    role: '/bidAdmin',
    fullName: 'E2E 资质详情'
  })
  await injectSession(page, session)
  return session
}

test.describe('§4.1.3.6 资质详情抽屉', () => {
  test('正向流程：API 创建 + 行点击打开抽屉 + 基本信息 4 新字段展示', async ({ page, request }) => {
    const session = await loginAsBidAdmin(page)

    // 通过 API 创建带 4 新字段的测试资质
    const certNo = `DETAIL-${Date.now()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`
    const createRes = await request.post('/api/knowledge/qualifications', {
      headers: { Authorization: `Bearer ${session.token}`, 'Content-Type': 'application/json' },
      data: {
        name: `E2E资质详情测试-${Date.now()}`,
        certificateNo: certNo,
        issueDate: '2024-01-15',
        expiryDate: '2027-12-31',
        issuer: '中国计量认证中心',
        agency: '代理认证机构X',
        agencyContact: '13800138000',
        certScope: 'ISO9001 质量管理体系认证',
        certReviewNote: '每年 3 月年审',
        remark: 'E2E test fixture'
      }
    })
    expect(createRes.status(), `API create status was ${createRes.status()}`).toBeLessThan(300)

    await page.goto('/knowledge/qualification')
    await page.waitForSelector('.el-table__row, .el-empty', { timeout: 15000 })

    // 找到刚创建的证书行（按 certificateNo 定位）
    const targetRow = page.locator('.el-table__row').filter({ hasText: certNo }).first()
    await expect(targetRow, `找不到含证书号 ${certNo} 的行`).toBeVisible({ timeout: 10000 })

    // 点击行（不点操作按钮）→ 抽屉打开
    await targetRow.locator('td').first().click()
    const drawer = page.locator('.el-drawer').filter({ hasText: '资质详情' }).last()
    await expect(drawer, '点击行后 800px 抽屉应打开').toBeVisible({ timeout: 5000 })

    // 顶部：证书名称 + 状态Tag + [编辑] [下架] [×]
    await expect(drawer.locator('.qd-title')).toContainText('E2E资质详情测试')
    await expect(drawer.locator('[data-testid="qd-status-tag"]')).toBeVisible()
    await expect(drawer.locator('[data-testid="qd-edit-btn"]')).toBeVisible()
    await expect(drawer.locator('[data-testid="qd-retire-btn"]')).toBeVisible()
    await expect(drawer.locator('[data-testid="qd-close-btn"]')).toBeVisible()

    // Tab 1 基本信息
    const basicTab = drawer.locator('[data-testid="qd-tab-basic"]')
    await expect(basicTab).toBeVisible()

    // 4 新字段展示（通过 el-descriptions-item label 文本验证）
    const descText = await drawer.locator('.qd-desc').textContent()
    expect(descText, '基本信息区应显示代理机构').toContain('代理认证机构X')
    expect(descText, '基本信息区应显示代理联系方式').toContain('13800138000')
    expect(descText, '基本信息区应显示认证范围').toContain('ISO9001')
    expect(descText, '基本信息区应显示证书审核提醒').toContain('每年 3 月年审')
    expect(descText, '基本信息区应显示证书名称').toContain('E2E资质详情测试')
    expect(descText, '基本信息区应显示认证机构').toContain('中国计量认证中心')

    // 附件区（演示数据 0 附件 → 显示空状态 + 上传按钮）
    const emptyAtt = drawer.locator('[data-testid="qd-attachment-empty"]')
    await expect(emptyAtt, '无附件应显示空状态').toBeVisible()
    await expect(drawer.locator('[data-testid="qd-att-upload"]')).toBeVisible()

    // Tab 2 操作日志（4.1.3.7 已接入真实数据）
    // 注意：OperationLogTab 的 watch immediate 会在 drawer 打开（panel mount）时调 API，
    // 此时 API 已发出或已完成。直接 waitForSelector 等数据稳定更可靠。
    const auditTab = drawer.getByRole('tab', { name: '操作日志' })
    await expect(auditTab, '操作日志 Tab 应可见').toBeVisible()
    await auditTab.click()
    // 等 audit-logs 数据落地：至少 1 条 item 或 1 个 empty
    await page.waitForResponse(
      r => /\/api\/qualifications\/\d+\/audit-logs$/.test(r.url()),
      { timeout: 10000 }
    ).catch(() => { /* may have already fired before listener */ })
    // 断言 tab 内容区存在
    const opLogRoot = drawer.locator('[data-testid="qd-op-log-tab"]')
    await expect(opLogRoot, '操作日志容器应可见').toBeVisible({ timeout: 10000 })
    // 至少 1 条日志（创建资质时 @Auditable 自动产生）或空状态
    const itemCount = await drawer.locator('[data-testid="qd-op-log-item"]').count()
    const hasEmpty = await drawer.locator('[data-testid="qd-op-log-empty"]').count()
    expect(itemCount + hasEmpty, '操作日志区应展示时间线或空状态').toBeGreaterThanOrEqual(1)

    // 关闭抽屉
    await drawer.locator('[data-testid="qd-close-btn"]').click()
    await expect(drawer).not.toBeVisible({ timeout: 3000 })
  })

  test('关闭按钮可关闭抽屉', async ({ page }) => {
    await loginAsBidAdmin(page)
    await page.goto('/knowledge/qualification')
    await page.waitForSelector('.el-table__row, .el-empty', { timeout: 15000 })

    const rows = await page.locator('.el-table__row').count()
    test.skip(rows === 0, '演示环境无资质数据，跳过关闭测试')

    await page.locator('.el-table__row').first().locator('td').first().click()
    const drawer = page.locator('.el-drawer').filter({ hasText: '资质详情' }).last()
    await expect(drawer).toBeVisible({ timeout: 5000 })

    // 点击 × 关闭
    await drawer.locator('[data-testid="qd-close-btn"]').click()
    await expect(drawer).not.toBeVisible({ timeout: 3000 })
  })

  test('操作按钮区域不应打开抽屉', async ({ page, request }) => {
    const session = await loginAsBidAdmin(page)
    // 创建一条数据确保有可点击行
    const certNo = `BUTTON-${Date.now()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`
    await request.post('/api/knowledge/qualifications', {
      headers: { Authorization: `Bearer ${session.token}`, 'Content-Type': 'application/json' },
      data: {
        name: `E2E按钮测试-${Date.now()}`,
        certificateNo: certNo,
        issueDate: '2024-01-01',
        expiryDate: '2027-12-31',
        issuer: 'CMA'
      }
    })

    await page.goto('/knowledge/qualification')
    await page.waitForSelector('.el-table__row, .el-empty', { timeout: 15000 })
    const targetRow = page.locator('.el-table__row').filter({ hasText: certNo }).first()
    await expect(targetRow).toBeVisible({ timeout: 10000 })

    // 点击操作列的"编辑"按钮（行点击不应触发）
    const editBtn = targetRow.getByRole('button', { name: '编辑' })
    if (await editBtn.count() > 0) {
      await editBtn.click()
      // 抽屉不应打开
      const drawer = page.locator('.el-drawer').filter({ hasText: '资质详情' })
      // 等编辑对话框渲染（dialog mask 出现），以此代替时间等待
      await page.waitForSelector('.el-dialog__wrapper, .el-overlay', { state: 'visible', timeout: 3000 })
        .catch(() => { /* may stay on detail drawer; just continue */ })
      // 编辑按钮通常会打开 QualFormDialog 而不是 QualDetailDrawer
      // 这里我们验证详情抽屉没被错误打开
      const detailDrawerCount = await drawer.count()
      expect(detailDrawerCount, '点操作按钮不应打开详情抽屉').toBe(0)
    }
  })
})
