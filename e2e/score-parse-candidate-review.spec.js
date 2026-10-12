// Input: deployed frontend, real API, existing project and authenticated storage state
// Output: read-only proof that extraction candidates remain visible before adoption
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
