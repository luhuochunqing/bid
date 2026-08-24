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

  // 当前 WarehouseDialog 为单页分区布局（基础信息/租约信息/资料核验 section-title 分隔，无 Tab）；
  // 列表筛选区也含"仓库名称"等同名字段，表单定位必须限定在 .el-dialog 内。
  // 租赁合同为必传附件（前端保存前拦截），完整提交需 setInputFiles 注入文件。
  const DIALOG = '.el-dialog '

  async function fillWarehouseBaseForm(page, name) {
    await page.locator(DIALOG + '.el-form-item:has-text("仓库名称") input').fill(name)
    await page.locator(DIALOG + '.el-form-item:has-text("仓库类型") .el-select').click()
    await page.locator('.type-select-popper .el-select-dropdown__item:has-text("自营")').click()
    await page.locator(DIALOG + '.el-form-item:has-text("所属区域") .el-select').click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("华北")').first().click()
    await page.locator(DIALOG + '.el-form-item:has-text("所在省份") .el-select').click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("北京市")').first().click()
    await page.locator(DIALOG + '.el-form-item:has-text("具体地址") input').fill('北京市顺义区天竺综保区')
    await page.locator(DIALOG + '.el-form-item:has-text("仓库面积") input').fill('1500')
    await page.locator(DIALOG + '.el-form-item:has-text("区域联系人") input').fill('王区长')
  }

  async function fillLeaseSection(page, { startDate = '2025-01-01', endDate = '2027-12-31' } = {}) {
    const startInput = page.locator(DIALOG + '.el-form-item:has-text("开始时间") input')
    await startInput.click()
    await startInput.fill(startDate)
    await startInput.press('Enter')
    const endInput = page.locator(DIALOG + '.el-form-item:has-text("结束时间") input')
    await endInput.click()
    await endInput.fill(endDate)
    await endInput.press('Enter')
    await page.locator(DIALOG + '.el-form-item:has-text("出租方") input').fill('北京顺义物流园区')
    await page.locator(DIALOG + '.el-form-item:has-text("承租方") input').fill('西域')
  }

  async function uploadLeaseContract(page, fileName = 'e2e-租赁合同.pdf') {
    await page.locator(DIALOG + '.el-form-item:has-text("租赁合同") input[type=file]')
      .setInputFiles({ name: fileName, mimeType: 'application/pdf', buffer: Buffer.from('e2e-lease-contract') })
    await expect(page.locator(DIALOG + '.el-form-item:has-text("租赁合同") .el-upload__tip:visible').first()).toBeVisible()
  }

  test.describe('新增仓库 — 3-Tab 表单', () => {

    test('创建仓库：3个Tab填写完整字段', async ({ page }) => {
      await loginAs(page, '/bidAdmin')
      await page.goto('/knowledge/warehouse')
      await page.waitForSelector('.el-table', { timeout: 10000 })

      await page.getByRole('button', { name: '新增仓库' }).click()
      await page.waitForSelector('.el-dialog', { timeout: 5000 })

      const name = `E2E北京顺义中央仓_${Date.now()}`
      await fillWarehouseBaseForm(page, name)
      await fillLeaseSection(page)
      await uploadLeaseContract(page)

      // 保存
      await page.getByRole('button', { name: '保存' }).click()
      await expect(page.locator('.el-dialog')).toBeHidden({ timeout: 5000 })

      // 验证表格中出现
      await expect(page.locator(`.el-table__body:has-text("${name}")`)).toBeVisible({ timeout: 5000 })
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

      await fillWarehouseBaseForm(page, `校验测试仓_${Date.now()}`)
      await fillLeaseSection(page, { startDate: '2027-12-31', endDate: '2025-01-01' })
      await uploadLeaseContract(page)

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

        const nameInput = page.locator(DIALOG + '.el-input__inner').first()
        const oldName = await nameInput.inputValue()
        await nameInput.fill(oldName + '_已编辑')

        // 租赁合同必传：编辑已有仓库（API 造数无附件）需补传后才能保存
        const contractInput = page.locator(DIALOG + '.el-form-item:has-text("租赁合同") input[type=file]')
        if (await contractInput.isVisible().catch(() => false)) {
          await contractInput.setInputFiles({ name: 'e2e-租赁合同.pdf', mimeType: 'application/pdf', buffer: Buffer.from('e2e-lease-contract') })
        }

        await page.getByRole('button', { name: '保存' }).click()

        // 应看到更新成功或弹窗关闭
        await expect(page.locator('.el-dialog')).toBeHidden({ timeout: 5000 })
      }
    })
  })

  test.describe('租约信息 — 租赁合同字段', () => {

    test('租赁合同上传位于租约信息分区（而非资料核验）', async ({ page }) => {
      await loginAs(page, '/bidAdmin')
      await page.goto('/knowledge/warehouse')

      await page.getByRole('button', { name: '新增仓库' }).click()
      await page.waitForSelector('.el-dialog', { timeout: 5000 })

      // 分区为平铺兄弟节点，用纵向坐标断言：租赁合同位于"租约信息"与"资料核验"两个标题之间
      const positions = await page.evaluate(() => {
        const top = (el) => el.getBoundingClientRect().top
        const titles = [...document.querySelectorAll('.el-dialog .section-title')]
        const leaseTitle = titles.find(t => t.textContent.includes('租约信息'))
        const certTitle = titles.find(t => t.textContent.includes('资料核验'))
        const leaseItem = [...document.querySelectorAll('.el-dialog .el-form-item')]
          .find(el => el.textContent.includes('租赁合同'))
        return {
          leaseTitleTop: top(leaseTitle),
          certTitleTop: top(certTitle),
          leaseItemTop: top(leaseItem),
        }
      })
      expect(positions.leaseItemTop).toBeGreaterThan(positions.leaseTitleTop)
      expect(positions.leaseItemTop).toBeLessThan(positions.certTitleTop)

      await expect(page.locator(DIALOG + '.el-form-item:has-text("租赁合同") .el-upload')).toBeVisible()
    })

    test('租赁合同无 switch 开关，直接显示上传组件', async ({ page }) => {
      await loginAs(page, '/bidAdmin')
      await page.goto('/knowledge/warehouse')

      await page.getByRole('button', { name: '新增仓库' }).click()
      await page.waitForSelector('.el-dialog', { timeout: 5000 })

      await expect(page.locator(DIALOG + '.el-form-item:has-text("有租赁合同") .el-switch')).toHaveCount(0)
      await expect(page.locator(DIALOG + '.el-form-item:has-text("租赁合同") .el-upload')).toBeVisible()
    })

    test('必填校验：未上传租赁合同提交被阻断', async ({ page }) => {
      await loginAs(page, '/bidAdmin')
      await page.goto('/knowledge/warehouse')

      await page.getByRole('button', { name: '新增仓库' }).click()
      await page.waitForSelector('.el-dialog', { timeout: 5000 })

      const name = `必填校验测试仓_${Date.now()}`
      await fillWarehouseBaseForm(page, name)
      await fillLeaseSection(page)
      // 注意：不上传租赁合同

      await page.getByRole('button', { name: '保存' }).click()

      await expect(page.locator('.el-message--error:has-text("请上传租赁合同附件")').first()).toBeVisible({ timeout: 3000 })
      await expect(page.locator('.el-dialog')).toBeVisible()
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
