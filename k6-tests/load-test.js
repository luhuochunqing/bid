// k6 性能测试脚本 — 西域数智化投标管理平台
// 用法: k6 run k6-tests/load-test.js
// 安装: brew install k6

import http from 'k6/http'
import { check, sleep, group } from 'k6'
import { Rate, Trend } from 'k6/metrics'

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:18080'
const ADMIN_USER = __ENV.ADMIN_USER || 'admin'
const ADMIN_PASS = __ENV.ADMIN_PASS || 'XiyuAdmin2026!'

const errorRate = new Rate('errors')
const loginTrend = new Trend('login_duration')
const tenderListTrend = new Trend('tender_list_duration')

export const options = {
  stages: [
    { duration: '30s', target: 10 },   // 逐步增加到 10 并发
    { duration: '1m', target: 50 },    // 增加到 50 并发
    { duration: '30s', target: 100 },  // 峰值 100 并发
    { duration: '30s', target: 0 },    // 逐步降到 0
  ],
  thresholds: {
    errors: ['rate<0.05'],            // 错误率 < 5%
    http_req_duration: ['p(95)<3000'], // 95% 请求 < 3s
  },
}

function getToken() {
  const res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({
    username: ADMIN_USER,
    password: ADMIN_PASS,
  }), { headers: { 'Content-Type': 'application/json' } })

  check(res, { 'login success': (r) => r.status === 200 })
  errorRate.add(res.status !== 200)
  loginTrend.add(res.timings.duration)

  if (res.status !== 200) return null
  return res.json().data.token
}

export default function () {
  const token = getToken()
  if (!token) { sleep(1); return }

  const authHeaders = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  }

  group('标讯模块', () => {
    // 标讯列表
    const listRes = http.get(`${BASE_URL}/api/tenders?page=0&size=10&sort=createdAt,desc`, { headers: authHeaders })
    check(listRes, { 'tender list ok': (r) => r.status === 200 })
    errorRate.add(listRes.status !== 200)
    tenderListTrend.add(listRes.timings.duration)
    sleep(0.5)

    // 标讯详情
    const ids = listRes.json('$.data.content[*].id') || [1]
    if (ids.length > 0) {
      const detailRes = http.get(`${BASE_URL}/api/tenders/${ids[0]}`, { headers: authHeaders })
      check(detailRes, { 'tender detail ok': (r) => r.status === 200 })
      errorRate.add(detailRes.status !== 200)
    }
    sleep(0.3)
  })

  group('项目模块', () => {
    const res = http.get(`${BASE_URL}/api/projects?page=0&size=10&sort=createdAt,desc`, { headers: authHeaders })
    check(res, { 'project list ok': (r) => r.status === 200 })
    errorRate.add(res.status !== 200)
    sleep(0.5)
  })

  group('知识库', () => {
    const res = http.get(`${BASE_URL}/api/knowledge/cases?page=0&size=10`, { headers: authHeaders })
    check(res, { 'knowledge list ok': (r) => r.status === 200 })
    errorRate.add(res.status !== 200)
    sleep(0.3)
  })

  group('数据分析', () => {
    const res = http.get(`${BASE_URL}/api/analytics/dashboard`, { headers: authHeaders })
    check(res, { 'dashboard ok': (r) => r.status === 200 })
    errorRate.add(res.status !== 200)
    sleep(0.5)
  })

  group('系统设置', () => {
    const res = http.get(`${BASE_URL}/api/settings`, { headers: authHeaders })
    check(res, { 'settings ok': (r) => r.status === 200 })
    errorRate.add(res.status !== 200)
    sleep(0.3)
  })
}
