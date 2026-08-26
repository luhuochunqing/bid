// Input: Playwright E2E 框架、真实后端（项目生命周期）、score-parse 接口 mock（AI 解析非确定性且耗时长）
// Output: score-parse-drawer-layout.spec.js - AI 评分标准解析抽屉布局回归防护
// Pos: e2e/ - Playwright end-to-end coverage
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// 背景（2026-08-26）：el-drawer teleport 到 body 后根元素不带 scoped data-v 属性，
// 组件内 :deep(.el-drawer__body) 的 flex 布局规则静默失效，内容区撑满全高，
// 重新解析遮罩文字落在全部内容中点、远低于当前浏览位置。
// 本用例固化修复后的布局契约：
//   1. .el-drawer__body 必须是 flex 列布局（内容区才能按可视区滚动）
//   2. 遮罩文字在可视区内（而非内容中点）
//   3. 遮罩展示期间内容区滚动被锁定（.scroll-locked），轮询中提示不漂出可视区
// score-parse 五个接口走 page.route mock：AI 解析结果非确定性、耗时数十秒，布局断言不需要真实解析。

import { test, expect } from '@playwright/test'
import { injectSession } from './auth-helpers.js'
import {
  createAuthenticatedSession,
  createProjectFixture,
  ensureRoleUser,
  advanceProjectToDrafting,
} from './support/project-fixtures.js'

const ITEM_COUNT = 40

function buildItems() {
  return Array.from({ length: ITEM_COUNT }, (_, i) => ({
    id: i + 1,
    code: `S${i + 1}`,
    dim: i % 3 === 0 ? '资质业绩' : i % 3 === 1 ? '技术方案' : '商务报价',
    detail: `评分项 ${i + 1}：具备相应资质证书与同类项目业绩得 2 分，每缺一项扣 0.5 分，扣完为止（客观项）`,
    weight: 2,
    scoreType: i % 8 === 7 ? 'SUBJECTIVE' : 'OBJECTIVE',
    status: i % 5 === 4 ? 'FAIL' : 'OK',
    estScore: i % 8 === 7 ? null : 2,
    estBasis: i % 8 === 7 ? null : '知识库匹配完成',
    kbHit: i % 5 !== 4,
  }))
}

function buildResults() {
  return Array.from({ length: ITEM_COUNT }, (_, i) => ({
    scoreItemId: i + 1,
    code: `S${i + 1}`,
    dim: '资质业绩',
    detail: `评分项 ${i + 1}`,
    weight: 2,
    scoreType: i % 8 === 7 ? 'SUBJECTIVE' : 'OBJECTIVE',
    status: i % 5 === 4 ? 'FAIL' : 'OK',
    actualScore: i % 8 === 7 ? null : i % 5 === 4 ? 0 : 2,
    evidence: '标书第 3 章提供证书复印件',
    quote: `第 ${i + 1} 节`,
    missedReason: null,
    suggestion: null,
  }))
}

test.describe('AI 评分标准解析抽屉布局契约（2026-08-26 修复）', () => {
  test('body 为 flex 布局；重新解析遮罩文字始终在可视区且滚动被锁定', async ({ page }) => {
    // —— 真实生命周期：建项目 → 上传招标文件 → 立项审批通过 → DRAFTING（标书制作） ——
    const session = await createAuthenticatedSession()
    const pl = await ensureRoleUser('bid-projectLeader')
    const project = await createProjectFixture(session, '评分抽屉布局', {
      teamMembers: [session.user.id, pl.id],
    })
    await advanceProjectToDrafting(session, project.id, pl)

    await injectSession(page, session)

    // —— score-parse 接口 mock（布局断言不依赖真实 AI 解析） ——
    const api = `**/api/projects/${project.id}/score-parse/**`
    let parseStatusCalls = 0
    await page.route(api, async (route) => {
      const url = route.request().url()
      const json = (body) =>
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ success: true, code: 200, msg: 'ok', data: body }),
        })

      if (url.includes('/items')) {
        return json({
          items: buildItems(),
          summary: null,
          sourceFileName: '招标文件.pdf',
          parseTime: '2026-08-26T09:00:00',
          meta: {
            sourceFileName: '招标文件.pdf',
            parseTime: '2026-08-26T09:00:00',
            bidFileName: '投标文件.pdf',
            scoreTime: '2026-08-26T10:00:00',
            lastParseStatus: 'COMPLETED',
          },
        })
      }
      if (url.includes('/results')) {
        return json({ results: buildResults(), bidFileName: '投标文件.pdf', scoreTime: '2026-08-26T10:00:00' })
      }
      if (route.request().method() === 'POST' && url.includes('/parse')) {
        parseStatusCalls = 0
        return json({ taskId: `e2e-parse-${Date.now()}`, status: 'PENDING' })
      }
      if (url.includes('/parse/status')) {
        // 轮询间隔 2s：前 3 次返回 PROCESSING，留出遮罩断言窗口
        parseStatusCalls += 1
        if (parseStatusCalls <= 3) return json({ status: 'PROCESSING' })
        return json({ status: 'COMPLETED', completedAt: '2026-08-26T10:30:00' })
      }
      return json({})
    })

    await page.goto(`/project/${project.id}/drafting`)

    // 打开抽屉（任务看板「AI 评分标准解析」按钮；.first() 防双布局容器同时渲染触发 strict mode）
    await page.locator('[data-test="score-parse-button"]').first().click()
    const drawer = page.locator('.score-parse-drawer')
    await expect(drawer.locator('[data-test="score-parse-content"]')).toBeVisible({ timeout: 15000 })

    // 契约 1：el-drawer__body 必须是 flex 列布局（scoped :deep 失效回归防护）
    const bodyDisplay = await page.evaluate(() => {
      const body = document.querySelector('.score-parse-drawer .el-drawer__body')
      const style = getComputedStyle(body)
      return { display: style.display, flexDirection: style.flexDirection }
    })
    expect(bodyDisplay.display).toBe('flex')
    expect(bodyDisplay.flexDirection).toBe('column')

    // 前置：内容必须高于可视区（否则滚动/漂移问题无从谈起）
    const geom0 = await drawer.locator('[data-test="score-parse-content"]').evaluate((el) => ({
      clientHeight: el.clientHeight,
      scrollHeight: el.scrollHeight,
    }))
    expect(geom0.scrollHeight).toBeGreaterThan(geom0.clientHeight)

    // 触发重新解析 → 遮罩出现
    await drawer.locator('[data-test="score-parse-reparse"]').click()
    await expect(drawer.locator('.scoring-overlay.show')).toBeVisible({ timeout: 10000 })

    // 契约 2：遮罩提示文字在可视区内（而非全部内容中点）
    const textRect = await drawer.locator('[data-test="score-parse-overlay-text"]').boundingBox()
    expect(textRect).not.toBeNull()
    expect(textRect.y).toBeGreaterThan(0)
    expect(textRect.y).toBeLessThan(page.viewportSize().height)

    // 契约 3：遮罩期间内容区滚动被锁定（.scroll-locked + 轮询中提示不漂出可视区）
    const wrapper = drawer.locator('[data-test="score-parse-content"]')
    await expect(wrapper).toHaveClass(/scroll-locked/)
    expect(await wrapper.evaluate((el) => getComputedStyle(el).overflowY)).toBe('hidden')
    await wrapper.hover()
    await page.mouse.wheel(0, 900)
    // 条件等待：轮询 scrollTop 连续 100ms 保持稳定（替代固定 waitForTimeout），再断言锁定生效未发生滚动
    await wrapper.evaluate(async (el) => {
      let last = el.scrollTop
      let stableSince = performance.now()
      while (performance.now() - stableSince < 100) {
        await new Promise((r) => requestAnimationFrame(r))
        if (el.scrollTop !== last) {
          last = el.scrollTop
          stableSince = performance.now()
        }
      }
    })
    expect(await wrapper.evaluate((el) => el.scrollTop)).toBe(0)
    const textRectAfterWheel = await drawer.locator('[data-test="score-parse-overlay-text"]').boundingBox()
    expect(textRectAfterWheel.y).toBeGreaterThan(0)
    expect(textRectAfterWheel.y).toBeLessThan(page.viewportSize().height)

    // 解析完成后遮罩消失、滚动解锁
    await expect(drawer.locator('.scoring-overlay.show')).toHaveCount(0, { timeout: 20000 })
    await expect(wrapper).not.toHaveClass(/scroll-locked/)
  })
})
