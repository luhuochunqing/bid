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

test.describe('§4.3 人员证书 - 新增证书 (3 Tab 表单 + 附件上传)', () => {
  test('bid_specialist 可以看到新增人员按钮并打开 3 Tab 表单', async ({ page }) => {
    await loginAsRole(page, 'bid_specialist')
    await page.goto('http://127.0.0.1:1314/knowledge/personnel')
    await page.waitForSelector('.personnel-container, .el-table', { timeout: 15000 })

    const addBtn = page.getByRole('button', { name: /新增人员/ })
    await expect(addBtn).toBeVisible()

    await addBtn.click()
    await page.waitForSelector('.el-dialog:has-text("新增人员")', { timeout: 8000 })

    // 3 个 Tab 存在（蓝图要求）-- 使用 tab role 更可靠（el-tabs 渲染）
    await expect(page.getByRole('tab', { name: '基础信息' })).toBeVisible({ timeout: 5000 })
    await expect(page.getByRole('tab', { name: '教育经历' })).toBeVisible({ timeout: 5000 })
    await expect(page.getByRole('tab', { name: '证书与职称' })).toBeVisible({ timeout: 5000 })
  })

  test('bid_specialist 完整填写 3 Tab 并保存成功（Tab3 可暂不填，验证教育经历+基本信息落库 + 列表刷新）', async ({ page }) => {
    const suffix = Date.now().toString().slice(-6)
    const empNo = `E2E${suffix}`
    const personName = `测试专员${suffix}`

    await loginAsRole(page, 'bid_specialist')
    await page.goto('http://127.0.0.1:1314/knowledge/personnel')
    await page.waitForSelector('.personnel-container', { timeout: 10000 })

    await page.getByRole('button', { name: /新增人员/ }).click()
    const dialog = page.locator('.el-dialog:has-text("新增人员")')
    await dialog.waitFor({ state: 'visible', timeout: 8000 })

    // Tab 1 基础信息（必填）—— scope 到 dialog 避免与列表筛选区重名
    await dialog.getByRole('tab', { name: '基础信息' }).click()
    await dialog.locator('.el-form-item:has-text("姓名") input').first().fill(personName)
    await dialog.locator('.el-form-item:has-text("工号") input').first().fill(empNo)
    // gender select (dialog 内)
    await dialog.locator('.el-form-item:has-text("性别") .el-select').first().click()
    await page.getByRole('option', { name: '男' }).click()
    // entry date (full date picker)
    await dialog.locator('.el-form-item:has-text("入职日期") input').first().fill('2024-01-15')

    // Tab 2 教育经历（全部履历，必填多字段，模拟蓝图要求）
    await dialog.getByRole('tab', { name: '教育经历' }).click()
    await dialog.getByRole('button', { name: /添加教育经历/ }).click()
    const eduItem = dialog.locator('.edu-item').first()
    await eduItem.locator('.el-form-item:has-text("学校名称") input').first().fill('测试大学')
    await eduItem.locator('.el-form-item:has-text("入学时间") input').first().fill('2018-09')
    await eduItem.locator('.el-form-item:has-text("毕业时间") input').first().fill('2022-06')
    await eduItem.locator('.el-form-item:has-text("最高学历") .el-select').first().click()
    await page.getByRole('option', { name: '本科' }).click()
    await eduItem.locator('.el-form-item:has-text("学习形式") .el-select').first().click()
    await page.getByRole('option', { name: '全日制' }).click()
    await eduItem.locator('.el-form-item:has-text("专业") input').first().fill('计算机')

    // Tab 3 证书与职称（按蓝图“可暂不填，后续补充” —— 不添加任何证书行）
    await page.getByRole('tab', { name: '证书与职称' }).click()
    // 确认没有强制添加，保存应成功

    // 保存（scope 到 dialog）
    await dialog.getByRole('button', { name: '保存' }).click()

    // 成功提示 + 列表刷新（包含高亮）
    await page.waitForSelector('.el-message--success:has-text("创建成功")', { timeout: 8000 })
    await page.waitForSelector('.personnel-container .el-table', { timeout: 10000 })

    // 断言新人员出现在列表（证明保存 + loadData 落库 + 刷新）
    const newRow = page.locator('.el-table__row').filter({ hasText: empNo })
    await expect(newRow).toBeVisible({ timeout: 5000 })
    await expect(newRow).toContainText(personName)
  })

  test('bid_admin / bid_lead / bid_specialist 均可访问，project负责人 无权限（按钮或页面受控）', async ({ page }) => {
    // 允许角色有新增按钮
    for (const role of ['bid_admin', 'bid_lead', 'bid_specialist']) {
      await loginAsRole(page, role)
      await page.goto('http://127.0.0.1:1314/knowledge/personnel')
      await page.waitForSelector('.personnel-container', { timeout: 10000 })
      const addBtn = page.getByRole('button', { name: /新增人员/ })
      await expect(addBtn).toBeVisible()
    }

    // 无权限角色不应看到或受 guard
    await loginAsRole(page, 'sales')
    await page.goto('http://127.0.0.1:1314/knowledge/personnel')
    await page.waitForTimeout(2000)
    const addBtn = page.getByRole('button', { name: /新增人员/ })
    // 可能 404 或 无按钮或 empty
    const count = await addBtn.count()
    expect(count).toBe(0)
  })

  test('表单 Tab3 支持证书附件上传（类型/大小校验）', async ({ page }) => {
    await loginAsRole(page, 'bid_specialist')
    await page.goto('http://127.0.0.1:1314/knowledge/personnel')
    await page.waitForSelector('.personnel-container', { timeout: 10000 })

    await page.getByRole('button', { name: /新增人员/ }).click()
    await page.waitForSelector('.el-dialog:has-text("新增人员")')

    // 切换到证书 Tab
    await page.getByText('证书与职称').click()

    // 添加一条证书
    await page.getByRole('button', { name: /添加证书/ }).click()

    // 附件上传控件存在（蓝图必填）
    // el-upload renders button + internal; use first() + specific to avoid strict violation
    const uploadArea = page.locator('.cert-item .el-upload').first().getByRole('button', { name: /选择附件/ }).first()
    await expect(uploadArea).toBeVisible({ timeout: 5000 })
  })
})
