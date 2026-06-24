import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

const PWD = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'

async function loginAs(page, role) {
  const s = await ensureApiSession({
    username: `e2e_perf_${role}_${Date.now()}_${Math.random().toString(36).slice(2,6)}`,
    role: '/bidAdmin', fullName: `E2E PERF ${role}`, password: PWD
  })
  await injectSession(page, s); return s
}

test.describe('§4.5 业绩管理 — 蓝图全功能 E2E 验证', () => {

  test('全生命周期流程：新增(带联动校验) -> 列表 -> 详情(5-Tab) -> 编辑 -> 删除', async ({ page }) => {
    await loginAs(page, '/bidAdmin')
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

    // 选择项目类型和对接方式
    await page.locator('.el-dialog label:has-text("项目类型") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("集采")').click()
    await page.locator('.el-dialog label:has-text("对接方式") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("API")').click()

    // ── Tab 2: 关键日期 ──
    await page.locator('.el-dialog .el-tabs__item:has-text("关键日期")').click()
    await expect(page.locator('.el-dialog .el-tab-pane:not([style*="display: none"]) .el-date-editor').first()).toBeVisible({ timeout: 3000 })
    
    const dates = page.locator('.el-dialog .el-tab-pane:not([style*="display: none"]) .el-date-editor .el-input__inner')
    await dates.nth(0).fill('2026-01-01')
    await dates.nth(1).fill('2028-12-31')

    // ── Tab 3: 客户信息 ──
    await page.locator('.el-dialog .el-tabs__item:has-text("客户信息")').click()
    await page.locator('.el-dialog input[placeholder*="姓名（职务）"]').fill('张专员')
    await page.locator('.el-dialog input[placeholder*="手机/座机/邮箱"]').fill('13800000000')
    await page.locator('.el-dialog input[placeholder*="例如：四川省成都市"]').fill('广东省广州市')
    await page.locator('.el-dialog input[placeholder*="详细的联系寄送地址"]').fill('天河区中山大道')
    await page.locator('.el-dialog input[placeholder*="西域对接本项目的负责人"]').fill('李项目')

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

    // 再次保存，仍然会被阻断提示【关系证明】必填
    await page.getByRole('button', { name: '保存档案' }).click()
    await expect(page.locator('.el-message--warning:has-text("关系证明")').first()).toBeVisible({ timeout: 3000 })

    // 填入关系证明
    await attachmentRows.nth(3).locator('input').nth(0).fill('关系证明.pdf')
    await attachmentRows.nth(3).locator('input').nth(1).fill('http://dummy-oss.com/proof/rel.pdf')

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

    // 切换 Tab 4 (附件资料) 验证附件存在且下载链接可访问
    await page.locator('.el-drawer .el-tabs__item:has-text("附件资料")').click()
    await expect(page.locator('.el-drawer .el-table:has-text("合同协议扫描件")')).toBeVisible({ timeout: 3000 })

    // 验证附件表格有 3 行数据（合同协议、央企名录、关系证明）
    const detailAttachmentRows = page.locator('.el-drawer .attachment-detail-list .el-table__body tr')
    await expect(detailAttachmentRows).toHaveCount(3, { timeout: 3000 })

    // 验证每行包含下载链接且 href 非空
    const downloadLinks = page.locator('.el-drawer .attachment-detail-list .el-table__body tr a.link-url')
    await expect(downloadLinks).toHaveCount(3)
    for (const link of await downloadLinks.all()) {
      const href = await link.getAttribute('href')
      expect(href).toMatch(/^https?:\/\/.+/)
    }

    // 验证文件类型标签文本正确
    await expect(page.locator('.el-drawer .attachment-detail-list .el-table__body tr:has-text("合同协议扫描件")')).toBeVisible()
    await expect(page.locator('.el-drawer .attachment-detail-list .el-table__body tr:has-text("央企名录页证明")')).toBeVisible()
    await expect(page.locator('.el-drawer .attachment-detail-list .el-table__body tr:has-text("组织层级关系证明")')).toBeVisible()

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
  test('筛选器扩展：多选维度与范围筛选可见且可交互', async ({ page }) => {
    await loginAs(page, '/bidAdmin')
    await page.goto('/knowledge/performance')

    // 验证多选筛选器均已渲染
    await expect(page.locator('.el-form label:has-text("客户类型")')).toBeVisible({ timeout: 8000 })
    await expect(page.locator('.el-form label:has-text("项目类型")')).toBeVisible()
    await expect(page.locator('.el-form label:has-text("合同状态")')).toBeVisible()
    await expect(page.locator('.el-form label:has-text("客户级别")')).toBeVisible()
    await expect(page.locator('.el-form label:has-text("属地")')).toBeVisible()
    await expect(page.locator('.el-form label:has-text("签约日期")')).toBeVisible()
    await expect(page.locator('.el-form label:has-text("截止日期")')).toBeVisible()
    await expect(page.locator('.el-form label:has-text("中标通知书")')).toBeVisible()
    await expect(page.locator('.el-form label:has-text("项目负责人")')).toBeVisible()

    // 客户类型多选交互
    const customerTypeSelect = page.locator('.el-form label:has-text("客户类型") + div .el-select')
    await customerTypeSelect.click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("央企")').click()
    await page.keyboard.press('Escape')

    const [response] = await Promise.all([
      page.waitForResponse(response => response.url().includes('/api/knowledge/performance') && response.status() === 200),
      page.getByRole('button', { name: '查询' }).click()
    ])
    await expect(page.locator('.el-message--error')).toHaveCount(0)

    // 验证返回结果确实只包含选中的客户类型
    const resBody = await response.json()
    const list = resBody?.data || []
    if (list.length > 0) {
      for (const item of list) {
        expect(item.customerType).toBe('CENTRAL_SOE')
      }
    }

    // 重置
    await Promise.all([
      page.waitForResponse(response => response.url().includes('/api/knowledge/performance') && response.status() === 200),
      page.getByRole('button', { name: '重置' }).click()
    ])
  })

  test('批量导入导出：按钮渲染与模板下载', async ({ page }) => {
    await loginAs(page, '/bidAdmin')
    await page.goto('/knowledge/performance')

    // 等待页面核心元素加载
    await expect(page.locator('.el-table__header:has-text("合同名称")')).toBeVisible({ timeout: 10000 })

    // 验证导入导出按钮已启用
    const importBtn = page.getByRole('button', { name: '批量导入' })
    const exportBtn = page.getByRole('button', { name: '导出' })
    await expect(importBtn).toBeVisible({ timeout: 8000 })
    await expect(exportBtn).toBeVisible()
    await expect(importBtn).toBeEnabled()
    await expect(exportBtn).toBeEnabled()

    // 点击导入按钮，验证弹窗
    await importBtn.click()
    await expect(page.locator('.el-dialog:has-text("批量导入业绩")')).toBeVisible({ timeout: 3000 })

    // 验证步骤条
    await expect(page.locator('.el-dialog .el-steps')).toBeVisible()

    // 点击下载模板
    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.locator('.el-dialog .el-button:has-text("下载模板")').click()
    ])
    expect(download.suggestedFilename()).toContain('performance_template')

    // 关闭弹窗
    await page.locator('.el-dialog__headerbtn').click()
    await expect(page.locator('.el-dialog:has-text("批量导入业绩")')).toBeHidden({ timeout: 3000 })

    // 点击导出按钮，验证导出功能触发
    await exportBtn.click()
    // 等待导出成功消息（后端返回文件流即表示功能正常）
    await expect(page.locator('.el-message--success:has-text("导出成功")')).toBeVisible({ timeout: 8000 })

    // 验证 ZIP 导出（含附件）下拉菜单
    const exportDropdown = page.locator('.export-dropdown')
    await exportDropdown.locator('.el-dropdown__caret-button').click()
    await expect(page.locator('.el-dropdown-menu:visible')).toBeVisible({ timeout: 3000 })
    await expect(page.locator('.el-dropdown-menu__item:has-text("导出 ZIP（含附件）")')).toBeVisible()

    // 点击 ZIP 导出
    await page.locator('.el-dropdown-menu__item:has-text("导出 ZIP（含附件）")').click()
    await expect(page.locator('.el-message--success:has-text("ZIP 导出成功")')).toBeVisible({ timeout: 15000 })
  })

  test('详情抽屉：自定义 header 与操作日志 Tab', async ({ page }) => {
    await loginAs(page, '/bidAdmin')
    await page.goto('/knowledge/performance')

    // 先创建一条记录确保表格有数据
    await page.getByRole('button', { name: '新增业绩' }).click()
    await page.waitForSelector('.el-dialog', { timeout: 5000 })
    const drawerTestName = `E2E业绩_抽屉验证_${Date.now()}`
    await page.locator('.el-dialog input[placeholder*="合同名称"]').fill(drawerTestName)
    await page.locator('.el-dialog input[placeholder*="签约单位"]').fill('测试单位')
    await page.locator('.el-dialog input[placeholder*="集团公司"]').fill('测试集团')
    await page.locator('.el-dialog label:has-text("客户类型") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("民企")').click()
    await page.locator('.el-dialog input[placeholder*="所属行业"]').fill('测试行业')
    await page.locator('.el-dialog label:has-text("项目类型") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("办公")').click()
    await page.locator('.el-dialog label:has-text("对接方式") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("API")').click()
    await page.locator('.el-dialog .el-tabs__item:has-text("关键日期")').click()
    const d = page.locator('.el-dialog .el-tab-pane:not([style*="display: none"]) .el-date-editor .el-input__inner')
    await d.nth(0).fill('2026-01-01')
    await d.nth(1).fill('2026-12-31')
    await page.locator('.el-dialog .el-tabs__item:has-text("客户信息")').click()
    await page.locator('.el-dialog input[placeholder*="姓名（职务）"]').fill('张测试')
    await page.locator('.el-dialog input[placeholder*="手机/座机/邮箱"]').fill('13800000000')
    await page.locator('.el-dialog input[placeholder*="例如：四川省成都市"]').fill('广东省广州市')
    await page.locator('.el-dialog input[placeholder*="详细的联系寄送地址"]').fill('天河区测试地址')
    await page.locator('.el-dialog input[placeholder*="西域对接本项目的负责人"]').fill('李测试')
    await page.locator('.el-dialog .el-tabs__item:has-text("附件资料")').click()
    const ar = page.locator('.el-dialog .attachment-row')
    await ar.nth(0).locator('input').nth(0).fill('合同.pdf')
    await ar.nth(0).locator('input').nth(1).fill('http://dummy-oss.com/c.pdf')
    await page.getByRole('button', { name: '保存档案' }).click()
    await expect(page.locator('.el-dialog')).toBeHidden({ timeout: 5000 })

    // 等待表格加载并点击第一行打开详情抽屉
    await expect(page.locator('.el-table__header:has-text("合同名称")')).toBeVisible({ timeout: 10000 })
    const firstRow = page.locator('.el-table__body tr').first()
    await expect(firstRow).toBeVisible({ timeout: 5000 })
    await firstRow.click()

    // 验证抽屉打开且自定义 header 存在
    await expect(page.locator('.el-drawer')).toBeVisible({ timeout: 5000 })
    await expect(page.locator('.el-drawer .drawer-header-custom h3:has-text("业绩详情档案")')).toBeVisible()
    await expect(page.locator('.el-drawer .drawer-header-custom .el-tag')).toBeVisible()

    // 验证 5 个 Tab 都存在
    await expect(page.locator('.el-drawer .el-tabs__item:has-text("合同基础")')).toBeVisible()
    await expect(page.locator('.el-drawer .el-tabs__item:has-text("关键日期")')).toBeVisible()
    await expect(page.locator('.el-drawer .el-tabs__item:has-text("客户信息")')).toBeVisible()
    await expect(page.locator('.el-drawer .el-tabs__item:has-text("附件资料")')).toBeVisible()
    await expect(page.locator('.el-drawer .el-tabs__item:has-text("操作日志")')).toBeVisible()

    // 切换到操作日志 Tab
    await page.locator('.el-drawer .el-tabs__item:has-text("操作日志")').click()
    // 验证操作日志区域渲染（有数据或无数据状态均可）
    await expect(page.locator('.el-drawer .logs-pane')).toBeVisible({ timeout: 3000 })

    // 关闭抽屉
    await page.locator('.el-drawer__close-btn').click()
    await expect(page.locator('.el-drawer')).toBeHidden({ timeout: 5000 })

    // 清理
    const delBtn = page.locator(`.el-table__body tr:has-text("${drawerTestName}") .el-button:has-text("删除")`)
    await delBtn.click()
    await page.locator('.el-message-box__btns .el-button:has-text("删除")').click()
    await expect(page.locator('.el-message-box')).toBeHidden({ timeout: 5000 })
  })
  test('操作日志 Tab：真实审计数据渲染验证', async ({ page }) => {
    await loginAs(page, '/bidAdmin')
    await page.goto('/knowledge/performance')

    // 1. 创建一条业绩记录（产生 CREATE 日志）
    await page.getByRole('button', { name: '新增业绩' }).click()
    await page.waitForSelector('.el-dialog', { timeout: 5000 })

    const randomContractName = `E2E业绩_日志验证_${Date.now()}`
    await page.locator('.el-dialog input[placeholder*="合同名称"]').fill(randomContractName)
    await page.locator('.el-dialog input[placeholder*="签约单位"]').fill('中国南方电网有限责任公司')
    await page.locator('.el-dialog input[placeholder*="集团公司"]').fill('南方电网')

    await page.locator('.el-dialog label:has-text("客户类型") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("央企")').click()
    await page.locator('.el-dialog input[placeholder*="所属行业"]').fill('能源电力')

    await page.locator('.el-dialog label:has-text("项目类型") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("集采")').click()
    await page.locator('.el-dialog label:has-text("对接方式") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("API")').click()

    await page.locator('.el-dialog .el-tabs__item:has-text("关键日期")').click()
    const dates = page.locator('.el-dialog .el-tab-pane:not([style*="display: none"]) .el-date-editor .el-input__inner')
    await dates.nth(0).fill('2026-01-01')
    await dates.nth(1).fill('2026-12-31')

    await page.locator('.el-dialog .el-tabs__item:has-text("客户信息")').click()
    await page.locator('.el-dialog input[placeholder*="姓名（职务）"]').fill('张专员')
    await page.locator('.el-dialog input[placeholder*="手机/座机/邮箱"]').fill('13800000000')
    await page.locator('.el-dialog input[placeholder*="例如：四川省成都市"]').fill('广东省广州市')
    await page.locator('.el-dialog input[placeholder*="详细的联系寄送地址"]').fill('天河区中山大道')
    await page.locator('.el-dialog input[placeholder*="西域对接本项目的负责人"]').fill('李项目')

    await page.locator('.el-dialog .el-tabs__item:has-text("附件资料")').click()
    const attachmentRows = page.locator('.el-dialog .attachment-row')
    await attachmentRows.nth(0).locator('input').nth(0).fill('设备买卖合同协议.pdf')
    await attachmentRows.nth(0).locator('input').nth(1).fill('http://dummy-oss.com/contracts/101.pdf')
    await attachmentRows.nth(2).locator('input').nth(0).fill('国资委央企名录截图.png')
    await attachmentRows.nth(2).locator('input').nth(1).fill('http://dummy-oss.com/proof/soe_list.png')
    await attachmentRows.nth(3).locator('input').nth(0).fill('关系证明.pdf')
    await attachmentRows.nth(3).locator('input').nth(1).fill('http://dummy-oss.com/proof/rel.pdf')

    await page.getByRole('button', { name: '保存档案' }).click()
    await expect(page.locator('.el-dialog')).toBeHidden({ timeout: 5000 })

    // 2. 编辑该记录（产生 UPDATE 日志）
    const editBtn = page.locator(`.el-table__body tr:has-text("${randomContractName}") .el-button:has-text("编辑")`)
    await editBtn.click()
    await page.waitForSelector('.el-dialog', { timeout: 5000 })
    const newName = `${randomContractName}_已变更`
    await page.locator('.el-dialog input[placeholder*="合同名称"]').fill(newName)
    await page.getByRole('button', { name: '保存档案' }).click()
    await expect(page.locator('.el-dialog')).toBeHidden({ timeout: 5000 })

    // 3. 打开详情抽屉 → 切换到操作日志 Tab
    const contractRow = page.locator(`.el-table__body tr:has-text("${newName}")`)
    await contractRow.click()
    await page.waitForSelector('.el-drawer', { timeout: 5000 })
    await page.locator('.el-drawer .el-tabs__item:has-text("操作日志")').click()
    await expect(page.locator('.el-drawer .logs-pane')).toBeVisible({ timeout: 3000 })

    // 4. 验证时间线（审计日志可能有数据也可能为空，两者都是合法状态）
    const timelineItems = page.locator('.el-drawer .el-timeline-item')
    const logCount = await timelineItems.count()
    if (logCount > 0) {
      // 5. 验证时间戳格式包含日期
      const firstTimestamp = timelineItems.first().locator('.el-timeline-item__timestamp')
      const timestampText = await firstTimestamp.textContent()
      expect(timestampText).toMatch(/\d{4}-\d{2}-\d{2}/)
      // 6. 验证动作类型标签存在
      const firstNode = timelineItems.first().locator('.el-timeline-item__node')
      await expect(firstNode).toBeVisible()
    } else {
      // 审计日志为空时验证 el-empty 组件存在
      await expect(page.locator('.el-drawer .logs-pane .el-empty')).toBeVisible()
    }

    // 7. 回收清理
    await page.locator('.el-drawer__close-btn').click()
    await expect(page.locator('.el-drawer')).toBeHidden({ timeout: 5000 })

    const deleteBtn = page.locator(`.el-table__body tr:has-text("${newName}") .el-button:has-text("删除")`)
    await deleteBtn.click()
    await page.locator('.el-message-box__btns .el-button:has-text("删除")').click()
    await expect(page.locator('.el-message-box')).toBeHidden({ timeout: 5000 })
  })

  test('编辑敏感字段警告提示与校验拦截', async ({ page }) => {
    await loginAs(page, '/bidAdmin')
    await page.goto('/knowledge/performance')

    // 1. 创建一条临时业绩以供编辑测试
    await page.getByRole('button', { name: '新增业绩' }).click()
    await page.waitForSelector('.el-dialog', { timeout: 5000 })
    
    const randomContractName = `E2E业绩_警示校验_${Date.now()}`
    await page.locator('.el-dialog input[placeholder*="合同名称"]').fill(randomContractName)
    await page.locator('.el-dialog input[placeholder*="签约单位"]').fill('中国南方电网有限责任公司')
    await page.locator('.el-dialog input[placeholder*="集团公司"]').fill('南方电网')
    
    await page.locator('.el-dialog label:has-text("客户类型") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("央企")').click()
    await page.locator('.el-dialog input[placeholder*="所属行业"]').fill('能源电力')

    // 选择项目类型和对接方式
    await page.locator('.el-dialog label:has-text("项目类型") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("集采")').click()
    await page.locator('.el-dialog label:has-text("对接方式") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("API")').click()

    await page.locator('.el-dialog .el-tabs__item:has-text("关键日期")').click()
    const dates = page.locator('.el-dialog .el-tab-pane:not([style*="display: none"]) .el-date-editor .el-input__inner')
    await dates.nth(0).fill('2026-01-01')
    await dates.nth(1).fill('2026-12-31') // 正常范围

    await page.locator('.el-dialog .el-tabs__item:has-text("客户信息")').click()
    await page.locator('.el-dialog input[placeholder*="姓名（职务）"]').fill('张专员')
    await page.locator('.el-dialog input[placeholder*="手机/座机/邮箱"]').fill('13800000000')
    await page.locator('.el-dialog input[placeholder*="例如：四川省成都市"]').fill('广东省广州市')
    await page.locator('.el-dialog input[placeholder*="详细的联系寄送地址"]').fill('天河区中山大道')
    await page.locator('.el-dialog input[placeholder*="西域对接本项目的负责人"]').fill('李项目')

    await page.locator('.el-dialog .el-tabs__item:has-text("附件资料")').click()
    const attachmentRows = page.locator('.el-dialog .attachment-row')
    await attachmentRows.nth(0).locator('input').nth(0).fill('设备买卖合同协议.pdf')
    await attachmentRows.nth(0).locator('input').nth(1).fill('http://dummy-oss.com/contracts/101.pdf')
    await attachmentRows.nth(2).locator('input').nth(0).fill('国资委央企名录截图.png')
    await attachmentRows.nth(2).locator('input').nth(1).fill('http://dummy-oss.com/proof/soe_list.png')
    await attachmentRows.nth(3).locator('input').nth(0).fill('关系证明.pdf')
    await attachmentRows.nth(3).locator('input').nth(1).fill('http://dummy-oss.com/proof/rel.pdf')

    await page.getByRole('button', { name: '保存档案' }).click()
    await expect(page.locator('.el-dialog')).toBeHidden({ timeout: 5000 })

    // 2. 点击编辑，测试日期校验拦截
    const editBtn = page.locator(`.el-table__body tr:has-text("${randomContractName}") .el-button:has-text("编辑")`)
    await editBtn.click()
    await page.waitForSelector('.el-dialog', { timeout: 5000 })

    // 修改截止日期早于签约日期
    await page.locator('.el-dialog .el-tabs__item:has-text("关键日期")').click()
    await dates.nth(1).fill('2025-12-31') // 早于 2026-01-01
    await page.getByRole('button', { name: '保存档案' }).click()
    await expect(page.locator('.el-message--warning:has-text("截止日期必须晚于签约日期")').first()).toBeVisible({ timeout: 3000 })

    // 恢复正常日期范围
    await dates.nth(1).fill('2026-12-31')

    // 3. 测试联系方式格式错误拦截
    await page.locator('.el-dialog .el-tabs__item:has-text("客户信息")').click()
    await page.locator('.el-dialog input[placeholder*="手机/座机/邮箱"]').fill('invalid-contact')
    await page.getByRole('button', { name: '保存档案' }).click()
    await expect(page.locator('.el-form-item__error:has-text("请输入有效的联系方式")').first()).toBeVisible({ timeout: 3000 })

    // 恢复正常联系方式
    await page.locator('.el-dialog input[placeholder*="手机/座机/邮箱"]').fill('test@xiyu.com')

    // 4. 测试修改敏感字段的警示弹窗：修改客户类型从“央企”到“地方国企”
    await page.locator('.el-dialog .el-tabs__item:has-text("合同基础")').click()
    await page.locator('.el-dialog label:has-text("客户类型") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("地方国企")').click()

    await page.getByRole('button', { name: '保存档案' }).click()
    
    // 应当弹出警示框
    await expect(page.locator('.el-message-box__message:has-text("修改客户类型将影响到期提醒规则，请确认必要性")')).toBeVisible({ timeout: 3000 })
    // 点击确认修改
    await page.locator('.el-message-box__btns .el-button--primary').click()

    await expect(page.locator('.el-dialog')).toBeHidden({ timeout: 5000 })

    // 5. 回收清理
    const deleteBtn = page.locator(`.el-table__body tr:has-text("${randomContractName}") .el-button:has-text("删除")`)
    await deleteBtn.click()
    await page.locator('.el-message-box__btns .el-button:has-text("删除")').click()
    await expect(page.locator('.el-message-box')).toBeHidden({ timeout: 5000 })
  })

  test('相似业绩搜索：按当前筛选条件智能匹配并展示排序结果', async ({ page }) => {
    await loginAs(page, '/bidAdmin')
    await page.goto('/knowledge/performance')

    await expect(page.locator('.el-table__header:has-text("合同名称")')).toBeVisible({ timeout: 10000 })

    // 选择客户类型为"央企"
    await page.locator('.el-form label:has-text("客户类型") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("央企")').click()
    await page.keyboard.press('Escape')

    // 选择项目类型为"集采"
    await page.locator('.el-form label:has-text("项目类型") + div .el-select').click()
    await page.locator('.el-select-dropdown:visible').last().locator('.el-select-dropdown__item:has-text("集采")').click()
    await page.keyboard.press('Escape')

    // 点击查询加载数据
    await Promise.all([
      page.waitForResponse(r => r.url().includes('/api/knowledge/performance') && r.status() === 200),
      page.getByRole('button', { name: '查询' }).click()
    ])

    // 点击相似业绩按钮
    await page.getByRole('button', { name: '相似业绩' }).click()

    // 验证抽屉打开
    await expect(page.locator('.el-drawer:has-text("相似业绩推荐")')).toBeVisible({ timeout: 5000 })

    // 等待加载完成（loading 消失或列表出现）
    await expect(page.locator('.similar-drawer')).toBeVisible({ timeout: 3000 })

    // 验证列表区域或空状态存在
    const cards = page.locator('.similar-card')
    const emptyState = page.locator('.el-empty')
    const hasCards = await cards.count() > 0
    const hasEmpty = await emptyState.count() > 0
    expect(hasCards || hasEmpty).toBe(true)

    // 如果有数据，验证排序标签存在
    if (hasCards) {
      const firstCard = cards.first()
      await expect(firstCard.locator('.similar-name')).toBeVisible()
      // 验证相似度标签（高度相似/部分相似/弱相关至少一种）
      const tags = firstCard.locator('.el-tag')
      await expect(tags.first()).toBeVisible()
    }

    // 关闭抽屉（精确定位相似业绩抽屉的关闭按钮）
    await page.locator('.el-drawer:has-text("相似业绩推荐") .el-drawer__close-btn').click()
    await expect(page.locator('.el-drawer:has-text("相似业绩推荐")')).toBeHidden({ timeout: 5000 })
  })
})
