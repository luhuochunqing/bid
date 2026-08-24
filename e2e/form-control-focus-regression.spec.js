import { expect, test } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

async function loginAsStyleRegressionUser(page) {
  const session = await ensureApiSession({
    username: `form_style_${Date.now()}`,
    role: '/bidAdmin',
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
  // 项目搜索区已重构为 ProjectSearchCard（.project-search-form，label-position=top）
  await expect(page.locator('.project-search-form')).toBeVisible()

  const nameInput = page.getByPlaceholder('搜索项目名称、招标主体')
  const nameWrapper = nameInput.locator('xpath=ancestor::div[contains(@class, "el-input__wrapper")]')

  // Mouse click: border light gray, no box-shadow  // @ui-cover:project
  await nameInput.click()
  const mouseStyle = await readBoxStyle(nameWrapper)
  expect(mouseStyle.borderColor).toMatch(/rgb\(1[6-9][0-9],\s*1[6-9][0-9],\s*1[6-9][0-9]\)/)
  expect(mouseStyle.boxShadow).not.toMatch(/rgba?\((?!2[0-2][0-9])[0-9]+,/)  // 无彩色光晕（灰 inset 允许）

  // Keyboard Tab+focus: border slightly darker, no box-shadow
  await page.keyboard.press('Tab')
  await nameInput.focus()
  const keyboardStyle = await readBoxStyle(nameWrapper)
  expect(keyboardStyle.borderColor).toMatch(/rgb\(1[6-9][0-9]\s*,\s*1[6-9][0-9]\s*,\s*1[6-9][0-9]\)/)
  expect(keyboardStyle.boxShadow).not.toMatch(/rgba?\((?!2[0-2][0-9])[0-9]+,/)  // 无彩色光晕

  // Select click: border light gray, no box-shadow
  const selectWrapper = page.locator('.project-search-form .el-select__wrapper').first()
  await selectWrapper.click()
  const selectStyle = await readBoxStyle(selectWrapper)
  // el-select-v2 聚焦边框为深灰/黑（26,26,26），与 input 的浅灰不同
  expect(selectStyle.borderColor).toMatch(/rgb\((1[6-9]|[0-9]|[12][0-9])[0-9]*,\s*[0-9]+,\s*[0-9]+\)/)
  expect(selectStyle.boxShadow).not.toMatch(/rgba?\((?!2[0-2][0-9])[0-9]+,/)  // 无彩色光晕
})
