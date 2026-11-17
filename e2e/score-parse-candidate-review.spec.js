// Input: deployed frontend, real API, existing project and authenticated storage state
// Output: read-only candidate/active-rule consistency and silent historical-scoring recovery proofs
// Pos: e2e/ - score-parse candidate review regression
import { test, expect } from '@playwright/test'

const projectId = process.env.XIYU_SCOREPARSE_PROJECT_ID
const storageState = process.env.XIYU_SCOREPARSE_STORAGE_STATE
if (storageState) test.use({ storageState })

test('shows every extracted candidate without requiring formal adoption', async ({ page }) => {
  test.skip(!projectId || !storageState, 'Requires an existing project and authenticated storage state; no fixtures or API mocks')
  await page.goto(`/project/${projectId}/drafting`)
  const response = await page.request.get(`/api/projects/${projectId}/score-parse/extraction/latest`)
  expect(response.ok()).toBe(true)
  const { data: snapshot } = await response.json()
  expect(snapshot?.items?.length).toBeGreaterThan(0)
  await page.locator('[data-test="score-parse-button"]').first().click()
  const drawer = page.locator('.score-parse-drawer')
  await expect(drawer).toBeVisible()
  const candidates = drawer.locator('.extraction-panel [data-item-id]')
  await expect(candidates).toHaveCount(snapshot.items.length)
  for (const item of snapshot.items) {
    const candidate = drawer.locator(`.extraction-panel [data-item-id="${item.itemId}"]`)
    await expect(candidate).toBeVisible()
    await expect(candidate).toContainText(item.name)
  }
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
