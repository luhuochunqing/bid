// Input: deployed frontend, real API, existing project and authenticated storage state
// Output: read-only candidate/active-rule consistency and silent historical-scoring recovery proofs
// Pos: e2e/ - score-parse candidate review regression
import { test, expect } from '@playwright/test'

const projectId = process.env.XIYU_SCOREPARSE_PROJECT_ID
const storageState = process.env.XIYU_SCOREPARSE_STORAGE_STATE
const scoreTypeLabels = { OBJECTIVE: '客观项', SUBJECTIVE: '主观项' }
const unusableName = /^(?:评分项(?:\s*\d+(?:[.-]\d+)*)?|\d+(?:[.-]\d+)*|[一二三四五六七八九十]+)$/
if (storageState) test.use({ storageState })

test('shows every extracted candidate without requiring formal adoption', async ({ page }) => {
  test.skip(!projectId || !storageState, 'Requires an existing project and authenticated storage state; no fixtures or API mocks')
  await page.goto(`/project/${projectId}/drafting`)
  const response = await page.request.get(`/api/projects/${projectId}/score-parse/extraction/latest`)
  expect(response.ok()).toBe(true)
  const { data: snapshot } = await response.json()
  expect(snapshot?.items?.length).toBeGreaterThan(0)
  if (projectId === '1394') {
    const revenueRules = snapshot.items.filter(item =>
      item.name === '营业总收入' && Number(item.rawMaximum) > 0 && !item.diagnostics?.includes('OUT_OF_SCHEME'))
    expect(revenueRules.length, '项目 1394 的版本化快照应包含营业总收入评分项').toBeGreaterThan(0)
    for (const rule of revenueRules) {
      expect(rule.scoreType, '营业总收入的分值由量化门槛决定').toBe('OBJECTIVE')
    }
  }
  await page.locator('[data-test="score-parse-button"]').first().click()
  const drawer = page.locator('.score-parse-drawer')
  await expect(drawer).toBeVisible()
  const candidates = drawer.locator('.extraction-panel [data-item-id]')
  await expect(candidates).toHaveCount(snapshot.items.length)
  for (const item of snapshot.items) {
    const candidate = drawer.locator(`.extraction-panel [data-item-id="${item.itemId}"]`)
    await expect(candidate).toBeVisible()
    await expect(candidate).toContainText(item.name)
    const type = scoreTypeLabels[item.scoreType]
    await expect(candidate.locator('[data-test="extraction-score-type"]')).toHaveText(type ?? '待定类型')
    if (item.computable) {
      expect(type, `known type for ${item.itemId}`).toBeDefined()
      expect(String(item.name || '').trim()).not.toBe('')
      expect(item.name.trim()).not.toMatch(unusableName)
      expect(item.name).not.toContain('|')
    }
  }
})

test('project 1394 legacy review shows the revenue rule as objective and blocks invalid names', async ({ page }) => {
  test.skip(process.env.XIYU_SCOREPARSE_LEGACY_1394 !== '1' || !storageState,
    'Requires authenticated access to test project 1394 with a pending legacy review and versioned extraction disabled')
  const path = '/api/projects/1394/score-parse/review'
  const response = await page.request.get(path)
  expect(response.ok()).toBe(true)
  const { data: review } = await response.json()
  expect(review?.status).toBe('COMPLETED')
  expect(review?.decisionStatus).toBe('REVIEW_REQUIRED')
  const revenue = review.facts.find(fact => fact.dimension === '营业总收入' && Number(fact.weight) > 0 && fact.scopeStatus !== 'OUT_OF_SCHEME')
  expect(revenue?.scoreType, '营业总收入的分值由量化门槛决定').toBe('OBJECTIVE')

  await page.goto('/project/1394/drafting')
  await page.locator('[data-test="score-parse-button"]').first().click()
  const drawer = page.locator('.score-parse-drawer')
  const row = drawer.locator(`tr[data-fact-id="${revenue.factId}"]`)
  const name = row.getByRole('textbox', { name: '评分维度' })
  await expect(name).toHaveValue('营业总收入')
  await expect(row.locator('.score-type-label')).toHaveText('客观项')
  const adopt = drawer.locator('[data-test="btn-adopt-candidates"]')
  await expect(adopt).toBeEnabled()

  const submissions = []
  page.on('request', request => {
    if (request.method() === 'POST' && new URL(request.url()).pathname === path) submissions.push(request.url())
  })
  for (const invalid of ['评分项 21', '1', '一', '3 | 营业总收入', '商务评分（20', '']) {
    await name.fill(invalid)
    await expect(row.locator('[data-test="invalid-dimension-hint"]')).toBeVisible()
    await expect(adopt).toBeDisabled()
    await adopt.evaluate(button => button.click())
  }
  await name.fill('营业总收入')
  await expect(adopt).toBeEnabled()
  expect(submissions).toEqual([])
})

test('active scoring rows retain complete rules and display numbers independently of internal codes', async ({ page }) => {
  test.skip(!projectId || !storageState, 'Requires an existing re-parsed project and authenticated storage state; read-only, no API mocks')
  await page.goto(`/project/${projectId}/drafting`)
  const response = await page.request.get(`/api/projects/${projectId}/score-parse/items`)
  expect(response.ok()).toBe(true)
  const { data } = await response.json()
  expect(data?.items?.length).toBeGreaterThan(0)
  await page.locator('[data-test="score-parse-button"]').first().click()
  const drawer = page.locator('.score-parse-drawer')
  await expect(drawer).toBeVisible()
  const table = drawer.locator('.score-parse-table-wrapper').first()
  await expect(table.locator('tbody tr')).toHaveCount(data.items.length)
  for (const [index, item] of data.items.entries()) {
    expect(item.detail.trim(), `complete source rule for item ${item.id}`).not.toBe('')
    expect(item.detail.trim()).not.toBe('—')
    const row = table.locator(`[data-item-id="${item.id}"]`)
    await expect(row.locator('.original-text-cell')).toContainText(item.detail)
    await expect(row.locator('.weight-cell')).toHaveText(String(Number(item.weight)))
    await expect(row.locator('.code-cell')).toHaveText(String(index + 1))
  }
  const sum = data.items.reduce((total, item) => total + Number(item.weight), 0)
  expect(Number(data.summary.totalWeight)).toBe(sum)
  await expect(table.locator('tfoot .tfoot-weight')).toHaveText(String(sum))
})


test('reopening a historical scoring failure keeps readable status without a toast or new task', async ({ page }) => {
  test.skip(!projectId || !storageState, 'Requires a real project with a latest FAILED scoring task and authenticated storage state')
  const response = await page.request.get(`/api/projects/${projectId}/score-parse/scoring/status`)
  expect(response.ok()).toBe(true)
  const { data: task } = await response.json()
  expect(task?.status, 'Use a real historical failure; do not manufacture production failures').toBe('FAILED')
  const itemsResponse = await page.request.get(`/api/projects/${projectId}/score-parse/items`)
  expect(itemsResponse.ok()).toBe(true)
  const { data: active } = await itemsResponse.json()
  expect(active?.items?.length, 'Existing rules prevent automatic parsing on open').toBeGreaterThan(0)
  expect(active.meta?.lastParseStatus).toBe('COMPLETED')
  const scoringPosts = []
  page.on('request', request => {
    if (request.method() === 'POST' && /\/score-parse\/(scoring|parse)(?:\?|$)/.test(request.url())) scoringPosts.push(request.url())
  })
  await page.goto(`/project/${projectId}/drafting`)
  await page.evaluate(() => {
    window.__scoringErrorNotifications = []
    const observer = new MutationObserver(records => {
      for (const record of records) for (const node of record.addedNodes) {
        if (!(node instanceof Element)) continue
        const messages = [...node.querySelectorAll('.el-message--error, .el-message--warning')]
        if (node.matches('.el-message--error, .el-message--warning')) messages.push(node)
        for (const message of messages) window.__scoringErrorNotifications.push(message.textContent)
      }
    })
    observer.observe(document.body, { childList: true, subtree: true })
  })
  const button = page.locator('[data-test="score-parse-button"]').first()
  const drawer = page.locator('.score-parse-drawer')
  for (let attempt = 0; attempt < 2; attempt++) {
    const statusRead = page.waitForResponse(r => r.url().includes(`/api/projects/${projectId}/score-parse/scoring/status`))
    await button.click()
    expect((await (await statusRead).json()).data.taskId, 'Concurrent scoring changed the fixture').toBe(task.taskId)
    await expect(drawer.locator('[data-test="scoring-hint"]')).toContainText('上次打分未完成')
    await expect(drawer.locator('[data-test="scoring-hint"]')).not.toContainText(task.taskId)
    await expect(drawer.locator('[data-test="scoring-hint"]')).not.toContainText(/[A-Z]+(?:_[A-Z]+)+/)
    await drawer.locator('.el-drawer__close-btn').click()
    await expect(drawer).not.toBeVisible()
  }
  expect(await page.evaluate(() => window.__scoringErrorNotifications)).toEqual([])
  expect(scoringPosts).toEqual([])
})
