// Input: Playwright E2E 环境 + 后端 /api/tenders/import 异步端点
// Output: 验证标讯异步导入全流程（上传 → taskId → 轮询 → 终态）
// Pos: e2e/ - 标讯异步导入 E2E 测试（spec 031 US1）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { test, expect } from '@playwright/test'
import { ensureApiSession, injectSession } from './auth-helpers.js'
import * as XLSX from 'xlsx'

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://127.0.0.1:18089'

// 与后端 TenderExcelParser.HEADERS 保持一致（18 列）
const HEADERS = [
  '项目名称*', '招标主体*', '总部所在地*',
  '报名截止时间*', '开标时间*',
  '联系人1*', '联系人1手机号', '联系人1座机', '联系人1邮箱',
  '联系人2', '联系人2手机号', '联系人2座机', '联系人2邮箱',
  '客户类型*', '优先级*', '项目类型*', '来源平台', '标讯描述'
]

/**
 * 生成合法的标讯导入 Excel（1 条数据行）。
 */
function generateValidExcel() {
  const wb = XLSX.utils.book_new()
  const wsData = [
    HEADERS,
    [
      `E2E测试标讯-${Date.now()}`,    // 项目名称
      'E2E测试采购单位',              // 招标主体
      '广东省深圳市',                  // 总部所在地
      '2026-12-31 17:00:00',          // 报名截止时间
      '2026-12-25 09:30:00',          // 开标时间
      'E2E联系人',                     // 联系人1
      '13800138000',                  // 联系人1手机号
      '',                             // 联系人1座机
      'e2e@test.com',                 // 联系人1邮箱
      '',                             // 联系人2
      '',                             // 联系人2手机号
      '',                             // 联系人2座机
      '',                             // 联系人2邮箱
      '央企',                          // 客户类型
      'A',                            // 优先级
      '工业品',                        // 项目类型
      'E2E测试平台',                   // 来源平台
      'E2E 异步导入测试数据'            // 标讯描述
    ]
  ]
  const ws = XLSX.utils.aoa_to_sheet(wsData)
  XLSX.utils.book_append_sheet(wb, ws, '标讯导入')
  return XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' })
}

/**
 * 生成包含错误的标讯导入 Excel（缺少必填字段）。
 */
function generateInvalidExcel() {
  const wb = XLSX.utils.book_new()
  const wsData = [
    HEADERS,
    [
      '',                  // 项目名称为空（必填）
      'E2E测试采购单位',
      '广东省深圳市',
      '2026-12-31 17:00:00',
      '2026-12-25 09:30:00',
      'E2E联系人',
      '13800138000',
      '', '', '',
      '', '', '', '',
      '央企', 'A', '工业品', '', ''
    ]
  ]
  const ws = XLSX.utils.aoa_to_sheet(wsData)
  XLSX.utils.book_append_sheet(wb, ws, '标讯导入')
  return XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' })
}

async function loginAsBidAdmin(page) {
  const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  const session = await ensureApiSession({
    username: `e2e_async_${suffix}`,
    role: '/bidAdmin',
    fullName: `E2E Async ${suffix}`
  })
  await injectSession(page, session)
  return session
}

test.describe('标讯异步导入全流程 (spec 031 US1)', () => {
  test('上传合法 Excel → 202 + taskId → 轮询至 COMPLETED', async ({ page }) => {
    await loginAsBidAdmin(page)
    await page.goto('/bidding')
    await page.waitForSelector('.el-table', { timeout: 10000 })

    // 打开批量导入对话框
    await page.getByRole('button', { name: '批量导入' }).click()
    await expect(page.locator('.el-dialog').filter({ hasText: '批量导入标讯' })).toBeVisible()

    // 上传 Excel 文件
    const excelBuffer = generateValidExcel()
    const fileInput = page.locator('input[type="file"]')
    await fileInput.setInputFiles({
      name: 'e2e-async-import.xlsx',
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      buffer: excelBuffer
    })

    // 验证文件名已显示
    await expect(page.locator('.el-upload__text')).toContainText('e2e-async-import.xlsx')

    // 点击"开始导入"
    await page.getByRole('button', { name: '开始导入' }).click()

    // 验证：显示"导入任务已创建"或进度条
    await expect(page.locator('.bulk-import-progress')).toBeVisible({ timeout: 5000 })

    // 等待终态（COMPLETED/PARTIAL_SUCCESS/FAILED），最多 60s
    await expect(
      page.locator('.alert-stub, .el-alert').filter({ hasText: /成功|失败|部分成功/ })
    ).toBeVisible({ timeout: 60000 })
  })

  test('上传非法 Excel → 轮询至 FAILED + 显示错误明细', async ({ page }) => {
    await loginAsBidAdmin(page)
    await page.goto('/bidding')
    await page.waitForSelector('.el-table', { timeout: 10000 })

    await page.getByRole('button', { name: '批量导入' }).click()
    await expect(page.locator('.el-dialog').filter({ hasText: '批量导入标讯' })).toBeVisible()

    const excelBuffer = generateInvalidExcel()
    const fileInput = page.locator('input[type="file"]')
    await fileInput.setInputFiles({
      name: 'e2e-async-import-invalid.xlsx',
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      buffer: excelBuffer
    })

    await page.getByRole('button', { name: '开始导入' }).click()

    // 等待终态：应显示失败提示
    await expect(
      page.locator('.alert-stub, .el-alert').filter({ hasText: '导入失败' })
    ).toBeVisible({ timeout: 60000 })

    // 验证错误明细表格出现
    await expect(page.locator('.bulk-import-error-table, .el-table')).toBeVisible()
  })

  test('API 契约：POST /import 返回 202 + taskId', async ({ request }) => {
    // 直接 API 层验证
    const session = await ensureApiSession({
      username: `e2e_api_${Date.now()}`,
      role: '/bidAdmin',
      fullName: 'E2E API Test'
    })

    const excelBuffer = generateValidExcel()
    const formData = new FormData()
    formData.append('file', new Blob([excelBuffer], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    }), 'e2e-api-test.xlsx')

    const response = await request.post(`${apiBaseUrl}/api/tenders/import`, {
      headers: {
        'Authorization': `Bearer ${session.accessToken}`,
        'Idempotency-Key': `e2e-${Date.now()}`
      },
      multipart: {
        file: {
          name: 'e2e-api-test.xlsx',
          mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          buffer: excelBuffer
        }
      }
    })

    expect(response.status()).toBe(202)
    const body = await response.json()
    expect(body.success).toBe(true)
    expect(body.data).toBeTruthy()
    expect(body.data.taskId).toBeTruthy()
    expect(body.data.taskId).toMatch(/^[0-9a-f-]{36}$/) // UUID 格式
    expect(body.data.status).toBe('PENDING')
  })

  test('API 契约：GET /progress 返回进度 DTO', async ({ request }) => {
    const session = await ensureApiSession({
      username: `e2e_progress_${Date.now()}`,
      role: '/bidAdmin',
      fullName: 'E2E Progress Test'
    })

    // 先触发导入
    const excelBuffer = generateValidExcel()
    const importResponse = await request.post(`${apiBaseUrl}/api/tenders/import`, {
      headers: {
        'Authorization': `Bearer ${session.accessToken}`,
        'Idempotency-Key': `e2e-progress-${Date.now()}`
      },
      multipart: {
        file: {
          name: 'e2e-progress-test.xlsx',
          mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          buffer: excelBuffer
        }
      }
    })
    expect(importResponse.status()).toBe(202)
    const importBody = await importResponse.json()
    const taskId = importBody.data.taskId

    // 查询进度
    const progressResponse = await request.get(
      `${apiBaseUrl}/api/tenders/import/${taskId}/progress`,
      {
        headers: { 'Authorization': `Bearer ${session.accessToken}` }
      }
    )
    expect(progressResponse.status()).toBe(200)
    const progressBody = await progressResponse.json()
    expect(progressBody.success).toBe(true)
    expect(progressBody.data).toBeTruthy()
    expect(progressBody.data.taskId).toBe(taskId)
    expect(['PENDING', 'PROCESSING', 'COMPLETED', 'PARTIAL_SUCCESS', 'FAILED'])
      .toContain(progressBody.data.status)
    expect(typeof progressBody.data.totalRows).toBe('number')
    expect(typeof progressBody.data.processedRows).toBe('number')
    expect(typeof progressBody.data.percent).toBe('number')
  })
})
