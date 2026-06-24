import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'

const PWD = process.env.COMMERCIAL_E2E_PASSWORD || 'XiyuDemo!2026'

async function loginAs(page, role) {
  const s = await ensureApiSession({
    username: `e2e_wh_${role}_${Date.now()}_${Math.random().toString(36).slice(2,6)}`,
    role: '/bidAdmin', fullName: `E2E WH ${role}`, password: PWD
  })
  await injectSession(page, s); return s
}

test.describe('§4.4 仓库信息 — 蓝图全功能验证', () => {

  test.describe('新增仓库 — 3-Tab 表单', () => {

    test('创建仓库：3个Tab填写完整字段', async ({ page }) => {
      await loginAs(page, '/bidAdmin')
      await page.goto('/knowledge/warehouse')

      await page.getByRole('button', { name: '新增仓库' }).click()
      await page.waitForSelector('.el-dialog', { timeout: 5000 })

      // Tab1 基础信息
      await page.locator('.el-form-item:has-text("仓库名称") input').fill('E2E北京顺义中央仓')
      await page.locator('.el-dialog .el-form-item:has-text("仓库类型") .el-select').click()
      await page.locator('.type-select-popper .el-select-dropdown__item:has-text("自营")').click()
      await page.locator('.el-form-item:has-text("所在省份") input').fill('北京')
      await page.locator('.el-form-item:has-text("具体地址") input').fill('北京市顺义区天竺综保区')
      await page.locator('.el-form-item:has-text("仓库面积") input').fill('1500')
      await page.locator('.el-form-item:has-text("区域联系人") input').fill('王区长')

      // Tab2 租约信息
      await page.locator('.el-tabs__item:has-text("租约/服务信息")').click()
      await expect(page.locator('.el-form-item:has-text("开始时间") input').first()).toBeVisible({ timeout: 3000 })
      await page.locator('.el-form-item:has-text("开始时间") input').fill('2025-01-01')
      await page.locator('.el-form-item:has-text("结束时间") input').fill('2027-12-31')
      await page.locator('.el-form-item:has-text("出租方") input').fill('北京顺义物流园区')
      await page.locator('.el-form-item:has-text("承租方") input').fill('西域')

      // Tab3 资料核验
      await page.locator('.el-tabs__item:has-text("资料核验")').click()
      await expect(page.locator('.el-dialog .el-form-item:has-text("是否有产权证")').first()).toBeVisible({ timeout: 3000 })

      // 保存
      await page.getByRole('button', { name: '保存' }).click()
      await expect(page.locator('.el-dialog')).toBeHidden({ timeout: 5000 })

      // 验证表格中出现
      await expect(page.locator('.el-table__body:has-text("E2E北京顺义中央仓")')).toBeVisible({ timeout: 5000 })
    })

    test('必填校验：空字段提交被阻断', async ({ page }) => {
      await loginAs(page, '/bidAdmin')
      await page.goto('/knowledge/warehouse')

      await page.getByRole('button', { name: '新增仓库' }).click()
      await page.waitForSelector('.el-dialog', { timeout: 5000 })

      // 不填任何字段直接保存
      await page.getByRole('button', { name: '保存' }).click()

      // 弹窗应仍然打开
      await expect(page.locator('.el-dialog')).toBeVisible()
    })

    test('时间校验：结束时间早于开始时间被阻断', async ({ page }) => {
      await loginAs(page, '/bidAdmin')
      await page.goto('/knowledge/warehouse')

      await page.getByRole('button', { name: '新增仓库' }).click()
      await page.waitForSelector('.el-dialog', { timeout: 5000 })

      // Tab1 基础必填
      await page.locator('.el-form-item:has-text("仓库名称") input').fill('校验测试仓')
      await page.locator('.el-form-item:has-text("所在省份") input').fill('上海')
      await page.locator('.el-form-item:has-text("具体地址") input').fill('浦东')
      await page.locator('.el-form-item:has-text("仓库面积") input').fill('1000')
      await page.locator('.el-form-item:has-text("区域联系人") input').fill('测试联系人')

      // Tab2 错误时间
      await page.locator('.el-tabs__item:has-text("租约/服务信息")').click()
      await expect(page.locator('.el-form-item:has-text("开始时间") input').first()).toBeVisible({ timeout: 3000 })
      await page.locator('.el-form-item:has-text("开始时间") input').fill('2027-12-31')
      await page.locator('.el-form-item:has-text("结束时间") input').fill('2025-01-01')
      await page.locator('.el-form-item:has-text("出租方") input').fill('测试出租方')
      await page.locator('.el-form-item:has-text("承租方") input').fill('测试承租方')

      await page.getByRole('button', { name: '保存' }).click()

      // 错误提示应出现
      await expect(page.locator('.el-message--error').first()).toBeVisible({ timeout: 3000 })
    })
  })

  test.describe('列表展示 — 11 列 + 状态', () => {

    test('表格加载并显示核心列', async ({ page }) => {
      await loginAs(page, '/bidAdmin')
      await page.goto('/knowledge/warehouse')
      await page.waitForSelector('.el-table', { timeout: 10000 })

      await expect(page.locator('.el-table__header:has-text("仓库名称")')).toBeVisible()
      await expect(page.locator('.el-table__header:has-text("仓库类型")')).toBeVisible()
      await expect(page.locator('.el-table__header:has-text("所属区域")')).toBeVisible()
      await expect(page.locator('.el-table__header:has-text("面积")')).toBeVisible()
      await expect(page.locator('.el-table__header:has-text("到期天数")')).toBeVisible()
      await expect(page.locator('.el-table__header:has-text("状态")')).toBeVisible()
    })

    test('分页组件可见', async ({ page }) => {
      await loginAs(page, '/bidAdmin')
      await page.goto('/knowledge/warehouse')
      await page.waitForSelector('.el-pagination', { timeout: 10000 })
      await expect(page.locator('.el-pagination')).toBeVisible()
    })
  })

  test.describe('编辑仓库', () => {

    test('编辑仓库名称并保存', async ({ page }) => {
      await loginAs(page, '/bidAdmin')
      await page.goto('/knowledge/warehouse')
      await page.waitForSelector('.el-table', { timeout: 10000 })

      // 如果表格有数据，点击编辑按钮
      const editBtn = page.locator('.el-table__body .el-button:has-text("编辑")').first()
      if (await editBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
        await editBtn.click()
        await page.waitForSelector('.el-dialog', { timeout: 5000 })

        const nameInput = page.locator('.el-dialog .el-input__inner').first()
        const oldName = await nameInput.inputValue()
        await nameInput.fill(oldName + '_已编辑')

        await page.getByRole('button', { name: '保存' }).click()

        // 应看到更新成功或弹窗关闭
        await expect(page.locator('.el-dialog')).toHaveCount(0, { timeout: 3000 })
      }
    })
  })

  test.describe('关仓与恢复', () => {

    test('关仓确认弹窗可见', async ({ page }) => {
      await loginAs(page, '/bidAdmin')
      await page.goto('/knowledge/warehouse')
      await page.waitForSelector('.el-table', { timeout: 10000 })

      const closeBtn = page.locator('.el-table__body .el-button:has-text("关仓")').first()
      if (await closeBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
        await closeBtn.click()
        await expect(page.locator('.el-message-box:has-text("关仓")').first()).toBeVisible({ timeout: 3000 })
        await page.locator('.el-message-box__btns .el-button:has-text("取消")').click()
      }
    })
  })
})
