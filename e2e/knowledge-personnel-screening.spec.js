import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18080'

/**
 * 工具：通过真实 API 快速创建测试人员（支持教育 + 证书）
 * 返回创建后的完整 DTO
 */
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

  test.beforeAll(async () => {
    adminSession = await ensureApiSession({
      username: `e2e_screen_admin_${Date.now()}`,
      role: 'bid_admin',
      fullName: 'E2E 筛选管理员'
    })

    const suffix = Date.now().toString().slice(-6)

    // 准备 4 个特征鲜明的人员（覆盖所有新筛选项）
    // 1. 男 + 本科 + 全日制 + 建造师 + 有效证书 + 2020入职
    const p1 = await createPersonViaApi(adminSession, {
      name: `张筛选_${suffix}`,
      employeeNumber: `SCR01${suffix}`,
      gender: '男',
      entryDate: '2020-03-15',
      departmentName: 'E2E测试部',
      status: 'ACTIVE'
    })
    await addEducationViaApi(adminSession, p1.data.id, {
      schoolName: '清华大学', startDate: '2016-09', endDate: '2020-06',
      highestEducation: '本科', studyForm: '全日制', major: '软件工程'
    })
    await addCertificateViaApi(adminSession, p1.data.id, {
      name: '一级建造师', certificateNumber: 'JZS2020', type: 'CONSTRUCTOR',
      issueDate: '2020-06', expiryDate: '2030-12-31'
    })
    testPersons.push(p1.data)

    // 2. 女 + 硕士 + 非全日制 + PMP + 即将到期（30天）
    const p2 = await createPersonViaApi(adminSession, {
      name: `李筛选_${suffix}`,
      employeeNumber: `SCR02${suffix}`,
      gender: '女',
      entryDate: '2021-06-01',
      departmentName: 'E2E测试部',
      status: 'ACTIVE'
    })
    await addEducationViaApi(adminSession, p2.data.id, {
      schoolName: '北京大学', startDate: '2018-09', endDate: '2021-06',
      highestEducation: '硕士', studyForm: '非全日制', major: '计算机科学'
    })
    const soon = new Date()
    soon.setDate(soon.getDate() + 25)
    await addCertificateViaApi(adminSession, p2.data.id, {
      name: 'PMP认证', certificateNumber: 'PMP2024', type: 'PMP',
      issueDate: '2024-01', expiryDate: soon.toISOString().slice(0, 10)
    })
    testPersons.push(p2.data)

    // 3. 男 + 博士 + 安全工程师 + 已过期证书
    const p3 = await createPersonViaApi(adminSession, {
      name: `王筛选_${suffix}`,
      employeeNumber: `SCR03${suffix}`,
      gender: '男',
      entryDate: '2018-11-20',
      departmentName: 'E2E测试部',
      status: 'ACTIVE'
    })
    await addEducationViaApi(adminSession, p3.data.id, {
      schoolName: '中国科学院', startDate: '2012-09', endDate: '2018-06',
      highestEducation: '博士', studyForm: '全日制', major: '信息安全'
    })
    const past = new Date()
    past.setDate(past.getDate() - 100)
    await addCertificateViaApi(adminSession, p3.data.id, {
      name: '安全工程师', certificateNumber: 'AQ2020', type: 'SECURITY',
      issueDate: '2020-06', expiryDate: past.toISOString().slice(0, 10)
    })
    testPersons.push(p3.data)

    // 4. 女 + 本科 + 网络教育 + 多个证书（用于复杂组合）
    const p4 = await createPersonViaApi(adminSession, {
      name: `赵筛选_${suffix}`,
      employeeNumber: `SCR04${suffix}`,
      gender: '女',
      entryDate: '2022-01-10',
      departmentName: 'E2E测试部',
      status: 'ACTIVE'
    })
    await addEducationViaApi(adminSession, p4.data.id, {
      schoolName: '上海交通大学', startDate: '2017-09', endDate: '2021-06',
      highestEducation: '本科', studyForm: '网络教育', major: '数据科学'
    })
    await addCertificateViaApi(adminSession, p4.data.id, {
      name: 'PMP认证', certificateNumber: 'PMP2025', type: 'PMP',
      issueDate: '2025-01', expiryDate: '2026-01-01'
    })
    testPersons.push(p4.data)
  })

  test('多维度筛选卡片 - 性别 + 学历多选 + 持有证书名称 组合生效', async ({ page }) => {
    await injectSession(page, adminSession)
    await page.goto('/knowledge/personnel')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(600) // 等待初始列表

    // 应用筛选：女 + 硕士 + 持有 "PMP"
    await page.getByLabel('性别').click()
    await page.getByRole('option', { name: '女' }).click()

    await page.getByLabel('最高学历').click()
    await page.getByRole('option', { name: '硕士' }).click()
    await page.keyboard.press('Escape')

    await page.getByLabel('持有证书').fill('PMP')
    await page.waitForTimeout(450) // debounce 等待

    const rows = page.locator('.el-table__body tr')
    await expect(rows).toHaveCount(1, { timeout: 8000 })
    await expect(rows.first()).toContainText(`SCR02`) // 李筛选

    // 截图断言（关键状态）
    await expect(page.locator('.filter-card')).toHaveScreenshot('personnel-filter-female-master-pmp.png', {
      threshold: 0.2
    })
  })

  test('证书状态筛选（即将到期）+ 复杂组合', async ({ page }) => {
    await injectSession(page, adminSession)
    await page.goto('/knowledge/personnel')
    await page.waitForLoadState('networkidle')

    // 证书状态：即将到期
    await page.getByLabel('证书状态').click()
    await page.getByRole('option', { name: /即将到期/ }).click()
    await page.keyboard.press('Escape')
    await page.waitForTimeout(400)

    const rows = page.locator('.el-table__body tr')
    await expect(rows.first()).toContainText(/SCR02|李筛选/, { timeout: 8000 })

    // 再加性别女 + 学习形式非全日制 验证复杂 AND
    await page.getByLabel('性别').click()
    await page.getByRole('option', { name: '女' }).click()

    await page.getByLabel('学习形式').click()
    await page.getByRole('option', { name: '非全日制' }).click()
    await page.keyboard.press('Escape')
    await page.waitForTimeout(400)

    await expect(rows).toHaveCount(1)
    await expect(rows.first()).toContainText('SCR02')

    await expect(page).toHaveScreenshot('personnel-filter-expiring-female-nonfull.png', {
      fullPage: false,
      threshold: 0.25
    })
  })

  test('重置按钮清空所有条件并恢复完整列表', async ({ page }) => {
    await injectSession(page, adminSession)
    await page.goto('/knowledge/personnel')
    await page.waitForLoadState('networkidle')

    // 先应用一个筛选
    await page.getByLabel('持有证书').fill('建造师')
    await page.waitForTimeout(400)
    await expect(page.locator('.el-table__body tr')).toHaveCount(1)

    // 重置
    await page.getByRole('button', { name: '重置' }).click()
    await page.waitForTimeout(600)

    // 应该至少看到 4 个我们创建的测试人员（可能更多历史数据）
    const count = await page.locator('.el-table__body tr').count()
    expect(count).toBeGreaterThanOrEqual(4)
  })
})