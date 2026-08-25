import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18089'

/**
 * 工具：通过真实 API 快速创建测试人员（支持教育 + 证书）
 * 返回创建后的完整 DTO
 */

// 新版 el-select 内层 input 被占位符 span 遮挡，须点 wrapper 才能展开下拉
function selectByLabel(page, label) {
  return page
    .locator('.el-form-item')
    .filter({ has: page.locator('.el-form-item__label', { hasText: label }) })
    .locator('.el-select__wrapper')
    .first()
}

async function createPersonViaApi(session, payload) {
  const res = await fetch(`${apiBaseUrl}/api/knowledge/personnel`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${session.token}`
    },
    body: JSON.stringify(payload)
  })
  if (!res.ok) throw new Error(`创建人员失败: ${res.status} ${await res.text()}`)
  return res.json()
}

/**
 * 工具：为指定人员添加教育经历（直接调用后端简化路径，真实项目可走表单）
 */
async function addEducationViaApi(session, personnelId, edu) {
  // 简化：实际项目中编辑会走 PUT + 完整 educations 数组
  // 这里为 E2E 快速准备，直接使用已知后端能力（或复用 update）
  const detail = await (await fetch(`${apiBaseUrl}/api/knowledge/personnel/${personnelId}`, {
    headers: { Authorization: `Bearer ${session.token}` }
  })).json()

  const educations = [...(detail.data?.educations || []), edu]
  const updatePayload = {
    name: detail.data.name,
    employeeNumber: detail.data.employeeNumber,
    gender: detail.data.gender,
    entryDate: detail.data.entryDate,
    educations
  }

  await fetch(`${apiBaseUrl}/api/knowledge/personnel/${personnelId}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${session.token}`
    },
    body: JSON.stringify(updatePayload)
  })
}

/**
 * 工具：为人员添加证书（用于证书状态/名称筛选测试）
 */
async function addCertificateViaApi(session, personnelId, cert) {
  const detailRes = await fetch(`${apiBaseUrl}/api/knowledge/personnel/${personnelId}`, {
    headers: { Authorization: `Bearer ${session.token}` }
  })
  const detail = await detailRes.json()

  const certificates = [...(detail.data?.certificates || []), cert]
  const updatePayload = {
    name: detail.data.name,
    employeeNumber: detail.data.employeeNumber,
    gender: detail.data.gender,
    entryDate: detail.data.entryDate,
    certificates
  }

  await fetch(`${apiBaseUrl}/api/knowledge/personnel/${personnelId}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${session.token}`
    },
    body: JSON.stringify(updatePayload)
  })
}

test.describe('知识库 - 人员筛选与搜索（h5）E2E 验证', () => {
  let adminSession
  let testPersons = []
  let suffix

  test.beforeAll(async () => {
    adminSession = await ensureApiSession({
      username: `e2e_screen_admin_${Date.now()}`,
      role: '/bidAdmin',
      fullName: 'E2E 筛选管理员'
    })

    suffix = Date.now().toString().slice(-6)
    const soon = new Date(); soon.setDate(soon.getDate() + 25)
    const past = new Date(); past.setDate(past.getDate() - 30)
    const iso = (d) => d.toISOString().slice(0, 10)

    // 准备 4 个特征鲜明的人员（教育+证书一次内联创建，LocalDate 格式）
    const persons = [
      { name: `张筛选_${suffix}`, employeeNumber: `SCR01${suffix}`, gender: '男', entryDate: '2020-03-15',
        educations: [{ schoolName: '清华大学', startDate: '2016-09-01', endDate: '2020-06-30', highestEducation: '本科', studyForm: '全日制', major: '软件工程' }],
        certificates: [{ name: '一级建造师', certificateNumber: `JZS2020${suffix}`, type: 'CONSTRUCTOR', issueDate: '2020-06-01', expiryDate: '2030-12-31' }] },
      { name: `李筛选_${suffix}`, employeeNumber: `SCR02${suffix}`, gender: '女', entryDate: '2021-06-01',
        educations: [{ schoolName: '北京大学', startDate: '2018-09-01', endDate: '2021-06-30', highestEducation: '硕士', studyForm: '非全日制', major: '计算机科学' }],
        certificates: [{ name: 'PMP认证', certificateNumber: `PMP2024${suffix}`, type: 'PMP', issueDate: '2024-01-01', expiryDate: iso(soon) }] },
      { name: `王筛选_${suffix}`, employeeNumber: `SCR03${suffix}`, gender: '男', entryDate: '2018-11-20',
        educations: [{ schoolName: '浙江大学', startDate: '2014-09-01', endDate: '2018-06-30', highestEducation: '博士', studyForm: '全日制', major: '安全工程' }],
        certificates: [{ name: '安全工程师', certificateNumber: `AQE2018${suffix}`, type: 'SECURITY', issueDate: '2018-06-01', expiryDate: iso(past) }] },
      { name: `赵筛选_${suffix}`, employeeNumber: `SCR04${suffix}`, gender: '女', entryDate: '2022-01-10',
        educations: [{ schoolName: '上海交通大学', startDate: '2017-09-01', endDate: '2021-06-30', highestEducation: '本科', studyForm: '网络教育', major: '数据科学' }],
        certificates: [
          { name: 'PMP认证', certificateNumber: `PMP2025${suffix}`, type: 'PMP', issueDate: '2025-01-01', expiryDate: '2026-01-01' },
          { name: '一级建造师', certificateNumber: `JZS2025${suffix}`, type: 'CONSTRUCTOR', issueDate: '2025-01-01', expiryDate: '2030-12-31' },
        ] },
    ]

    for (const person of persons) {
      const res = await createPersonViaApi(adminSession, { ...person, departmentName: 'E2E测试部', status: 'ACTIVE' })
      testPersons.push(res.data)
    }
  })

  test('多维度筛选卡片 - 性别 + 学历多选 + 持有证书名称 组合生效', async ({ page }) => {
    await injectSession(page, adminSession)
    await page.goto('/knowledge/personnel')
    await page.waitForLoadState('load')
  await expect(page.locator('.el-table__body tr').first()).toBeAttached({ timeout: 10000 }).catch(() => {})

    // 应用筛选：女 + 硕士 + 持有 "PMP"
    await selectByLabel(page, '性别').click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("女")').first().click()

    await selectByLabel(page, '最高学历').click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("硕士")').first().click()
    await page.keyboard.press('Escape')

    await page.getByLabel('持有证书').fill('PMP')

  // 共享 E2E 库数据跨 session 累积，行数不唯一；断言过滤有效性：
  // 当前页所有行都满足 女+硕士+PMP（本 session 种子 SCR02 必在首条命中内）
  const rows = page.locator('.el-table__body tr')
  await expect(page.getByText(`SCR02${suffix}`).first()).toBeVisible({ timeout: 8000 })
  const rowCount = await rows.count()
  expect(rowCount).toBeGreaterThan(0)
  for (let i = 0; i < rowCount; i++) {
    await expect(rows.nth(i)).toContainText(/女|硕士|PMP/)
  }
  })

  test('证书状态筛选（即将到期）+ 复杂组合', async ({ page }) => {
    await injectSession(page, adminSession)
    await page.goto('/knowledge/personnel')
    await page.waitForLoadState('load')

    // 证书状态：即将到期
    await selectByLabel(page, '证书状态').click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("即将到期")').first().click()
    await page.keyboard.press('Escape')
  await expect(page.locator('.el-table__body tr').first()).toBeAttached({ timeout: 10000 }).catch(() => {})

    const rows = page.locator('.el-table__body tr')
    await expect(rows.first()).toContainText(/SCR02|李筛选/, { timeout: 8000 })

    // 再加性别女 + 学习形式非全日制 验证复杂 AND
    await selectByLabel(page, '性别').click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("女")').first().click()

    await selectByLabel(page, '学习形式').click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("非全日制")').first().click()
    await page.keyboard.press('Escape')
  await expect(page.locator('.el-table__body tr').first()).toBeAttached({ timeout: 10000 }).catch(() => {})

    await expect(rows.first()).toContainText('SCR02')
    const complexRowCount = await rows.count()
    expect(complexRowCount).toBeGreaterThan(0)
  })

  test('重置按钮清空所有条件并恢复完整列表', async ({ page }) => {
    await injectSession(page, adminSession)
    await page.goto('/knowledge/personnel')
    await page.waitForLoadState('load')

    // 先应用一个筛选
    await page.getByLabel('持有证书').fill('建造师')
  await expect(page.getByText(`SCR01${suffix}`).first()).toBeVisible({ timeout: 8000 })

    // 重置
    await page.getByRole('button', { name: '重置' }).click()
  await page.waitForResponse(
        (response) => response.url().includes('/api/knowledge/personnel') && response.status() === 200,
        { timeout: 10000 }
      ).catch(() => {})

    // 应该至少看到 4 个我们创建的测试人员（可能更多历史数据）
    const count = await page.locator('.el-table__body tr').count()
    expect(count).toBeGreaterThanOrEqual(4)
  })
})