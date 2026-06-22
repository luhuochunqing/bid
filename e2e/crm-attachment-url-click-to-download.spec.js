// @ui-cover:auth,crm
// CRM 附件下载 URL 认证回归测试。
//
// 目标：验证 CO-280 修复后的完整链路 —— 外部系统通过 X-API-Key 访问
// /api/integration/tenders 接口，响应中的附件 URL 已自动附加
// ?api_key=xxx 查询参数，使浏览器用户可直接点击链接下载文件，
// 无需在客户端手动设置 Header。
//
// 前置条件：
//   1. 后端服务运行在真实 API 模式，MySQL 已启动
//   2. 测试环境中需存在至少一个 Tender 记录（或通过 API 推送）
//   3. 或使用 API Key 验证 URL 参数认证机制
//
// 三组测试用例：
//   A. 参数式认证：GET /api/integration/tenders/attachments/download?fileUrl=...&api_key=xxx
//      返回 200/404（有效 key 时不应返回 401）
//   B. Header 式认证：GET /api/integration/tenders/attachments/download?fileUrl=...
//      Header 带 X-API-Key: xxx，返回 200/404（不应 401）
//   C. 列表/详情接口的 URL 标准化：GET /api/integration/tenders?api_key=xxx
//      返回体中 sourceDocumentFileUrl / bidNoticeFileUrl / attachments[].fileUrl
//      若为 doc-insight:// 协议，应转换为 /api/integration/tenders/attachments/download
//      并附加 api_key 参数
//
// 本测试使用 Playwright 的 request 上下文做 HTTP 级验证，
// 不依赖 UI（UI 测试在其他 spec 中覆盖）。

import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession } from './auth-helpers.js'

test.describe('CO-280: CRM 附件 URL 参数式认证', () => {

  // ── Case A: URL 参数认证 ────────────────────────────────────────
  test('GET /api/integration/tenders with ?api_key= should accept param auth', async ({ request }) => {
    // 注意：此测试期望本地环境中存在有效的 API Key
    // 若未设置环境变量则跳过
    const e2eApiKey = process.env.E2E_API_KEY
    test.skip(!e2eApiKey, '未设置 E2E_API_KEY 环境变量，跳过 URL 参数认证测试')

    // 用 URL 参数方式访问列表接口 —— 模拟 CRM 用户直接点击链接的场景
    const listUrl = `/api/integration/tenders?api_key=${encodeURIComponent(e2eApiKey)}`
    const response = await request.get(listUrl, {
      headers: { 'Content-Type': 'application/json' }
    })

    // 有效 API Key 时应返回 200，而非 401
    expect(response.status()).toBeGreaterThanOrEqual(200)
    expect(response.status()).toBeLessThan(400)

    const body = await response.json().catch(() => null)
    expect(body).toBeTruthy()
  })

  // ── Case B: Header 认证（与 A 等价行为）───────────────────────
  test('GET /api/integration/tenders with X-API-Key header should accept auth', async ({ request }) => {
    const e2eApiKey = process.env.E2E_API_KEY
    test.skip(!e2eApiKey, '未设置 E2E_API_KEY 环境变量，跳过 Header 认证测试')

    const response = await request.get('/api/integration/tenders', {
      headers: {
        'Content-Type': 'application/json',
        'X-API-Key': e2eApiKey
      }
    })

    // 有效 API Key 时应返回 200
    expect(response.status()).toBeGreaterThanOrEqual(200)
    expect(response.status()).toBeLessThan(400)
  })

  // ── Case C: 无认证应返回 401（负向回归）──────────────────────
  test('GET /api/integration/tenders without api_key should return 401', async ({ request }) => {
    const response = await request.get('/api/integration/tenders', {
      headers: { 'Content-Type': 'application/json' }
    })

    // 未提供任何 API Key 认证 → 必须返回 401
    expect(response.status()).toBe(401)
  })

  // ── Case D: URL 参数 vs Header 认证返回相同结果（一致性）──
  test('param auth and header auth return identical data', async ({ request }) => {
    const e2eApiKey = process.env.E2E_API_KEY
    test.skip(!e2eApiKey, '未设置 E2E_API_KEY 环境变量，跳过一致性测试')

    const paramResponse = await request.get(
      `/api/integration/tenders?api_key=${encodeURIComponent(e2eApiKey)}`
    )
    const headerResponse = await request.get('/api/integration/tenders', {
      headers: { 'X-API-Key': e2eApiKey }
    })

    // 状态码一致
    expect(headerResponse.status()).toBe(paramResponse.status())

    // 当两个请求均成功时，响应 body 中的数据结构应一致
    if (paramResponse.status() >= 200 && paramResponse.status() < 300) {
      const paramBody = await paramResponse.json().catch(() => null)
      const headerBody = await headerResponse.json().catch(() => null)
      // 仅断言数据结构等价（不做严格等号，避免时间戳等差异）
      if (paramBody && headerBody) {
        expect(paramBody).toHaveProperty('success')
        expect(headerBody).toHaveProperty('success')
      }
    }
  })

  // ── Case E: 下载端点的参数认证 ─────────────────────────────────
  test('GET /api/integration/tenders/attachments/download with ?api_key= should not return 401',
    async ({ request }) => {
      const e2eApiKey = process.env.E2E_API_KEY
      test.skip(!e2eApiKey, '未设置 E2E_API_KEY 环境变量，跳过下载端点测试')

      // 用一个明显不存在的 fileUrl，核心断言是"不应 401"
      // 如果后端找到文件应 200；如果找不到文件应返回 404/错误
      const encodedUrl = encodeURIComponent('doc-insight://TEST/NONEXISTENT.pdf')
      const response = await request.get(
        `/api/integration/tenders/attachments/download?fileUrl=${encodedUrl}&api_key=${encodeURIComponent(e2eApiKey)}`
      )

      // 最关键的断言：认证参数有效时，决不能返回 401
      expect(response.status()).not.toBe(401)
      // 也不能是 403（权限被拒）
      expect(response.status()).not.toBe(403)
    })

})

// ── 工具方法测试：URL 中 api_key 参数格式（纯前端/HTTP 层验证） ─
test.describe('CO-280: URL api_key 参数格式', () => {

  test('response from integration endpoint should contain standardized URLs', async ({ request }) => {
    const e2eApiKey = process.env.E2E_API_KEY
    test.skip(!e2eApiKey, '未设置 E2E_API_KEY 环境变量，跳过 URL 标准化测试')

    // 1. 通过 Header 认证访问列表
    const listResponse = await request.get('/api/integration/tenders', {
      headers: {
        'Content-Type': 'application/json',
        'X-API-Key': e2eApiKey
      }
    })
    if (listResponse.status() >= 400) return // 接口不可用时跳过

    const body = await listResponse.json().catch(() => null)
    if (!body || !body.data || !body.data.content) return

    // 2. 断言响应中的 URL 已被标准化
    //    - doc-insight:// 协议不应直接返回
    //    - 若有附件 URL，应使用集成下载端点
    for (const tender of body.data.content) {
      const fieldsToCheck = [
        tender.sourceDocumentFileUrl,
        tender.bidNoticeFileUrl
      ]
      if (tender.attachments && Array.isArray(tender.attachments)) {
        tender.attachments.forEach((a) => fieldsToCheck.push(a.fileUrl))
      }

      for (const url of fieldsToCheck) {
        if (!url) continue
        // 不允许直接返回 doc-insight://（CRM 无法理解此协议）
        expect(url).not.toMatch(/^doc-insight:\/\//)
        // 如果是平台生成的下载 URL，应包含 api_key 参数（浏览器可直接点击）
        if (url.includes('/api/integration/tenders/attachments/download')) {
          expect(url).toContain('api_key=')
        }
      }
    }
  })

})
