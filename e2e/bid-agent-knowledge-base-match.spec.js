// Input: Playwright E2E framework, real backend API, auth-helpers, project-fixtures
// Output: bid-agent-knowledge-base-match.spec.js - E2E coverage for four-library matching and full analysis
// Pos: e2e/ - Playwright end-to-end coverage
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession, injectSession } from './auth-helpers.js'
import { createProjectFixture, authedJson } from './support/project-fixtures.js'

const suffix = Date.now()

/**
 * Seed a qualification entry into the knowledge base.
 * Mirrors the pattern from knowledge-qualification-flow.spec.js.
 */
async function seedQualification(session) {
  const res = await fetch(`${apiBaseUrl}/api/knowledge/qualifications`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.token}`,
    },
    body: JSON.stringify({
      name: `E2E ISO9001质量管理 ${suffix}`,
      certificateNo: `E2E-QUAL-${suffix}`,
      level: 'AAA',
      issuer: 'E2E 测试发证机关',
      agency: 'E2E 测试代理机构',
      agencyContact: '13800000000',
      certScope: 'E2E 测试认证范围',
      holderName: 'E2E 测试持有人',
      issueDate: '2024-01-01',
      expiryDate: '2027-12-31',
      status: 'valid',
    }),
  })
  return res.ok
}

/**
 * Seed a personnel record with a certificate into the knowledge base.
 * Personnel controller requires bid_admin/bid_lead/bid_specialist authority.
 */
async function seedPersonnel(session) {
  const res = await fetch(`${apiBaseUrl}/api/knowledge/personnel`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.token}`,
    },
    body: JSON.stringify({
      name: `E2E 张工程师 ${suffix}`,
      employeeNumber: `E2E-${suffix}`,
      departmentCode: 'BID',
      departmentName: '投标部',
      gender: '男',
      entryDate: '2020-01-01',
      phone: '13800000000',
      education: '本科',
      technicalTitle: '高级工程师',
      educations: [
        {
          schoolName: 'E2E 测试大学',
          startDate: '2016-09-01',
          endDate: '2020-06-30',
          highestEducation: '本科',
          studyForm: '全日制',
          major: '土木工程',
          isHighestEducationSchool: true,
        },
      ],
      certificates: [
        {
          name: '一级建造师',
          certificateNumber: `E2E-CERT-${suffix}`,
          type: 'CONSTRUCTOR',
          issueDate: '2022-06-01',
          expiryDate: '2027-12-31',
        },
      ],
    }),
  })
  return res.ok
}

/**
 * Seed a brand authorization entry into the knowledge base.
 */
async function seedBrandAuth(session) {
  const res = await fetch(`${apiBaseUrl}/api/knowledge/brand-auth`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.token}`,
    },
    body: JSON.stringify({
      authorizationType: 'MANUFACTURER',
      productLine: 'TOOLS',
      brandId: `E2E-BRAND-${suffix}`,
      brandName: `E2E 测试品牌 ${suffix}`,
      importDomestic: '国产',
      manufacturerName: `E2E 测试制造商 ${suffix}`,
      authStartDate: '2024-01-01',
      authEndDate: '2027-12-31',
    }),
  })
  return res.ok
}

/**
 * Seed a performance record into the knowledge base.
 */
async function seedPerformance(session) {
  const res = await fetch(`${apiBaseUrl}/api/knowledge/performance`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.token}`,
    },
    body: JSON.stringify({
      contractName: `E2E 测试合同项目 ${suffix}`,
      signingEntity: `E2E 测试签约主体 ${suffix}`,
      groupCompany: 'E2E 集团',
      customerType: 'PRIVATE_ENTERPRISE',
      industry: '制造业',
      projectType: 'OTHER',
      dockingMethod: 'EMALL',
      customerLevel: 'GROUP',
      signingDate: '2024-06-01',
      expiryDate: '2027-12-31',
      totalExpiryDate: '2027-12-31',
      contactPerson: 'E2E 联系人',
      contactInfo: '13800000000',
      territory: '华东',
      customerAddress: 'E2E 测试客户地址',
      xiyuProjectManager: 'E2E 项目经理',
      hasBidNotice: false,
      remarks: 'E2E 测试业绩记录',
      attachments: [
        {
          fileName: 'E2E 合同协议.pdf',
          fileUrl: 'e2e://test/contract.pdf',
          fileType: 'CONTRACT_AGREEMENT',
        },
      ],
    }),
  })
  return res.ok
}

test.describe('bid agent knowledge base match and full analysis', () => {
  let session
  let project
  let seededLibraries = {}

  test.beforeAll(async () => {
    // 使用 bid_admin 角色以确保四库 seeding 权限
    session = await ensureApiSession({
      username: `e2e_ba_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E Bid Agent Admin',
    })

    project = await createProjectFixture(session, '四库联动')

    // Seed knowledge base data — gracefully handle permission failures
    const [qualOk, personnelOk, brandOk, perfOk] = await Promise.all([
      seedQualification(session).catch(() => false),
      seedPersonnel(session).catch(() => false),
      seedBrandAuth(session).catch(() => false),
      seedPerformance(session).catch(() => false),
    ])

    seededLibraries = {
      qualification: qualOk,
      personnel: personnelOk,
      brandAuth: brandOk,
      performance: perfOk,
    }

    // Wait for seeded data to propagate
    if (qualOk || personnelOk || brandOk || perfOk) {
      await new Promise((r) => setTimeout(r, 1500))
    }
  })

  test('full analysis API returns structured response with all dimensions', async ({ page }) => {
    await injectSession(page, session)
    const projectId = String(project.id)

    // Intercept the full-analysis API call to validate response structure
    const responsePromise = page.waitForResponse(
      (resp) => resp.url().includes('/bid-agent/full-analysis') && resp.status() === 200,
      { timeout: 30000 },
    )

    await page.goto(`/project/${projectId}`)
    await expect(page).toHaveURL(/\/project\/\d+$/)

    // Switch to "标书制作" tab which contains the bid agent button
    await page.getByRole('tab', { name: '标书制作' }).click()

    // Open the BidAgent drawer via the "AI 生成初稿" button
    await page.getByRole('button', { name: /AI 生成初稿/ }).click()
    await expect(page.locator('.bid-agent-drawer')).toBeVisible({ timeout: 10000 })

    // Click "AI 评分标准一键解析" to trigger full analysis
    await page.getByRole('button', { name: /AI 评分标准一键解析/ }).click()

    // Wait for API response
    const response = await responsePromise
    const body = await response.json()

    // Validate top-level structure
    expect(body?.success).toBeTruthy()
    const data = body.data
    expect(data).toBeTruthy()

    // knowledgeBaseMatch should exist (even if items are empty when no requirements)
    expect(data).toHaveProperty('knowledgeBaseMatch')

    // riskSummary should always be present with numeric counts
    expect(data).toHaveProperty('riskSummary')
    expect(typeof data.riskSummary.redLineCount).toBe('number')
    expect(typeof data.riskSummary.unsatisfiedCount).toBe('number')
    expect(typeof data.riskSummary.attentionCount).toBe('number')

    // Other dimension results should exist
    expect(data).toHaveProperty('scoringCriteria')
    expect(data).toHaveProperty('technicalRequirements')
    expect(data).toHaveProperty('commercialRequirements')
    expect(data).toHaveProperty('riskClassification')
  })

  test('risk summary banner renders after full analysis', async ({ page }) => {
    await injectSession(page, session)
    const projectId = String(project.id)

    await page.goto(`/project/${projectId}`)
    // Switch to "标书制作" tab which contains the bid agent button
    await page.getByRole('tab', { name: '标书制作' }).click()
    await page.getByRole('button', { name: /AI 生成初稿/ }).click()
    await expect(page.locator('.bid-agent-drawer')).toBeVisible({ timeout: 10000 })

    // Trigger full analysis and wait for completion
    await page.getByRole('button', { name: /AI 评分标准一键解析/ }).click()

    // Wait for the loading state to finish (button loading attribute clears)
    await page.waitForResponse(
      (resp) => resp.url().includes('/bid-agent/full-analysis') && resp.status() === 200,
      { timeout: 30000 },
    )

    // Wait for risk summary section to appear
    const riskSummary = page.locator('.risk-summary')
    await expect(riskSummary).toBeVisible({ timeout: 15000 })

    // Verify header text
    await expect(page.getByText('风险总览')).toBeVisible()

    // "X 项待关注" total text should be present
    await expect(page.getByText(/\d+ 项待关注/)).toBeVisible()

    // At least one risk card should be visible
    // (either risk-card-danger, risk-card-unsatisfied, risk-card-attention, or risk-card-ok)
    const cardCount = await page.locator('.risk-card').count()
    expect(cardCount).toBeGreaterThan(0)
  })

  test('knowledge base match panel shows four library tabs', async ({ page }) => {
    await injectSession(page, session)
    const projectId = String(project.id)

    await page.goto(`/project/${projectId}`)
    // Switch to "标书制作" tab which contains the bid agent button
    await page.getByRole('tab', { name: '标书制作' }).click()
    await page.getByRole('button', { name: /AI 生成初稿/ }).click()
    await expect(page.locator('.bid-agent-drawer')).toBeVisible({ timeout: 10000 })

    // Trigger full analysis
    await page.getByRole('button', { name: /AI 评分标准一键解析/ }).click()
    await page.waitForResponse(
      (resp) => resp.url().includes('/bid-agent/full-analysis') && resp.status() === 200,
      { timeout: 30000 },
    )

    // Verify "知识库匹配" section header
    await expect(page.getByText('知识库匹配')).toBeVisible({ timeout: 15000 })

    // Four tabs should be present in the tabs component
    const tabs = page.locator('.kb-tabs .el-tabs__item')
    const tabTexts = await tabs.allTextContents()

    expect(tabTexts.some((t) => t.includes('资质库'))).toBeTruthy()
    expect(tabTexts.some((t) => t.includes('人员库'))).toBeTruthy()
    expect(tabTexts.some((t) => t.includes('品牌授权'))).toBeTruthy()
    expect(tabTexts.some((t) => t.includes('业绩库'))).toBeTruthy()
  })

  test('risk red line panel renders with three risk levels', async ({ page }) => {
    await injectSession(page, session)
    const projectId = String(project.id)

    await page.goto(`/project/${projectId}`)
    // Switch to "标书制作" tab which contains the bid agent button
    await page.getByRole('tab', { name: '标书制作' }).click()
    await page.getByRole('button', { name: /AI 生成初稿/ }).click()
    await expect(page.locator('.bid-agent-drawer')).toBeVisible({ timeout: 10000 })

    // Trigger full analysis
    await page.getByRole('button', { name: /AI 评分标准一键解析/ }).click()
    await page.waitForResponse(
      (resp) => resp.url().includes('/bid-agent/full-analysis') && resp.status() === 200,
      { timeout: 30000 },
    )

    // Verify "风险与废标红线" section header
    await expect(page.getByText('风险与废标红线')).toBeVisible({ timeout: 15000 })

    // If risk items exist, verify they have proper level tags
    const riskItems = page.locator('.risk-item')
    const riskItemCount = await riskItems.count()

    if (riskItemCount > 0) {
      // Each risk item should have a tag (废标红线, 一般风险, or 信息)
      const tags = page.locator('.risk-item .el-tag')
      const tagCount = await tags.count()
      expect(tagCount).toBeGreaterThan(0)

      // Verify tag text content is one of the three levels
      for (let i = 0; i < tagCount; i++) {
        const text = await tags.nth(i).textContent()
        expect(['废标红线', '一般风险', '信息']).toContain(text)
      }
    }
  })

  test('knowledge base match standalone endpoint returns valid response', async () => {
    // Validate the standalone knowledge-base-match endpoint independently
    const projectId = String(project.id)
    const payload = await authedJson(
      `/api/projects/${projectId}/bid-agent/knowledge-base-match`,
      session.token,
    )

    expect(payload?.success).toBeTruthy()
    const data = payload.data

    // Should have all four library match results
    expect(data).toHaveProperty('qualificationMatch')
    expect(data).toHaveProperty('personnelMatch')
    expect(data).toHaveProperty('brandAuthMatch')
    expect(data).toHaveProperty('performanceMatch')

    // Should have summary with counts
    expect(data).toHaveProperty('summary')
    if (data.summary) {
      expect(typeof data.summary.totalSatisfied).toBe('number')
      expect(typeof data.summary.totalAttention).toBe('number')
      expect(typeof data.summary.totalUnsatisfied).toBe('number')
    }
  })

  test('full analysis endpoint matches standalone endpoints', async () => {
    // Call full-analysis and knowledge-base-match independently, then compare structure
    const projectId = String(project.id)

    const [fullPayload, kbPayload] = await Promise.all([
      authedJson(`/api/projects/${projectId}/bid-agent/full-analysis`, session.token),
      authedJson(`/api/projects/${projectId}/bid-agent/knowledge-base-match`, session.token),
    ])

    expect(fullPayload?.success).toBeTruthy()
    expect(kbPayload?.success).toBeTruthy()

    // Both should return knowledgeBaseMatch with same structure
    const fullKb = fullPayload.data?.knowledgeBaseMatch
    const standaloneKb = kbPayload.data

    expect(fullKb).toHaveProperty('qualificationMatch')
    expect(standaloneKb).toHaveProperty('qualificationMatch')
    expect(fullKb).toHaveProperty('summary')
    expect(standaloneKb).toHaveProperty('summary')
  })
})
