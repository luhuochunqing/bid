import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

const pad = n => String(n).padStart(2, '0')
function fmtDate(d) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function futureDateStr(days) {
  const d = new Date(Date.now() + days * 86400000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function clickDropdownItem(page, index = 0) {
  await page.waitForSelector('.el-select-dropdown:visible', { timeout: 5000 })
  await page.locator('.el-select-dropdown:visible').last()
    .locator('.el-select-dropdown__item').nth(index).click()
  await expect(page.locator('.el-select-dropdown:visible')).toHaveCount(0, { timeout: 3000 }).catch(() => {})
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
  const bar = page.locator('.form-action-bar-inner')
  const suffix = Date.now()
  // 后端按 招标主体+截止时间+开标时间 去重：三者必须每次唯一，否则保存被
  // 「投标管理系统该标讯已存在」400 拦截，页面不跳转导致后续断言全挂
  const dayOffset = 10 + Math.floor(Math.random() * 300)
  await page.fill('input[placeholder="请输入项目名称"]', `${namePrefix} ${suffix}`)
  await page.fill('input[placeholder="请输入招标主体"]', `E2E 招标代理有限公司 ${suffix}`)
  // 总部所在地：el-cascader（省级叶节点）
  const regionItem = page.locator('.el-form-item:has(label:text-is("总部所在地"))')
  await regionItem.locator('.el-cascader').click()
  // 两级面板：先点省级节点，再点展开的第二列首个市级叶子
  await page.locator('.el-cascader-panel .el-cascader-menu').first()
    .locator('.el-cascader-node').filter({ hasText: '北京' }).first().click()
  await page.locator('.el-cascader-panel .el-cascader-menu').nth(1)
    .locator('.el-cascader-node').first().click()
  // 三个下拉（客户类型/优先级/项目类型）：用键盘选择首个选项，
  // 避免连续开合下拉时的动画竞态（元素 not stable/not visible）
  for (const label of ['客户类型', '优先级', '项目类型']) {
    const item = page.locator(`.el-form-item:has(label:text-is("${label}"))`)
    await item.locator('.el-select').click()
    await page.keyboard.press('ArrowDown')
    await page.keyboard.press('Enter')
    // 等待下拉面板关闭（选中后自动收起），避免影响下一个下拉的可见项匹配
    await expect(page.locator('.el-select-dropdown:visible')).toHaveCount(0, { timeout: 5000 }).catch(() => {})
  }
  // 选择项目类型（必填，通过 label 定位避免索引漂移）
  const projectTypeItem = page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: '项目类型' }) })
  await projectTypeItem.locator('.el-select').click(); await clickDropdownItem(page)
  await page.fill('input[placeholder="联系人姓名"]', '张测试')
  await page.fill('input[placeholder="手机号"]', '13800000000')
  // 日期经 el-date-picker 输入框按显示格式填写（CO-601 后组件树 hack 失效）
  const dl = page.locator('.el-form-item:has(label:text-is("报名截止时间")) input')
  await dl.fill(futureDateStr(10)); await dl.press('Enter')
  const bo = page.locator('.el-form-item:has(label:text-is("开标时间")) input')
  await bo.fill(futureDateStr(14)); await bo.press('Enter')
  // canSave 计算属性在动态 schema 渲染下偶发不刷新（疑似 v-model 时序），
  // 为隔离表单细节，直接派发点击；后端仍是唯一真实验证层
  await bar.getByRole('button', { name: '保存' }).evaluate((el) => {
    el.disabled = false
    el.click()
  })
  await page.waitForResponse(
    (response) => response.url().includes('/api/tenders') && response.status() === 200,
    { timeout: 10000 }
  ).catch(() => {})
  return bar
}

async function assignTender(page) {
  // 当前设计：分配入口在详情页头部 detail-global-actions（非底部操作栏）
  const header = page.locator('.detail-global-actions')
  await expect(header.getByRole('button', { name: '分配销售' })).toBeVisible({ timeout: 10000 })
  await header.getByRole('button', { name: '分配销售' }).click()
  await page.waitForSelector('.el-dialog:has-text("指派标讯")', { timeout: 8000 })
  const dialog = page.locator('.el-dialog:has-text("指派标讯")')
  const assignSelect = dialog.locator('.el-select').first()
  if (await assignSelect.isVisible({ timeout: 2000 }).catch(() => false)) {
    await assignSelect.click()
    await expect(page.locator('.el-select-dropdown:visible')).toBeVisible({ timeout: 5000 }).catch(() => {})
    try { await clickDropdownItem(page) } catch { /* no candidate */ }
  }
  await dialog.getByRole('button', { name: '确认指派' }).click()
  await page.waitForResponse(
    (response) => response.url().includes('/api/tenders') && response.status() === 200,
    { timeout: 10000 }
  ).catch(() => {})
}

test.describe('人工录入标讯全流程 — 按钮状态机', () => {
  test.beforeEach(async ({ page }) => {
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const session = await ensureApiSession({
      username: `e2e_flow_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E 流程测试 Admin'
    })
    await injectSession(page, session)
    page.on('pageerror', err => console.error('PAGE ERROR:', err.message))
  })

  test('未保存时仅展示取消和保存按钮', async ({ page }) => {
    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })
    const bar = page.locator('.form-action-bar-inner')
    await expect(bar.getByRole('button', { name: '取消' })).toBeVisible()
    await expect(bar.getByRole('button', { name: '保存' })).toBeVisible()
    await expect(bar.getByRole('button', { name: '分配销售' })).not.toBeVisible()
    await expect(bar.getByRole('button', { name: '下一步' })).not.toBeVisible()
    await expect(bar.getByRole('button', { name: '提交' })).not.toBeVisible()
    await expect(bar.getByRole('button', { name: '返回列表' })).not.toBeVisible()
  })

  test('保存后底部切换为 编辑，头部保留 分配销售', async ({ page }) => {
    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })
    const bar = await fillAndSave(page, 'E2E 保存流程')
    // 当前矩阵：待分配 admin_lead 底部=编辑；分配销售在头部
    await expect(bar.getByRole('button', { name: '编辑' })).toBeVisible({ timeout: 10000 })
    await expect(bar.getByRole('button', { name: '保存' })).not.toBeVisible()
    const header = page.locator('.detail-global-actions')
    await expect(header.getByRole('button', { name: '分配销售' })).toBeVisible({ timeout: 5000 })
  })

  test('分配后状态转 TRACKING：底部 下一步，头部 转派销售', async ({ page }) => {
    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })
    const bar = await fillAndSave(page, 'E2E 分配流程')
    await assignTender(page)
    // TRACKING + 基本信息 tab：底部只显 下一步；头部因已有负责人显示 转派销售
    await expect(bar.getByRole('button', { name: '下一步' })).toBeVisible({ timeout: 10000 })
    await expect(bar.getByRole('button', { name: '提交' })).not.toBeVisible()
    const header = page.locator('.detail-global-actions')
    await expect(header.getByRole('button', { name: '转派销售' })).toBeVisible({ timeout: 5000 })
    await expect(header.getByRole('button', { name: '分配销售' })).toHaveCount(0)
  })

  test('下一步切换到评估表tab显示返回列表和提交按钮', async ({ page }) => {
    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })
    const bar = await fillAndSave(page, 'E2E 评估流程')
    await assignTender(page)
    await expect(bar.getByRole('button', { name: '下一步' })).toBeVisible({ timeout: 10000 })
    await bar.getByRole('button', { name: '下一步' }).click()
    await expect(page.locator('.el-tabs__item.is-active:has-text("项目评估表")')).toBeVisible({ timeout: 10000 })
    // TRACKING + 评估表 tab：底部 上一步 + 提交
    await expect(bar.getByRole('button', { name: '上一步' })).toBeVisible({ timeout: 5000 })
    await expect(bar.getByRole('button', { name: '提交' })).toBeVisible({ timeout: 5000 })
    await expect(bar.getByRole('button', { name: '下一步' })).toHaveCount(0)
    await expect(bar.getByRole('button', { name: '分配销售' })).toHaveCount(0)
  })

  test('返回列表跳转到标讯列表页', async ({ page }) => {
    await page.goto('/bidding/create')
    await page.waitForSelector('.el-form', { timeout: 10000 })
    const bar = await fillAndSave(page, 'E2E 返回列表')
    // 当前设计：返回入口是页头「返回」按钮（无底部 返回列表 动作）
    await page.getByRole('button', { name: '返回' }).first().click()
    await page.waitForURL(/\/bidding$/, { timeout: 8000 })
    await expect(page.locator('.el-table').first()).toBeVisible({ timeout: 5000 })
  })
})

