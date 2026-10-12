// Input: deployed frontend, real API, existing project and authenticated storage state
// Output: read-only candidate visibility and active rule/API/numbering consistency proofs
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
    await expect(row.locator('.code-cell')).toHaveText(/^[A-Za-z_]+$/.test(item.code || '') ? String(index + 1) : item.code)
  }
  const sum = data.items.reduce((total, item) => total + Number(item.weight), 0)
  expect(Number(data.summary.totalWeight)).toBe(sum)
  await expect(table.locator('tfoot .tfoot-weight')).toHaveText(String(sum))
})
