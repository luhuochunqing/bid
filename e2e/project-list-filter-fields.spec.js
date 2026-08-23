// Input: UI changes to Project/List.vue (label renames + column reordering)  // @ui-cover:project
import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession, apiBaseUrl } from './auth-helpers.js'

async function seedUser(session, username, fullName) {
  const password = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'
  await fetch(`${apiBaseUrl}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username,
      password,
      email: `${username}@example.com`,
      fullName: fullName || username,
      role: '/bidAdmin',
    }),
  }).catch(() => {})
}

async function searchUsers(session, query) {
  const res = await fetch(`${apiBaseUrl}/api/users/search?q=${encodeURIComponent(query)}`, {
    headers: { Authorization: `Bearer ${session.token}` },
  })
  return res.json()
}

test.describe('project list filter fields', () => {
  test('region cascader renders and allows province+city selection', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_proj_filter_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Project Filter',
    })

    await injectSession(page, session)
    await page.goto('/project')
    await page.waitForLoadState('load')
    await expect(page.locator('.title').filter({ hasText: '投标项目列表' })).toBeVisible({ timeout: 20000 })

    // Verify new column headers are present after label rename
    const tableHeader = page.locator('.el-table__header-wrapper, .el-table__inner-wrapper').first()
    await expect(tableHeader.locator('thead').getByText('招标主体')).toBeVisible({ timeout: 5000 })
    await expect(tableHeader.locator('thead').getByText('项目状态')).toBeVisible({ timeout: 5000 })

    // Find the region cascader (label 总部所在地，placeholder 已统一为"全部")
    const regionFormItem = page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: '总部所在地' }) })
    const regionCascader = regionFormItem.locator('.el-cascader')
    await expect(regionCascader).toBeVisible({ timeout: 5000 })

    // Open cascader and select a province（两级节点同挂在一个 panel 下，按 menu 分层定位）
    await regionCascader.click()
    const beijingOption = page.locator('.el-cascader-panel .el-cascader-menu').nth(0).locator('.el-cascader-node').filter({ hasText: '北京市' }).first()
    await expect(beijingOption).toBeVisible({ timeout: 5000 })

    // Select a city under Beijing（直辖市二级节点即"北京市"，无区县层）
    await beijingOption.click()
    const beijingCityOption = page.locator('.el-cascader-panel .el-cascader-menu').nth(1).locator('.el-cascader-node').filter({ hasText: '北京市' }).first()
    await expect(beijingCityOption).toBeVisible({ timeout: 5000 })
    await beijingCityOption.click()

    // Click away to close cascader
    await page.locator('body').click()

    // Verify the cascader input shows the selected value
    const cascaderInput = regionCascader.locator('input').first()
    const inputValue = await cascaderInput.inputValue()
    expect(inputValue.length).toBeGreaterThan(0)

    // Click reset and verify cascader is cleared
    await page.locator('.search-reset-button').click()
    const resetValue = await cascaderInput.inputValue()
    expect(resetValue || '').toBe('')
  })

  test('project leader remote search dropdown appears on focus', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_leader_search_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Leader Search',
    })

    // Seed a second user so the search returns results
    await seedUser(session, `e2e_leader_search_2_${suffix}`)
    await injectSession(page, session)

    await page.goto('/project')
    await page.waitForLoadState('load')
    await expect(page.locator('.title').filter({ hasText: '投标项目列表' })).toBeVisible({ timeout: 20000 })

    // Find "投标负责人" select (now remote-search, label is on the el-form-item)
    const leaderFormItem = page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: '投标负责人' }) })
    const leaderSelect = leaderFormItem.locator('.el-select')
    await expect(leaderSelect).toBeVisible({ timeout: 5000 })

    // Unified search area order: 项目负责人 -> 投标负责人（项目负责人部门筛选项已移除）
    const labelTexts = page.locator('.el-form-item__label')
    const iProjLeader = await labelTexts.allTextContents().then((all) => all.findIndex((t) => t.includes('项目负责人')))
    const iBidLeader = await labelTexts.allTextContents().then((all) => all.findIndex((t) => t.includes('投标负责人')))
    expect(iProjLeader).toBeGreaterThanOrEqual(0)
    expect(iBidLeader).toBeGreaterThan(iProjLeader)

    // Focus to open dropdown (remote search triggers on focus + type)
    await leaderSelect.click()
    // Dropdown should appear with a loading indicator or results
    const dropdown = page.locator('.el-select-dropdown').filter({ hasText: session.user.name }).first()

    // The dropdown should show user results from the remote search
    // （多个 UserPicker 各自渲染下拉，仅当前聚焦的可见，用 :visible 收敛）
    await expect(dropdown.or(page.locator('.el-select-dropdown__empty:visible'))).toBeVisible({ timeout: 8000 })
  })

  test('project leader remote search filters by query string', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_leader_qry_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Leader Query',
    })

    // Seed another user with a distinct name（fullName 独立，避免与登录用户同名）
    const targetName = `E2E查询目标_${suffix}`
    await seedUser(session, `e2e_leader_qry_target_${suffix}`, targetName)
    await injectSession(page, session)

    await page.goto('/project')
    await page.waitForLoadState('load')
    await expect(page.locator('.title').filter({ hasText: '投标项目列表' })).toBeVisible({ timeout: 20000 })

    // Find "投标负责人" select
    const leaderFormItem = page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: '投标负责人' }) })
    const leaderSelect = leaderFormItem.locator('.el-select')
    await leaderSelect.click()

    // Type partial name to trigger remote search
    const input = leaderSelect.locator('input').first()
    await input.fill('E2E查询目标')

    // Wait for dropdown to update with filtered results
    const dropdown = page.locator('.el-select-dropdown').filter({ hasText: 'E2E查询目标' }).first()
    await expect(dropdown).toBeVisible({ timeout: 8000 })
    const firstResult = dropdown.locator('.el-select-dropdown__item', { hasText: 'E2E查询目标' }).first()
    await expect(firstResult).toBeVisible({ timeout: 5000 })

    // Click the first result and verify the selection echoes in the picker
    await firstResult.click()
    await expect(leaderSelect).toContainText('E2E查询目标')
  })

  test('reset button clears region cascader and leader selects', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_reset_filters_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Reset Filters',
    })

    await injectSession(page, session)
    await page.goto('/project')
    await page.waitForLoadState('load')
    await expect(page.locator('.title').filter({ hasText: '投标项目列表' })).toBeVisible({ timeout: 20000 })

    // Set region cascader
    const regionFormItem = page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: '总部所在地' }) })
    const regionCascader = regionFormItem.locator('.el-cascader')
    await regionCascader.click()
    await page.locator('.el-cascader-panel').first().locator('.el-cascader-node').filter({ hasText: '江苏省' }).first().click()
    await page.locator('.el-cascader-panel').last().locator('.el-cascader-node').filter({ hasText: '南京市' }).first().click()
    await page.locator('body').click()

    // Verify cascader has a value
    const cascaderInput = regionCascader.locator('input').first()
    const valueBefore = await cascaderInput.inputValue()
    expect(valueBefore.length).toBeGreaterThan(0)

    // Click reset
    await page.locator('.search-reset-button').click()

    // Cascader should be cleared
    await expect(cascaderInput).toHaveValue('', { timeout: 5000 })
  })

  test('project type dropdown is visible without toggle and includes blueprint options', async ({ page }) => {
    const suffix = Date.now()
    const session = await ensureApiSession({
      username: `e2e_proj_type_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Project Type',
    })

    await injectSession(page, session)
    await page.goto('/project')
    await page.waitForLoadState('load')

    // Unified search area: project type is visible without clicking "更多筛选"
    const typeFormItem = page.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: '项目类型' }) })
    const typeSelect = typeFormItem.locator('.el-select')
    await expect(typeSelect).toBeVisible({ timeout: 5000 })
    await typeSelect.click()

    const dropdown = page.locator('.el-select-dropdown').filter({ hasText: '办公' }).first()
    await expect(dropdown).toBeVisible({ timeout: 5000 })
    await expect(dropdown.locator('.el-select-dropdown__item').filter({ hasText: '工业品' })).toBeVisible({ timeout: 3000 })

    await page.locator('body').click()
  })
})
