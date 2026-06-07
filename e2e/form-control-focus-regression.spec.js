import { expect, test } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

async function loginAsStyleRegressionUser(page) {
  const session = await ensureApiSession({
    username: `form_style_${Date.now()}`,
    role: 'ADMIN',
    fullName: 'Form Style Admin',
  })
  await injectSession(page, session)
}

async function readBoxStyle(locator) {
  return locator.evaluate((element) => {
    const style = window.getComputedStyle(element)
    return {
      borderColor: style.borderColor,
      boxShadow: style.boxShadow,
      outline: style.outline,
    }
  })
}

test('form controls keep simple mouse focus and keyboard-only gray focus affordance', async ({ page }) => {
  await loginAsStyleRegressionUser(page)
  await page.goto('/project')
  await expect(page.locator('.search-form')).toBeVisible()

  const nameInput = page.getByPlaceholder('请输入项目名称')
  const nameWrapper = nameInput.locator('xpath=ancestor::div[contains(@class, "el-input__wrapper")]')

  // Mouse click: border light gray, no box-shadow  // @ui-cover:project
  await nameInput.click()
  const mouseStyle = await readBoxStyle(nameWrapper)
  expect(mouseStyle.borderColor).toMatch(/rgb\(20[4-9],\s*20[4-9],\s*20[4-9]\)/)
  expect(mouseStyle.boxShadow).toBe('none')

  // Keyboard Tab+focus: border slightly darker, no box-shadow
  await page.keyboard.press('Tab')
  await nameInput.focus()
  const keyboardStyle = await readBoxStyle(nameWrapper)
  expect(keyboardStyle.borderColor).toMatch(/rgb\(1[6-9][0-9]\s*,\s*1[6-9][0-9]\s*,\s*1[6-9][0-9]\)/)
  expect(keyboardStyle.boxShadow).toBe('none')

  // Select click: border light gray, no box-shadow
  const selectWrapper = page.locator('.search-form .el-select__wrapper').first()
  await selectWrapper.click()
  const selectStyle = await readBoxStyle(selectWrapper)
  expect(selectStyle.borderColor).toMatch(/rgb\(20[4-9],\s*20[4-9],\s*20[4-9]\)/)
  expect(selectStyle.boxShadow).toBe('none')
})
