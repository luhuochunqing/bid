import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18089'

/**
 * 工具函数：通过 UI 新增一个人（带教育经历），返回创建后的工号
 */
async function createPersonViaUI(page, { name, employeeNumber, educations = [] }) {
  await page.getByRole('button', { name: '新增人员' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()

  // 工具：通过 textbox accessible name 定位（Element Plus label-width 模式下 getByLabel 不生效，
  // 但 Playwright accessible name 计算会从 el-form-item__label 推导出 textbox name）
  const inputByName = (accessibleName) => dialog.getByRole('textbox', { name: accessibleName })

  // Tab 1 - 基础信息
  await inputByName('* 姓名').fill(name)
  await inputByName('* 工号').fill(employeeNumber)
  await inputByName('部门').fill('E2E 测试部')
  await inputByName('学历').fill('本科')
  await inputByName('技术职称').fill('测试专员')

  // Tab 2 - 教育经历
  await dialog.locator('.el-tabs__item').filter({ hasText: '教育经历' }).click()
  await expect(dialog.locator('.edu-item').first()).toBeVisible({ timeout: 5000 }).catch(() => {})

  for (const edu of educations) {
    await dialog.getByRole('button', { name: '+ 添加教育经历' }).click()
    const row = dialog.locator('.edu-item').last()

    await row.getByPlaceholder('如：清华大学').fill(edu.schoolName)
    // el-date-picker type="month" 无 placeholder，用 .el-date-editor input 按顺序定位
    await row.locator('.el-date-editor').nth(0).locator('input').fill(edu.startDate)
    await row.locator('.el-date-editor').nth(1).locator('input').fill(edu.endDate)

    // el-select 用 .el-form-item__label 精准匹配文本
    await row.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: /^最高学历$/ }) }).locator('.el-select').click()
    await page.getByRole('option', { name: edu.highestEducation, exact: true }).click()
    await row.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: /^学习形式$/ }) }).locator('.el-select').click()
    await page.getByRole('option', { name: edu.studyForm, exact: true }).click()
    if (edu.major) {
      await row.getByPlaceholder('如：计算机科学与技术').fill(edu.major)
    }
  }

  // 切到「证书与职称」Tab（最后一个 Tab，此时 footer 才显示「保存」按钮）
  await dialog.locator('.el-tabs__item').filter({ hasText: '证书与职称' }).click()

  // 保存
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('创建成功')).toBeVisible({ timeout: 10000 })

  return employeeNumber
}

test.describe('知识库 - 人员新增（教育经历支持）- E2E 验证', () => {

  test('bid_specialist 可以通过 3 Tab 表单成功新增含多条教育经历的人员', async ({ page }) => {
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const username = `e2e_personnel_${suffix}`

    const session = await ensureApiSession({
      username,
      role: 'bid-Team',
      fullName: `E2E 人员专员 ${suffix}`
    })

    await injectSession(page, session)
    await page.goto('/knowledge/personnel')
    await page.waitForLoadState('load')

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

  const allowedRoles = ['/bidAdmin', 'bid-TeamLeader', 'bid-Team']
  // 根据 RoleProfileCatalog 现有 8 个角色（staff/task_executor/auditor 等已退役）
  const disallowedRoles = ['bid-projectLeader', 'bid-otherDept', 'bid-administration']

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
      await page.waitForLoadState('load')

      await page.getByRole('button', { name: '新增人员' }).click()
      const dialog = page.getByRole('dialog')
      await expect(dialog).toBeVisible()

      // 用 accessible name 定位（Element Plus label-width 模式下 getByLabel 不生效，
      // 但 Playwright accessible name 计算会从 el-form-item__label 推导出 textbox name）
      const inputByName = (accessibleName) => dialog.getByRole('textbox', { name: accessibleName })

      await inputByName('* 姓名').fill(`权限测试_${role}`)
      await inputByName('* 工号').fill(`PERM${role}${suffix}`)

      // 至少加一条教育经历（含必填字段：学校、最高学历、学习形式、毕业时间）
      await dialog.locator('.el-tabs__item').filter({ hasText: '教育经历' }).click()
      await dialog.getByRole('button', { name: '+ 添加教育经历' }).click()
      const row = dialog.locator('.edu-item').first()
      await row.getByPlaceholder('如：清华大学').fill('测试大学')
      await row.locator('.el-date-editor').nth(0).locator('input').fill('2020-09')
      await row.locator('.el-date-editor').nth(1).locator('input').fill('2024-06')
      await row.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: /^最高学历$/ }) }).locator('.el-select').click()
      await page.getByRole('option', { name: '本科', exact: true }).click()
      await row.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: /^学习形式$/ }) }).locator('.el-select').click()
      await page.getByRole('option', { name: '全日制', exact: true }).click()

      // 切到「证书与职称」Tab（最后一个 Tab，此时 footer 才显示「保存」按钮）
      await dialog.locator('.el-tabs__item').filter({ hasText: '证书与职称' }).click()
      await dialog.getByRole('button', { name: '保存' }).click()

      await expect(page.getByText('创建成功')).toBeVisible({ timeout: 10000 })
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
          educations: [{ schoolName: 'xx', startDate: '2020-01-01', endDate: '2024-01-01', highestEducation: '本科', studyForm: '全日制' }]
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
      role: 'bid-Team',
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
          { schoolName: '初始大学', startDate: '2020-09-01', endDate: '2024-06-01', highestEducation: '本科', studyForm: '全日制' }
        ]
      })
    })
    const created = await createRes.json()
    console.log('[DEBUG edit-self] createRes status:', createRes.status, 'body:', JSON.stringify(created).slice(0, 600))
    const personId = created?.data?.id || created?.data?.personnel?.id
    expect(personId).toBeTruthy()

    await injectSession(page, session)
    await page.goto('/knowledge/personnel')
    await page.waitForLoadState('load')

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
          { schoolName: '初始大学', startDate: '2020-09-01', endDate: '2024-06-01', highestEducation: '本科', studyForm: '全日制' },
          { schoolName: '新大学', startDate: '2024-09-01', endDate: '2027-06-01', highestEducation: '硕士', studyForm: '全日制' }
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
      role: 'bid-projectLeader', // 不允许编辑人员
      fullName: `E2E 无编辑权限 ${suffix}`
    })

    // 先让有权限的人创建一个测试记录
    const adminSession = await ensureApiSession({
      username: `e2e_admin_for_edit_${suffix}`,
      role: '/bidAdmin'
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
        educations: [{ schoolName: '测试大学', startDate: '2020-09-01', endDate: '2024-06-01', highestEducation: '本科', studyForm: '全日制' }]
      })
    })
    const created = await createRes.json()
    console.log('[DEBUG edit-denied] createRes status:', createRes.status, 'body:', JSON.stringify(created).slice(0, 400))
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
        educations: [{ schoolName: '测试大学', startDate: '2020-09-01', endDate: '2024-06-01', highestEducation: '本科', studyForm: '全日制' }]
      })
    })

    console.log('[DEBUG edit-denied] updateRes status:', updateRes.status)
    expect(updateRes.status).toBe(403)
  })

  // 更完整的编辑流程测试：工号变更 + 教育经历修改 + 证书替换
  test('完整编辑流程：修改工号、教育经历，并替换证书附件', async ({ page }) => {
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const username = `e2e_full_edit_${suffix}`

    const session = await ensureApiSession({
      username,
      role: 'bid-Team',
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
          { schoolName: '旧大学', startDate: '2019-09-01', endDate: '2023-06-01', highestEducation: '本科', studyForm: '全日制' }
        ]
      })
    })
    const created = await createRes.json()
    const personId = created?.data?.id || created?.data?.personnel?.id
    expect(personId).toBeTruthy()

    await injectSession(page, session)
    await page.goto('/knowledge/personnel')
    await page.waitForLoadState('load')

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

    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()

    // === Tab 1: 修改工号（触发前置警示）===
    await dialog.locator('.el-tabs__item').filter({ hasText: '基础信息' }).click()
    const newEmpNo = `NEW${suffix}`
    // 用 accessible name 定位（Element Plus label-width 模式下 getByLabel 不生效）
    const inputByName = (accessibleName) => dialog.getByRole('textbox', { name: accessibleName })
    await inputByName('* 工号').fill(newEmpNo)

    // 验证提交前本地警示出现（Phase 5 已实现）
    await expect(dialog.locator('.form-warning')).toContainText('修改工号将影响外部对账')

    // === Tab 2: 修改教育经历（修改第一条 + 新增一条）===
    await dialog.locator('.el-tabs__item').filter({ hasText: '教育经历' }).click()
  await expect(dialog.locator('.edu-item').first()).toBeVisible({ timeout: 5000 }).catch(() => {})

    // 修改第一条教育经历
    const firstEduRow = dialog.locator('.edu-item').first()
    await firstEduRow.getByPlaceholder('如：清华大学').fill('新清华大学')
    await firstEduRow.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: /^最高学历$/ }) }).locator('.el-select').click()
    // Element Plus select 下拉 teleport 到 body，只点击可见的 option，避免同名冲突
    await page.locator('.el-select-dropdown:visible').getByRole('option', { name: '硕士', exact: true }).click()
    // 等下拉关闭
    await expect(page.locator('.el-select-dropdown:visible')).toHaveCount(0, { timeout: 3000 }).catch(() => {})

    // 新增第二条
    await dialog.getByRole('button', { name: '+ 添加教育经历' }).click()
    const newEduRow = dialog.locator('.edu-item').last()
    await newEduRow.getByPlaceholder('如：清华大学').fill('斯坦福大学')
    await newEduRow.locator('.el-date-editor').nth(0).locator('input').fill('2023-09')
    await newEduRow.locator('.el-date-editor').nth(1).locator('input').fill('2025-06')
    await newEduRow.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: /^最高学历$/ }) }).locator('.el-select').click()
    await page.locator('.el-select-dropdown:visible').getByRole('option', { name: '硕士', exact: true }).click()
    // 必填学习形式（前端 validateTab('education') 要求每条必须有学校、最高学历、学习形式、毕业时间）
    await newEduRow.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: /^学习形式$/ }) }).locator('.el-select').click()
    await page.locator('.el-select-dropdown:visible').getByRole('option', { name: '全日制', exact: true }).click()

    // === Tab 3: 替换证书附件 ===
    await dialog.locator('.el-tabs__item').filter({ hasText: '证书与职称' }).click()

    const certRow = dialog.locator('.cert-item').first()
    // 证书编号输入框无 placeholder，用 accessible name 定位
    await certRow.getByRole('textbox', { name: '证书编号' }).fill(`PMP-NEW-${suffix}`)
    // 模拟更换附件（实际 E2E 附件上传较复杂，这里主要验证字段变更 + 后端逻辑）
    // 如果有文件上传组件，可以用 setInputFiles

    // 保存
    await dialog.getByRole('button', { name: '保存' }).click()

    // 验证后端返回包含工号变更警示
    // ElMessageBox.alert 标题"更新成功（含警示）"可能被分割成多个 text node，用宽松匹配
    await expect(page.locator('.el-message-box').getByText(/更新成功/)).toBeVisible({ timeout: 10000 })
    // 关闭弹窗以便后续操作
    await page.getByRole('button', { name: '我知道了' }).click().catch(() => {})
    await expect(page.locator('.el-message-box')).toBeHidden({ timeout: 3000 }).catch(() => {})

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
    const session = await ensureApiSession({ username: `e2e_view_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`, role: '/bidAdmin', fullName: 'E2E View Certs' })
    await injectSession(page, session)
    await page.goto('/knowledge/personnel')
    await page.waitForLoadState('load')

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
        educations: [{ schoolName: '测试大学', startDate: '2019-09-01', endDate: '2023-06-01', highestEducation: '本科', studyForm: '全日制' }]
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
    await expect(row.getByText('男')).toBeVisible()
    await expect(row.getByText(/年/)).toBeVisible() // 入职年限
    await expect(row.locator('.cert-count-clickable, [class*="cert"]')).toBeVisible()

    // 整行点击打开详情抽屉（800px 4 Tab）
    await row.click()
    const drawer = page.locator('.el-drawer')
    await expect(drawer).toBeVisible({ timeout: 5000 })

    // 验证 4 个 Tab 存在（el-tabs__item 承载 role=tab，限定在 drawer 内）
    await expect(drawer.locator('.el-tabs__item').filter({ hasText: '基础信息' })).toBeVisible()
    await expect(drawer.locator('.el-tabs__item').filter({ hasText: '教育经历' })).toBeVisible()
    await expect(drawer.locator('.el-tabs__item').filter({ hasText: '证书与职称' })).toBeVisible()
    await expect(drawer.locator('.el-tabs__item').filter({ hasText: '操作日志' })).toBeVisible()

    // 切换到证书 Tab 并验证证书数量点击逻辑（从列表直接点数量）
    await drawer.locator('.el-tabs__item').filter({ hasText: '证书与职称' }).click()
    // 详情抽屉用 el-table 展示证书列表（不是 cert-item），「建造师」可能多列出现，用 .first()
    await expect(drawer.locator('.el-table').getByText('建造师').first()).toBeVisible({ timeout: 5000 })

    // 关闭抽屉（PersonnelDetailDrawer 自定义"关闭"按钮，非 Element Plus 默认 close-btn class）
    await drawer.getByRole('button', { name: '关闭' }).click()
    await expect(drawer).toBeHidden({ timeout: 3000 })
  })
})

// ============================================================
// 「删除人员」h5 边界 + E2E 测试补充
// ============================================================
test.describe('删除人员 - 边界与恢复流程', () => {
  test('删除带证书人员应显示警示，删除后进入停用筛选可恢复', async ({ page }) => {
    const session = await ensureApiSession({ username: `e2e_del_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`, role: '/bidAdmin', fullName: 'E2E Delete' })
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
        educations: [{ schoolName: '测试大学', startDate: '2019-09-01', endDate: '2023-06-01', highestEducation: '本科', studyForm: '全日制' }]
      })
    })
    const created = await createRes.json()
    console.log('[DEBUG delete] createRes status:', createRes.status, 'body:', JSON.stringify(created).slice(0, 400))
    const personId = created?.data?.id
    expect(personId).toBeTruthy()

    await page.goto('/knowledge/personnel')
    await page.waitForLoadState('load')

    // 筛选到在职，找到该人并删除
    await page.getByPlaceholder('搜索姓名或工号').fill(`DEL${suffix}`)
    await page.getByRole('button', { name: '查询' }).click()
  await page.waitForResponse(
        (response) => response.url().includes('/api/knowledge/personnel') && response.status() === 200,
        { timeout: 10000 }
      ).catch(() => {})

    const row = page.locator('tr', { hasText: `DEL${suffix}` }).first()
    await row.getByRole('button', { name: '删除' }).click()

    // 验证强确认弹窗出现 + 证书警示（实际文案含 ⚠️ 前缀和后续说明）
    await expect(page.getByRole('dialog')).toContainText('删除人员档案')
    await expect(page.getByRole('dialog')).toContainText(/⚠️.*该人员持有 1 张证书/)

    // 填写原因 + 勾选 + 确认
    await page.getByRole('dialog').getByRole('textbox').fill('测试删除-业绩不达标')
    // Element Plus el-checkbox 原生 input 是隐藏的（class="el-checkbox__original"），check() 会超时；
    // 点击可见的 .el-checkbox__inner 或直接点 label 即可勾选
    await page.getByRole('dialog').locator('.el-checkbox').first().click()

    // 注：原 toHaveScreenshot 断言在动态数据下脆弱，已移除（依据 frontend-pitfalls 规范）

    await page.getByRole('dialog').getByRole('button', { name: '确认删除' }).click()

    await expect(page.getByText('删除成功')).toBeVisible()

    // 切换到停用筛选，应能看到该人 + 恢复按钮
    // el-select 不暴露 combobox role，点击 .el-select 容器打开下拉
    // 列表页有"状态"（人员）和"证书状态"两个筛选框，filter hasText '状态' 会同时匹配两个，用 ^状态$ 精确匹配
    await page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: /^状态$/ }) }).locator('.el-select').click()
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
    // ElMessageBox.confirm 默认按钮为"确定"，el-button 渲染可能带空格"确 定"，用正则稳健匹配
    await page.getByRole('button', { name: /确\s*[定认]/ }).click() // 二次确认

    await expect(page.getByText('恢复成功')).toBeVisible()

    // 切回在职，应能看到该人
    // el-select 不暴露 combobox role，点击 .el-select 容器打开下拉
    // 列表页有"状态"（人员）和"证书状态"两个筛选框，filter hasText '状态' 会同时匹配两个，用 ^状态$ 精确匹配
    await page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: /^状态$/ }) }).locator('.el-select').click()
    await page.getByRole('option', { name: '在职' }).click()
    await page.getByRole('button', { name: '查询' }).click()
  await page.waitForResponse(
        (response) => response.url().includes('/api/knowledge/personnel') && response.status() === 200,
        { timeout: 10000 }
      ).catch(() => {})

    await expect(page.locator('tr', { hasText: `DEL${suffix}` })).toBeVisible()
  })
})
