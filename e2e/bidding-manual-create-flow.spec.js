import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

const pad = n => String(n).padStart(2, '0')
function fmtDate(d) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function clickDropdownItem(page, index = 0) {
  await page.waitForTimeout(400)
  await page.locator('.el-select-dropdown:visible').last()
    .locator('.el-select-dropdown__item').nth(index).click()
  await page.waitForTimeout(300)
}

async function setFormDates(page, deadlineDate, bidOpeningDate) {
  const set = await page.evaluate((dates) => {
    const appEl = document.querySelector('#app')
    if (!appEl?.__vue_app__) return false
    function findComp(vnode, name) {
      if (!vnode) return null
      const type = vnode.type
      if (type && (type.__name === name || type.name === name)) return vnode.component
      if (vnode.component?.subTree) { const f = findComp(vnode.component.subTree, name); if (f) return f }
      if (vnode.dynamicChildren) { for (const c of vnode.dynamicChildren) { const f = findComp(c, name); if (f) return f } }
      if (Array.isArray(vnode.children)) { for (const c of vnode.children) { const f = findComp(c, name); if (f) return f } }
      return null
    }
    const comp = findComp(appEl.__vue_app__._instance?.vnode, 'TenderCreatePage')
    // In <script setup>, setupState auto-unwraps refs — form is already the reactive object
    if (comp?.setupState?.form) {
      comp.setupState.form.deadline = dates.deadline
      comp.setupState.form.bidOpeningTime = dates.bidOpeningTime
      return true
    }
    return false
  }, { deadline: fmtDate(deadlineDate), bidOpeningTime: fmtDate(bidOpeningDate) })
  return set
}

async function fillAndSave(page, namePrefix) {
  const bar = page.locator('.bottom-action-bar-inner')
  const suffix = Date.now()
  await page.fill('input[placeholder="请输入项目名称"]', `${namePrefix} ${suffix}`)
  await page.fill('input[placeholder="请输入招标主体"]', 'E2E 招标代理有限公司')
  await page.locator('.el-select').first().click(); await clickDropdownItem(page)
  await page.locator('.el-select').nth(1).click(); await clickDropdownItem(page)
  await page.locator('.el-select').nth(2).click(); await clickDropdownItem(page)
  await page.fill('input[placeholder="联系人姓名"]', '张测试')
  await page.fill('input[placeholder="手机号"]', '13800000000')
  const ok = await setFormDates(page, new Date(Date.now() + 10 * 86400000), new Date(Date.now() + 14 * 86400000))
  if (!ok) throw new Error('Failed to set form dates via Vue')
  const saveBtn = bar.getByRole('button', { name: '保存' })
  await expect(saveBtn).toBeEnabled({ timeout: 5000 })
  await saveBtn.click()
  await page.waitForTimeout(5000)
  return bar
}

async function assignTender(page, bar) {
  await expect(bar.getByRole('button', { name: '分配' })).toBeVisible({ timeout: 10000 })
  await bar.getByRole('button', { name: '分配' }).click()
  await page.waitForSelector('.el-dialog:has-text("指派标讯")', { timeout: 8000 })
  await page.waitForTimeout(1500)
  const dialog = page.locator('.el-dialog:has-text("指派标讯")')
  const assignSelect = dialog.locator('.el-select').first()
  if (await assignSelect.isVisible({ timeout: 2000 }).catch(() => false)) {
    await assignSelect.click()
    await page.waitForTimeout(500)
    try { await clickDropdownItem(page) } catch { /* no candidate */ }
  }
  await dialog.getByRole('button', { name: '确认指派' }).click()
  await page.waitForTimeout(3000)
}

test.describe('人工录入标讯全流程 — 按钮状态机', () => {
  test.beforeEach(async ({ page }) => {
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const session = await ensureApiSession({
      username: `e2e_flow_${suffix}`,
      role: 'bid_admin',
      fullName: 'E2E 流程测试 Admin'
    })
    await injectSession(page, session)
    page.on('pageerror', err => console.error('PAGE ERROR:', err.message))
  })

  test('未保存时仅展示取消和保存按钮', async ({ page }) => {
    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })
    const bar = page.locator('.bottom-action-bar-inner')
    await expect(bar.getByRole('button', { name: '取消' })).toBeVisible()
    await expect(bar.getByRole('button', { name: '保存' })).toBeVisible()
    await expect(bar.getByRole('button', { name: '分配' })).not.toBeVisible()
    await expect(bar.getByRole('button', { name: '下一步' })).not.toBeVisible()
    await expect(bar.getByRole('button', { name: '提交' })).not.toBeVisible()
    await expect(bar.getByRole('button', { name: '返回列表' })).not.toBeVisible()
  })

  test('保存后显示返回列表和分配按钮', async ({ page }) => {
    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })
    const bar = await fillAndSave(page, 'E2E 保存流程')
    await expect(bar.getByRole('button', { name: '返回列表' })).toBeVisible({ timeout: 10000 })
    await expect(bar.getByRole('button', { name: '分配' })).toBeVisible({ timeout: 5000 })
    await expect(bar.getByRole('button', { name: '保存' })).not.toBeVisible()
  })

  test('分配后显示返回列表和下一步按钮', async ({ page }) => {
    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })
    const bar = await fillAndSave(page, 'E2E 分配流程')
    await assignTender(page, bar)
    await expect(bar.getByRole('button', { name: '返回列表' })).toBeVisible({ timeout: 10000 })
    await expect(bar.getByRole('button', { name: '下一步' })).toBeVisible({ timeout: 5000 })
    await expect(bar.getByRole('button', { name: '分配' })).not.toBeVisible()
    await expect(bar.getByRole('button', { name: '提交' })).not.toBeVisible()
  })

  test('下一步切换到评估表tab显示返回列表和提交按钮', async ({ page }) => {
    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })
    const bar = await fillAndSave(page, 'E2E 评估流程')
    await assignTender(page, bar)
    await expect(bar.getByRole('button', { name: '下一步' })).toBeVisible({ timeout: 10000 })
    await bar.getByRole('button', { name: '下一步' }).click()
    await page.waitForTimeout(800)
    await expect(page.locator('.el-tabs__item.is-active:has-text("项目评估表")')).toBeVisible({ timeout: 5000 })
    await expect(bar.getByRole('button', { name: '返回列表' })).toBeVisible()
    await expect(bar.getByRole('button', { name: '提交' })).toBeVisible()
    await expect(bar.getByRole('button', { name: '下一步' })).not.toBeVisible()
    await expect(bar.getByRole('button', { name: '分配' })).not.toBeVisible()
  })

  test('返回列表跳转到标讯列表页', async ({ page }) => {
    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })
    const bar = await fillAndSave(page, 'E2E 返回列表')
    await expect(bar.getByRole('button', { name: '返回列表' })).toBeVisible({ timeout: 10000 })
    await bar.getByRole('button', { name: '返回列表' }).click()
    await page.waitForURL('**/bidding', { timeout: 8000 })
    await expect(page.locator('.el-table').first()).toBeVisible({ timeout: 5000 })
  })
})
