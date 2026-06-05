import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

const PWD = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'

async function loginAs(page, role) {
  const s = await ensureApiSession({
    username: `e2e_perf_${role}_${Date.now()}_${Math.random().toString(36).slice(2,6)}`,
    role: 'ADMIN', fullName: `E2E PERF ${role}`, password: PWD
  })
  await injectSession(page, s); return s
}

test.describe('§4.5 业绩管理 — 蓝图全功能 E2E 验证', () => {

  test('全生命周期流程：新增(带联动校验) -> 列表 -> 详情(5-Tab) -> 编辑 -> 删除', async ({ page }) => {
    await loginAs(page, 'bid_admin')
    await page.goto('/knowledge/performance')

    // 1. 验证列表页面核心列是否渲染
    await expect(page.locator('.el-table__header:has-text("合同名称")')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('.el-table__header:has-text("签约单位")')).toBeVisible()
    await expect(page.locator('.el-table__header:has-text("客户类型")')).toBeVisible()
    await expect(page.locator('.el-table__header:has-text("到期天数")')).toBeVisible()
    await expect(page.locator('.el-table__header:has-text("状态")')).toBeVisible()

    // 2. 点击新增业绩
    await page.getByRole('button', { name: '新增业绩' }).click()
    await page.waitForSelector('.el-dialog', { timeout: 5000 })

    // ── Tab 1: 基础 ──
    const randomContractName = `E2E业绩_电力设备集采合同_${Date.now()}`
    await page.locator('.el-dialog input[placeholder*="合同名称"]').fill(randomContractName)
    await page.locator('.el-dialog input[placeholder*="签约单位"]').fill('中国南方电网有限责任公司')
    await page.locator('.el-dialog input[placeholder*="集团公司"]').fill('南方电网')
    
    // 选择客户类型为“央企”以触发央企必填联动
    await page.locator('.el-dialog label:has-text("客户类型") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("央企")').click()
    await page.locator('.el-dialog input[placeholder*="所属行业"]').fill('能源电力')

    // ── Tab 2: 关键日期 ──
    await page.locator('.el-dialog .el-tabs__item:has-text("关键日期")').click()
    await expect(page.locator('.el-dialog .el-tab-pane:not([style*="display: none"]) .el-date-editor').first()).toBeVisible({ timeout: 3000 })
    
    const dates = page.locator('.el-dialog .el-tab-pane:not([style*="display: none"]) .el-date-editor .el-input__inner')
    await dates.nth(0).fill('2026-01-01')
    await dates.nth(1).fill('2028-12-31')

    // ── Tab 4: 附件资料 (验证联动校验) ──
    await page.locator('.el-dialog .el-tabs__item:has-text("附件资料")').click()
    await expect(page.locator('.el-dialog .attachment-row').first()).toBeVisible({ timeout: 3000 })
    
    // 不填入任何附件，点击保存，应被阻断并提示【合同协议】必填
    await page.getByRole('button', { name: '保存档案' }).click()
    await expect(page.locator('.el-message--warning:has-text("合同协议")').first()).toBeVisible({ timeout: 3000 })

    // 录入合同协议链接
    const attachmentRows = page.locator('.el-dialog .attachment-row')
    // 第一个 row 是合同协议
    await attachmentRows.nth(0).locator('input').nth(0).fill('设备买卖合同协议.pdf')
    await attachmentRows.nth(0).locator('input').nth(1).fill('http://dummy-oss.com/contracts/101.pdf')

    // 再次保存，因为客户类型是“央企”，会被联动校验阻断，提示【央企名录】与【关系证明】必填
    await page.getByRole('button', { name: '保存档案' }).click()
    await expect(page.locator('.el-message--warning:has-text("央企名录")').first()).toBeVisible({ timeout: 3000 })

    // 填入央企名录
    await attachmentRows.nth(2).locator('input').nth(0).fill('国资委央企名录截图.png')
    await attachmentRows.nth(2).locator('input').nth(1).fill('http://dummy-oss.com/proof/soe_list.png')

    // 保存档案
    await page.getByRole('button', { name: '保存档案' }).click()
    await expect(page.locator('.el-dialog')).toBeHidden({ timeout: 5000 })

    // 3. 验证新业绩显示在表格中
    const contractRow = page.locator(`.el-table__body tr:has-text("${randomContractName}")`)
    await expect(contractRow).toBeVisible({ timeout: 5000 })

    // 4. 点击行打开详情抽屉，验证 5-Tab 信息
    await contractRow.click()
    await page.waitForSelector('.el-drawer', { timeout: 5000 })
    await expect(page.locator('.el-drawer:has-text("业绩详情档案")')).toBeVisible()

    // 切换 Tab 2 (关键日期) 并验证日期正确
    await page.locator('.el-drawer .el-tabs__item:has-text("关键日期")').click()
    await expect(page.locator('.el-drawer .el-descriptions:has-text("2028-12-31")')).toBeVisible({ timeout: 3000 })

    // 切换 Tab 4 (附件资料) 验证附件存在
    await page.locator('.el-drawer .el-tabs__item:has-text("附件资料")').click()
    await expect(page.locator('.el-drawer .el-table:has-text("合同协议扫描件")')).toBeVisible({ timeout: 3000 })

    // 关闭抽屉
    await page.locator('.el-drawer__close-btn').click()
    await expect(page.locator('.el-drawer')).toBeHidden({ timeout: 5000 })

    // 5. 编辑业绩
    const editBtn = page.locator(`.el-table__body tr:has-text("${randomContractName}") .el-button:has-text("编辑")`)
    await editBtn.click()
    await page.waitForSelector('.el-dialog', { timeout: 5000 })

    // 修改合同名称
    const newName = `${randomContractName}_已变更`
    await page.locator('.el-dialog input[placeholder*="合同名称"]').fill(newName)
    
    // 保存档案
    await page.getByRole('button', { name: '保存档案' }).click()
    await expect(page.locator('.el-dialog')).toBeHidden({ timeout: 5000 })

    // 验证列表中已更新
    await expect(page.locator(`.el-table__body tr:has-text("${newName}")`)).toBeVisible({ timeout: 5000 })

    // 6. 删除业绩
    const deleteBtn = page.locator(`.el-table__body tr:has-text("${newName}") .el-button:has-text("删除")`)
    await deleteBtn.click()
    await page.waitForSelector('.el-message-box', { timeout: 3000 })
    
    // 点击确认删除
    await page.locator('.el-message-box__btns .el-button:has-text("删除")').click()
    await expect(page.locator('.el-message-box')).toBeHidden({ timeout: 5000 })

    // 验证新业绩已从表格消失
    await expect(page.locator(`.el-table__body tr:has-text("${newName}")`)).toHaveCount(0, { timeout: 3000 })
  })
  test('筛选器扩展：属地/截止日期范围/中标通知书筛选可见且可交互', async ({ page }) => {
    await loginAs(page, 'bid_admin')
    await page.goto('/knowledge/performance')

    // 验证新增的筛选器均已渲染
    await expect(page.locator('.el-form label:has-text("属地")')).toBeVisible({ timeout: 8000 })
    await expect(page.locator('.el-form label:has-text("签约日期")')).toBeVisible()
    await expect(page.locator('.el-form label:has-text("截止日期")')).toBeVisible()
    await expect(page.locator('.el-form label:has-text("中标通知书")')).toBeVisible()
    await expect(page.locator('.el-form label:has-text("项目负责人")')).toBeVisible()

    // 属地筛选器输入交互
    const territoryInput = page.locator('.el-form').getByPlaceholder('省/市关键词')
    await territoryInput.fill('广东')
    await Promise.all([
      page.waitForResponse(response => response.url().includes('/api/knowledge/performance') && response.status() === 200),
      page.getByRole('button', { name: '查询' }).click()
    ])
    // 查询结果不包含错误提示即可
    await expect(page.locator('.el-message--error')).toHaveCount(0)

    // 中标通知书筛选器交互
    await Promise.all([
      page.waitForResponse(response => response.url().includes('/api/knowledge/performance') && response.status() === 200),
      page.getByRole('button', { name: '重置' }).click()
    ])
    // 选择"有"
    const bidNoticeSelect = page.locator('.el-form label:has-text("中标通知书") + div .el-select')
    await bidNoticeSelect.click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("有")').click()
    await Promise.all([
      page.waitForResponse(response => response.url().includes('/api/knowledge/performance') && response.status() === 200),
      page.getByRole('button', { name: '查询' }).click()
    ])
    await expect(page.locator('.el-message--error')).toHaveCount(0)

    // 重置所有筛选器
    await Promise.all([
      page.waitForResponse(response => response.url().includes('/api/knowledge/performance') && response.status() === 200),
      page.getByRole('button', { name: '重置' }).click()
    ])
    await expect(territoryInput).toHaveValue('')
  })
})
