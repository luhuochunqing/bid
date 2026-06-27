#!/usr/bin/env node
// Input: backend API base URL, login credentials, CRM matching criteria
// Output: exit code 0/1 and diagnostic logs for the CRM search-by-tender probe
// Pos: scripts/ - Production health probe for CRM opportunity selector
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * CRM 商机选择器真实 API 探针。
 *
 * 用途：在部署环境中定期/手动验证后端 `search-by-tender` 能按蓝图规则
 * （招标主体 + 报名截止时间 + 开标时间）从真实 CRM 查到匹配商机。
 *
 * 必要环境变量：
 *   PROBE_API_BASE_URL    后端地址，例如 http://127.0.0.1:18089
 *   PROBE_USERNAME        登录用户名
 *   PROBE_PASSWORD        登录密码
 *   PROBE_GROUP_NAME      招标主体/CRM groupName，例如 "山东海化集团有限公司"
 *   PROBE_EVALUATION_DATE 评标日期 yyyy-MM-dd，需与 CRM 商机 evaluationTime 一致
 *
 * 可选环境变量：
 *   PROBE_EXPECTED_OPPORTUNITY_NAME  期望看到的商机名称（精确包含匹配）
 *   PROBE_PAGE_SIZE         默认 20
 *
 * 退出码：0=探测通过；1=探测失败
 */

const apiBaseUrl = process.env.PROBE_API_BASE_URL
const username = process.env.PROBE_USERNAME
const password = process.env.PROBE_PASSWORD
const groupName = process.env.PROBE_GROUP_NAME
const evaluationDate = process.env.PROBE_EVALUATION_DATE
const expectedName = process.env.PROBE_EXPECTED_OPPORTUNITY_NAME
const pageSize = Number(process.env.PROBE_PAGE_SIZE || '20')

function required(value, name) {
  if (!value) {
    console.error(`[crm-probe] 缺少必要环境变量: ${name}`)
    process.exit(1)
  }
}

required(apiBaseUrl, 'PROBE_API_BASE_URL')
required(username, 'PROBE_USERNAME')
required(password, 'PROBE_PASSWORD')
required(groupName, 'PROBE_GROUP_NAME')
required(evaluationDate, 'PROBE_EVALUATION_DATE')

async function requestJson(path, options = {}) {
  const url = `${apiBaseUrl}${path}`
  const response = await fetch(url, options)
  const text = await response.text()
  let payload = null
  try {
    payload = JSON.parse(text)
  } catch {
    payload = { raw: text }
  }
  if (!response.ok) {
    throw new Error(`${options.method || 'GET'} ${url} -> ${response.status}: ${text.substring(0, 500)}`)
  }
  return payload
}

function extractAccessToken(response) {
  const setCookie = response.headers.get('set-cookie') || ''
  const match = setCookie.match(/access_token=([^;]+)/)
  if (!match) {
    throw new Error('登录响应缺少 access_token cookie')
  }
  return match[1]
}

async function login() {
  const response = await fetch(`${apiBaseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(`登录失败 ${response.status}: ${JSON.stringify(payload)}`)
  }
  return extractAccessToken(response)
}

async function probe() {
  const token = await login()
  console.log(`[crm-probe] 登录成功，准备探测 groupName=${groupName} evaluationDate=${evaluationDate}`)

  const body = {
    tenderer: groupName,
    registrationDeadline: `${evaluationDate}T23:59:00`,
    bidOpeningTime: `${evaluationDate}T10:00:00`,
    pageIndex: 1,
    pageSize,
  }

  const payload = await requestJson('/api/xiyu/crm/chances/search-by-tender', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(body),
  })

  const data = payload?.data
  const list = data?.list || []
  const totalCount = data?.totalCount ?? list.length

  console.log(`[crm-probe] 返回 totalCount=${totalCount} list.length=${list.length}`)
  if (list.length > 0) {
    console.log('[crm-probe] 前 3 条商机：')
    list.slice(0, 3).forEach((item, idx) => {
      console.log(`  ${idx + 1}. id=${item.id} code=${item.code} name=${item.name} groupName=${item.groupName} evaluationTime=${item.evaluationTime}`)
    })
  }

  if (totalCount === 0 || list.length === 0) {
    console.error('[crm-probe] ❌ 未查到匹配商机，可能查询条件或 CRM 数据已变更')
    process.exit(1)
  }

  if (expectedName) {
    const found = list.some(item => item.name && String(item.name).includes(expectedName))
    if (!found) {
      console.error(`[crm-probe] ❌ 返回商机中未包含期望名称: ${expectedName}`)
      process.exit(1)
    }
    console.log(`[crm-probe] ✅ 找到期望商机: ${expectedName}`)
  }

  console.log('[crm-probe] ✅ 探测通过')
}

probe().catch(err => {
  console.error('[crm-probe] ❌ 探测失败:', err.message)
  process.exit(1)
})
