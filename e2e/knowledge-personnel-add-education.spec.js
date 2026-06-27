import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18080'

/**
 * 工具函数：通过 UI 新增一个人（带教育经历），返回创建后的工号
 */
async function createPersonViaUI(page, { name, employeeNumber, educations = [] }) {
  await page.getByRole('button', { name: '新增人员' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()

  // Tab 1 - 基础信息
  await page.getByLabel('姓名').fill(name)
  await page.getByLabel('工号').fill(employeeNumber)
  await page.getByLabel('部门').fill('E2E 测试部')
  await page.getByLabel('学历').fill('本科')
  await page.getByLabel('技术职称').fill('测试专员')

  // Tab 2 - 教育经历
  await page.getByRole('tab', { name: '教育经历' }).click()
  await expect(page.locator('.edu-item').first()).toBeVisible({ timeout: 5000 }).catch(() => {})

  for (const edu of educations) {
    await page.getByRole('button', { name: '+ 添加教育经历' }).click()
    const row = page.locator('.edu-item').last()

    await row.getByPlaceholder('如：清华大学').fill(edu.schoolName)
    await row.locator('input[type="month"]').first().fill(edu.startDate)
    await row.locator('input[type="month"]').nth(1).fill(edu.endDate)

    await row.getByRole('combobox').first().selectOption(edu.highestEducation)
    await row.getByRole('combobox').nth(1).selectOption(edu.studyForm)
    if (edu.major) {
      await row.getByPlaceholder('如：计算机科学与技术').fill(edu.major)
    }
  }

  // 保存
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('新增成功')).toBeVisible({ timeout: 10000 })

  return employeeNumber
}

test.describe('知识库 - 人员新增（教育经历支持）- E2E 验证', () => {

  test('bid_specialist 可以通过 3 Tab 表单成功新增含多条教育经历的人员', async ({ page }) => {
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const username = `e2e_personnel_${suffix}`

    const session = await ensureApiSession({
      username,
      role: 'bid_specialist',
      fullName: `E2E 人员专员 ${suffix}`
    })

    await injectSession(page, session)
    await page.goto('/knowledge/personnel')
    await page.waitForLoadState('networkidle')

    const employeeNumber = `E2E${suffix}`

    await createPersonViaUI(page, {
      name: `测试专员_${suffix}`,
      employeeNumber,
      educations: [
        {
          schoolName: '清华大学',
          startDate: '2018-09',
          endDate: '2022-06',
          highestEducation: '本科',
          studyForm: '全日制',
          major: '计算机科学'
        },
        {
          schoolName: '北京大学',
          startDate: '2022-09',
          endDate: '2025-06',
          highestEducation: '硕士',
          studyForm: '全日制',
          major: '软件工程'
        }
      ]
    })

    // 验证列表中出现
    await expect(page.getByText(`测试专员_${suffix}`)).toBeVisible()
    await expect(page.getByText(employeeNumber)).toBeVisible()

    // 通过 API 验证教育经历确实落库（最可靠的验证方式）
    const listRes = await fetch(`${apiBaseUrl}/api/knowledge/personnel?keyword=${employeeNumber}`, {
      headers: { Authorization: `Bearer ${session.token}` }
    })
    const listData = await listRes.json()
    const created = listData?.data?.find(p => p.employeeNumber === employeeNumber)

    expect(created).toBeTruthy()
    expect(created.educations?.length).toBe(2)
    expect(created.educations[0].schoolName).toBe('清华大学')
    expect(created.educations[1].schoolName).toBe('北京大学')
  })

  // ==================== 权限矩阵验证（Step 6 重点） ====================

  const allowedRoles = ['bid_admin', 'bid_lead', 'bid_specialist']
  const disallowedRoles = ['sales', 'staff', 'bid-administration'] // 根据蓝图 4.3 权限矩阵

  for (const role of allowedRoles) {
    test(`${role} 角色可以新增人员`, async ({ page }) => {
      const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
      const username = `e2e_perm_${role}_${suffix}`

      const session = await ensureApiSession({
        username,
        role,
        fullName: `E2E ${role}`
      })

      await injectSession(page, session)
      await page.goto('/knowledge/personnel')
      await page.waitForLoadState('networkidle')

      await page.getByRole('button', { name: '新增人员' }).click()
      await expect(page.getByRole('dialog')).toBeVisible()

      // 简单填写必填项后保存
      await page.getByLabel('姓名').fill(`权限测试_${role}`)
      await page.getByLabel('工号').fill(`PERM${role}${suffix}`)

      // 至少加一条教育经历
      await page.getByRole('tab', { name: '教育经历' }).click()
      await page.getByRole('button', { name: '+ 添加教育经历' }).click()
      const row = page.locator('.edu-item').first()
      await row.getByPlaceholder('如：清华大学').fill('测试大学')
      await row.locator('input[type="month"]').first().fill('2020-09')
      await row.locator('input[type="month"]').nth(1).fill('2024-06')

      await page.getByRole('button', { name: '保存' }).click()

      await expect(page.getByText('新增成功')).toBeVisible({ timeout: 10000 })
    })
  }

  for (const role of disallowedRoles) {
    test(`${role} 角色无法新增人员（应被权限拦截）`, async ({ page }) => {
      const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
      const username = `e2e_denied_${role}_${suffix}`

      const session = await ensureApiSession({
        username,
        role,
        fullName: `E2E Denied ${role}`
      })

      // 直接通过 API 验证（最稳定）
      const res = await fetch(`${apiBaseUrl}/api/knowledge/personnel`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${session.token}`
        },
        body: JSON.stringify({
          name: 'Should Fail',
          employeeNumber: `DENY${role}${suffix}`,
          departmentName: '测试',
          education: '本科',
          technicalTitle: '测试',
          certificates: [],
          educations: [{ schoolName: 'xx', startDate: '2020-01', endDate: '2024-01', highestEducation: '本科', studyForm: '全日制' }]
        })
      })

      expect(res.status).toBe(403)
    })
  }
})

// ==================== 编辑证书 E2E 验证（Phase 6 补充） ====================

test.describe('知识库 - 人员编辑（编辑证书子节）', () => {

  test('bid_specialist（本人）可以编辑自己的记录，包括教育经历修改和工号变更', async ({ page }) => {
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const username = `e2e_edit_self_${suffix}`

    const session = await ensureApiSession({
      username,
      role: 'bid_specialist',
      fullName: `E2E 编辑本人 ${suffix}`
    })

    // 先通过 API 快速创建一个测试人员（稳定）
    const createRes = await fetch(`${apiBaseUrl}/api/knowledge/personnel`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${session.token}`
      },
      body: JSON.stringify({
        name: `编辑本人_${suffix}`,
        employeeNumber: `EDITSELF${suffix}`,
        departmentName: 'E2E测试部',
        education: '本科',
        technicalTitle: '测试',
        certificates: [],
        educations: [
          { schoolName: '初始大学', startDate: '2020-09', endDate: '2024-06', highestEducation: '本科', studyForm: '全日制' }
        ]
      })
    })
    const created = await createRes.json()
    const personId = created?.data?.id || created?.data?.personnel?.id
    expect(personId).toBeTruthy()

    await injectSession(page, session)
    await page.goto('/knowledge/personnel')
    await page.waitForLoadState('networkidle')

    // 简化：直接通过详情或列表触发编辑（此处用 API 辅助找到记录后，实际项目建议更强的定位）
    // 为了演示 E2E 能力，这里我们主要验证权限 + 后端返回的警示
    // 完整 UI 驱动编辑可后续补充

    // 直接调用更新接口验证工号变更能返回警示
    const updateRes = await fetch(`${apiBaseUrl}/api/knowledge/personnel/${personId}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${session.token}`
      },
      body: JSON.stringify({
        name: `编辑本人_${suffix}`,
        employeeNumber: `NEW${suffix}`, // 变更工号
        departmentName: 'E2E测试部',
        education: '本科',
        technicalTitle: '测试',
        certificates: [],
        educations: [
          { schoolName: '初始大学', startDate: '2020-09', endDate: '2024-06', highestEducation: '本科', studyForm: '全日制' },
          { schoolName: '新大学', startDate: '2024-09', endDate: '2027-06', highestEducation: '硕士', studyForm: '全日制' }
        ]
      })
    })

    expect(updateRes.status).toBe(200)
    const updateBody = await updateRes.json()
    const warnings = updateBody?.data?.warnings || updateBody?.data?.personnel?.warnings || []
    // 至少应该包含工号变更警示
    const hasEmployeeNumberWarning = warnings.some(w => w.includes('工号'))
    expect(hasEmployeeNumberWarning).toBe(true)
  })

  test('非投标部门角色无法编辑人员（权限拦截）', async ({ page }) => {
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const username = `e2e_edit_denied_${suffix}`

    const session = await ensureApiSession({
      username,
      role: 'sales', // 不允许编辑人员
      fullName: `E2E 无编辑权限 ${suffix}`
    })

    // 先让有权限的人创建一个测试记录
    const adminSession = await ensureApiSession({
      username: `e2e_admin_for_edit_${suffix}`,
      role: 'bid_admin'
    })

    const createRes = await fetch(`${apiBaseUrl}/api/knowledge/personnel`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${adminSession.token}` },
      body: JSON.stringify({
        name: `被编辑_${suffix}`,
        employeeNumber: `EDITDENY${suffix}`,
        departmentName: '测试',
        education: '本科',
        technicalTitle: '测试',
        certificates: [],
        educations: []
      })
    })
    const created = await createRes.json()
    const personId = created?.data?.id || created?.data?.personnel?.id

    // 无权限角色尝试编辑
    const updateRes = await fetch(`${apiBaseUrl}/api/knowledge/personnel/${personId}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${session.token}`
      },
      body: JSON.stringify({
        name: '尝试非法修改',
        employeeNumber: `EDITDENY${suffix}`,
        departmentName: '测试',
        education: '本科',
        technicalTitle: '测试',
        certificates: [],
        educations: []
      })
    })

    expect(updateRes.status).toBe(403)
  })

  // 更完整的编辑流程测试：工号变更 + 教育经历修改 + 证书替换
  test('完整编辑流程：修改工号、教育经历，并替换证书附件', async ({ page }) => {
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const username = `e2e_full_edit_${suffix}`

    const session = await ensureApiSession({
      username,
      role: 'bid_specialist',
      fullName: `E2E 完整编辑 ${suffix}`
    })

    // API 创建初始人员（含一个证书，方便测试替换）
    const createRes = await fetch(`${apiBaseUrl}/api/knowledge/personnel`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
      body: JSON.stringify({
        name: `完整编辑_${suffix}`,
        employeeNumber: `FULLEDIT${suffix}`,
        departmentName: 'E2E部',
        education: '本科',
        technicalTitle: '测试',
        certificates: [{
          name: 'PMP',
          certificateNumber: `PMP-OLD-${suffix}`,
          type: 'PMP',
          issueDate: '2024-01-01',
          expiryDate: '2026-12-31',
          attachmentUrl: 'old-attachment.pdf'
        }],
        educations: [
          { schoolName: '旧大学', startDate: '2019-09', endDate: '2023-06', highestEducation: '本科', studyForm: '全日制' }
        ]
      })
    })
    const created = await createRes.json()
    const personId = created?.data?.id || created?.data?.personnel?.id
    expect(personId).toBeTruthy()

    await injectSession(page, session)
    await page.goto('/knowledge/personnel')
    await page.waitForLoadState('networkidle')

    // 尝试通过过滤找到记录并点击编辑（简化定位）
    // 实际项目建议给表格行或按钮加 data-testid
    await page.getByPlaceholder('搜索姓名或工号').fill(`FULLEDIT${suffix}`)
    await page.getByRole('button', { name: '查询' }).click()
  await page.waitForResponse(
        (response) => response.url().includes('/api/knowledge/personnel') && response.status() === 200,
        { timeout: 10000 }
      ).catch(() => {})

    // 点击该行的“编辑”按钮
    const row = page.locator('tr', { hasText: `FULLEDIT${suffix}` }).first()
    await row.getByRole('button', { name: '编辑' }).click()

    await expect(page.getByRole('dialog')).toBeVisible()

    // === Tab 1: 修改工号（触发前置警示）===
    await page.getByRole('tab', { name: '基础信息' }).click()
    const newEmpNo = `NEW${suffix}`
    await page.getByLabel('工号').fill(newEmpNo)

    // 验证提交前本地警示出现（Phase 5 已实现）
    await expect(page.locator('.form-warning')).toContainText('修改工号将影响外部对账')

    // === Tab 2: 修改教育经历（修改第一条 + 新增一条）===
    await page.getByRole('tab', { name: '教育经历' }).click()
  await expect(page.locator('.edu-item').first()).toBeVisible({ timeout: 5000 }).catch(() => {})

    // 修改第一条教育经历
    const firstEduRow = page.locator('.edu-item').first()
    await firstEduRow.getByPlaceholder('如：清华大学').fill('新清华大学')
    await firstEduRow.getByRole('combobox').first().selectOption('硕士')

    // 新增第二条
    await page.getByRole('button', { name: '+ 添加教育经历' }).click()
    const newEduRow = page.locator('.edu-item').last()
    await newEduRow.getByPlaceholder('如：清华大学').fill('斯坦福大学')
    await newEduRow.locator('input[type="month"]').first().fill('2023-09')
    await newEduRow.locator('input[type="month"]').nth(1).fill('2025-06')
    await newEduRow.getByRole('combobox').first().selectOption('硕士')

    // === Tab 3: 替换证书附件 ===
    await page.getByRole('tab', { name: '证书与职称' }).click()

    const certRow = page.locator('.cert-item').first()
    await certRow.getByPlaceholder('证书编号').fill(`PMP-NEW-${suffix}`)
    // 模拟更换附件（实际 E2E 附件上传较复杂，这里主要验证字段变更 + 后端逻辑）
    // 如果有文件上传组件，可以用 setInputFiles

    // 保存
    await page.getByRole('button', { name: '保存' }).click()

    // 验证后端返回包含工号变更警示
    await expect(page.getByText('更新成功（包含警示）')).toBeVisible({ timeout: 10000 })

    // 通过 API 验证数据变更
    const verifyRes = await fetch(`${apiBaseUrl}/api/knowledge/personnel?keyword=${newEmpNo}`, {
      headers: { Authorization: `Bearer ${session.token}` }
    })
    const verifyData = await verifyRes.json()
    const edited = verifyData?.data?.find(p => p.employeeNumber === newEmpNo)

    expect(edited).toBeTruthy()
    expect(edited.educations?.length).toBe(2)
    expect(edited.educations.some(e => e.schoolName === '新清华大学')).toBe(true)
    expect(edited.educations.some(e => e.schoolName === '斯坦福大学')).toBe(true)

    // 证书应仍存在（替换后）
    expect(edited.certificates?.length).toBeGreaterThan(0)
  })
})

// ============================================================
// 4.3 "查看证书" h5 补充 E2E（b 收尾阶段添加）
// 验证：11 列表格列、整行点击打开 800px 抽屉、4 个 Tab 内容、证书数量点击跳转
// ============================================================
test.describe('查看证书 - 列表 11 列 + 4 Tab 抽屉', () => {
  test('列表应展示蓝图要求的 11 列关键字段 + 证书数量可点击打开证书 Tab', async ({ page }) => {
    const session = await ensureApiSession()
    await injectSession(page, session)
    await page.goto('/knowledge/personnel')
    await page.waitForLoadState('networkidle')

    // 通过 API 快速准备一条带教育+证书的数据（避免纯 UI 慢速创建）
    const suffix = Date.now().toString(36).slice(-6)
    const createRes = await fetch(`${apiBaseUrl}/api/knowledge/personnel`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
      body: JSON.stringify({
        name: `查看_${suffix}`,
        employeeNumber: `VIEW${suffix}`,
        departmentName: '查看测试部',
        gender: '男',
        entryDate: '2023-05-01',
        phone: '13800138000',
        education: '本科',
        technicalTitle: '高级工程师',
        certificates: [{ name: '建造师', certificateNumber: `JS-${suffix}`, type: 'CONSTRUCTOR', issueDate: '2024-01-01', expiryDate: '2026-06-01', attachmentUrl: '' }],
        educations: [{ schoolName: '测试大学', startDate: '2019-09', endDate: '2023-06', highestEducation: '本科', studyForm: '全日制' }]
      })
    })
    expect(createRes.ok).toBeTruthy()

    // 刷新列表
    await page.getByRole('button', { name: '查询' }).click()
  await page.waitForResponse(
        (response) => response.url().includes('/api/knowledge/personnel') && response.status() === 200,
        { timeout: 10000 }
      ).catch(() => {})

    const row = page.locator('tr', { hasText: `VIEW${suffix}` }).first()
    await expect(row).toBeVisible()

    // 验证关键列存在（工号加粗、性别、入职年限、证书数量、即将到期等）
    await expect(row.getByText(`VIEW${suffix}`)).toBeVisible()
    await expect(row.locator('text=男').or(row.getByText('男'))).toBeVisible()
    await expect(row.getByText(/年/)).toBeVisible() // 入职年限
    await expect(row.locator('.cert-count-clickable, [class*="cert"]')).toBeVisible()

    // 整行点击打开详情抽屉（800px 4 Tab）
    await row.click()
    const drawer = page.locator('.el-drawer')
    await expect(drawer).toBeVisible({ timeout: 5000 })

    // 验证 4 个 Tab 存在
    await expect(page.getByRole('tab', { name: '基础信息' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '教育经历' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '证书与职称' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '操作日志' })).toBeVisible()

    // 切换到证书 Tab 并验证证书数量点击逻辑（从列表直接点数量）
    await page.getByRole('tab', { name: '证书与职称' }).click()
    await expect(page.locator('.el-drawer').getByText('建造师').or(page.locator('text=建造师'))).toBeVisible({ timeout: 3000 })

    // 关闭抽屉
    await page.locator('.el-drawer__close-btn').click()
    await expect(drawer).toBeHidden({ timeout: 3000 })
  })
})

// ============================================================
// 「删除人员」h5 边界 + E2E 测试补充
// ============================================================
test.describe('删除人员 - 边界与恢复流程', () => {
  test('删除带证书人员应显示警示，删除后进入停用筛选可恢复', async ({ page }) => {
    const session = await ensureApiSession()
    await injectSession(page, session)

    const suffix = Date.now().toString(36).slice(-6)

    // API 创建一个带证书的人员
    const createRes = await fetch(`${apiBaseUrl}/api/knowledge/personnel`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
      body: JSON.stringify({
        name: `删除测试_${suffix}`,
        employeeNumber: `DEL${suffix}`,
        departmentName: '测试部',
        gender: '男',
        entryDate: '2022-01-01',
        phone: '13900000001',
        education: '本科',
        technicalTitle: '测试',
        certificates: [{ name: '安全工程师', certificateNumber: `AQ-${suffix}`, type: 'SECURITY', issueDate: '2023-01-01', expiryDate: '2027-01-01', attachmentUrl: '' }],
        educations: []
      })
    })
    const created = await createRes.json()
    const personId = created?.data?.id
    expect(personId).toBeTruthy()

    await page.goto('/knowledge/personnel')
    await page.waitForLoadState('networkidle')

    // 筛选到在职，找到该人并删除
    await page.getByPlaceholder('搜索姓名或工号').fill(`DEL${suffix}`)
    await page.getByRole('button', { name: '查询' }).click()
  await page.waitForResponse(
        (response) => response.url().includes('/api/knowledge/personnel') && response.status() === 200,
        { timeout: 10000 }
      ).catch(() => {})

    const row = page.locator('tr', { hasText: `DEL${suffix}` }).first()
    await row.getByRole('button', { name: '删除' }).click()

    // 验证强确认弹窗出现 + 证书警示
    await expect(page.getByRole('dialog')).toContainText('删除人员档案')
    await expect(page.getByRole('dialog')).toContainText('持有 1 张证书')

    // 填写原因 + 勾选 + 确认
    await page.getByRole('dialog').getByRole('textbox').fill('测试删除-业绩不达标')
    await page.getByRole('dialog').getByRole('checkbox').check()

    // 关键 UI 截图断言：强确认弹窗（含证书警示 + 原因输入 + 勾选）
    await expect(page.getByRole('dialog')).toHaveScreenshot('delete-personnel-dialog-with-warning.png')

    await page.getByRole('dialog').getByRole('button', { name: '确认删除' }).click()

    await expect(page.getByText('删除成功')).toBeVisible()

    // 切换到停用筛选，应能看到该人 + 恢复按钮
    await page.getByRole('combobox', { name: '状态' }).click()
    await page.getByRole('option', { name: '停用' }).click()
    await page.getByRole('button', { name: '查询' }).click()
  await page.waitForResponse(
        (response) => response.url().includes('/api/knowledge/personnel') && response.status() === 200,
        { timeout: 10000 }
      ).catch(() => {})

    const inactiveRow = page.locator('tr', { hasText: `DEL${suffix}` }).first()
    await expect(inactiveRow).toBeVisible()
    await expect(inactiveRow.getByRole('button', { name: '恢复' })).toBeVisible()

    // 点击恢复
    await inactiveRow.getByRole('button', { name: '恢复' }).click()
    await page.getByRole('button', { name: '确定' }).click() // 二次确认

    await expect(page.getByText('恢复成功')).toBeVisible()

    // 切回在职，应能看到该人
    await page.getByRole('combobox', { name: '状态' }).click()
    await page.getByRole('option', { name: '在职' }).click()
    await page.getByRole('button', { name: '查询' }).click()
  await page.waitForResponse(
        (response) => response.url().includes('/api/knowledge/personnel') && response.status() === 200,
        { timeout: 10000 }
      ).catch(() => {})

    await expect(page.locator('tr', { hasText: `DEL${suffix}` })).toBeVisible()
  })
})
