// ============================================================================
// k6 性能压力测试脚本 — 西域数智化投标管理平台
// 支持场景: 多角色并发模拟、标讯高频检索与多维筛选、项目全流程协同、
//           知识库检索、数据分析大屏重聚合、受控业务数据写入
//
// 运行示例:
//   1. 冒烟测试 (1 VU / 30s):
//      STRESS_PROFILE=smoke k6 run k6-tests/load-test.js
//
//   2. 标准阶梯压测 (10 -> 50 -> 100 VU / 3.5m):
//      BASE_URL=http://127.0.0.1:18080 STRESS_PROFILE=standard k6 run k6-tests/load-test.js
//
//   3. 极限摸高压测 (20 -> 100 -> 200 VU / 5m):
//      BASE_URL=http://127.0.0.1:18080 STRESS_PROFILE=stress k6 run k6-tests/load-test.js
//
//   4. 自定义多用户账号池:
//      K6_USERS='[{"username":"admin","password":"XiyuAdmin2026!"},{"username":"zhangjingli","password":"123456"},{"username":"xiaowang","password":"123456"}]' k6 run k6-tests/load-test.js
// ============================================================================

import http from 'k6/http'
import { check, sleep, group } from 'k6'
import { Counter, Rate, Trend } from 'k6/metrics'

// 1. 基础配置与环境变量
const BASE_URL = (__ENV.BASE_URL || 'http://127.0.0.1:18080').replace(/\/$/, '')
const PROFILE = __ENV.STRESS_PROFILE || 'standard'
const ENABLE_WRITES = String(__ENV.ENABLE_WRITES || 'true').toLowerCase() === 'true'

// 2. 多用户账号池
const DEFAULT_USER_POOL = [
  { username: 'admin', password: __ENV.ADMIN_PASS || 'XiyuAdmin2026!', role: 'ADMIN' },
  { username: 'zhangjingli', password: '123456', role: 'MANAGER' },
  { username: 'xiaowang', password: '123456', role: 'STAFF' },
  { username: 'xiaozhao', password: '123456', role: 'AUDITOR' },
]

function getUserPool() {
  if (__ENV.K6_USERS) {
    try {
      const parsed = JSON.parse(__ENV.K6_USERS)
      if (Array.isArray(parsed) && parsed.length > 0) return parsed
    } catch (e) {
      console.warn('Failed to parse K6_USERS, fallback to default pool')
    }
  }
  return DEFAULT_USER_POOL
}

const userPool = getUserPool()

// 3. 压测指标采集定义 (Metrics)
const errorRate = new Rate('xiyu_error_rate')
const authSuccessRate = new Rate('xiyu_auth_success_rate')
const businessSuccessRate = new Rate('xiyu_business_success_rate')

const loginDuration = new Trend('xiyu_login_duration')
const tenderListDuration = new Trend('xiyu_tender_list_duration')
const tenderDetailDuration = new Trend('xiyu_tender_detail_duration')
const tenderCreateDuration = new Trend('xiyu_tender_create_duration')
const projectListDuration = new Trend('xiyu_project_list_duration')
const projectDetailDuration = new Trend('xiyu_project_detail_duration')
const knowledgeSearchDuration = new Trend('xiyu_knowledge_search_duration')
const dashboardDuration = new Trend('xiyu_dashboard_duration')

const totalRequestsCounter = new Counter('xiyu_total_requests')
const readOperationsCounter = new Counter('xiyu_read_ops')
const writeOperationsCounter = new Counter('xiyu_write_ops')

// 4. 阶梯加压配置 (Profiles)
const STAGE_CONFIGS = {
  smoke: [
    { duration: '10s', target: 1 },
    { duration: '20s', target: 1 },
  ],
  standard: [
    { duration: '30s', target: 10 },  // 预热阶段
    { duration: '1m', target: 50 },   // 日常业务基准
    { duration: '1m', target: 100 },  // 业务高峰负载
    { duration: '30s', target: 100 }, // 持续峰值
    { duration: '30s', target: 0 },   // 冷却恢复
  ],
  stress: [
    { duration: '30s', target: 20 },  // 快速预热
    { duration: '1m', target: 100 },  // 跃升至 100 并发
    { duration: '2m', target: 200 },  // 极限摸高 200 并发
    { duration: '1m', target: 200 },  // 持续施压
    { duration: '30s', target: 0 },   // 降载恢复
  ],
  endurance: [
    { duration: '1m', target: 50 },
    { duration: '10m', target: 50 },  // 持续中高负载稳定性检测
    { duration: '1m', target: 0 },
  ],
}

export const options = {
  stages: STAGE_CONFIGS[PROFILE] || STAGE_CONFIGS.standard,
  thresholds: {
    // 全局硬性 SLA 门禁
    xiyu_error_rate: ['rate<0.01'],              // 全局错误率 < 1%
    http_req_failed: ['rate<0.01'],              // HTTP 失败率 < 1%
    http_req_duration: ['p(95)<1500', 'p(99)<3000'], // 95% 请求 < 1.5s, 99% < 3s
    xiyu_auth_success_rate: ['rate>0.99'],        // 认证成功率 > 99%
    xiyu_business_success_rate: ['rate>0.99'],    // 业务成功率 > 99%
    // 关键高频接口专项 SLA
    xiyu_tender_list_duration: ['p(95)<1000'],
    xiyu_project_list_duration: ['p(95)<1000'],
    xiyu_dashboard_duration: ['p(95)<2000'],
  },
}

// 5. 仿真业务随机因子
const KEYWORDS = ['智慧', '采购', '改造', '系统', '框架', '平台', '数据', '服务', '集采', 'MRO']
const REGIONS = ['上海', '北京', '广州', '深圳', '成都', '武汉', '杭州', '南京']
const TENDER_STATUSES = ['PENDING_ASSIGNMENT', 'TRACKING', 'EVALUATED', 'BIDDING']
const INDUSTRIES = ['智慧园区', '电力能源', '智能制造', '轨道交通', '智慧城市', '医疗']

function randomItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)]
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min
}

// 6. 用户认证与 Token 获取
const STATIC_AUTH_TOKEN = __ENV.K6_AUTH_TOKEN || ''

function authenticate(user) {
  if (STATIC_AUTH_TOKEN) {
    return STATIC_AUTH_TOKEN
  }
  const started = Date.now()
  const payload = JSON.stringify({
    username: user.username,
    password: user.password,
  })

  const res = http.post(`${BASE_URL}/api/auth/login`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'auth_login' },
  })

  const duration = Date.now() - started
  loginDuration.add(duration)
  totalRequestsCounter.add(1)

  let token = res.json('data.token') || res.json('data.accessToken')
  if (!token && res.cookies && res.cookies.access_token && res.cookies.access_token.length > 0) {
    token = res.cookies.access_token[0].value
  }
  if (!token && res.headers['Set-Cookie']) {
    const match = res.headers['Set-Cookie'].match(/access_token=([^;]+)/)
    if (match) token = match[1]
  }

  const isSuccess = res.status === 200 && Boolean(token)
  authSuccessRate.add(Boolean(isSuccess))
  errorRate.add(!isSuccess)

  if (!isSuccess) {
    return null
  }
  return token
}

// 7. 测试前置初始化 (Pre-authenticate once)
export function setup() {
  if (STATIC_AUTH_TOKEN) {
    return { tokens: [{ user: 'static_admin', token: STATIC_AUTH_TOKEN }] }
  }
  const tokens = []
  for (const user of userPool) {
    const token = authenticate(user)
    if (token) {
      tokens.push({ user: user.username, token })
    }
  }
  if (tokens.length === 0) {
    console.warn('⚠️ Warning: Could not authenticate user pool via /api/auth/login directly. Falling back to anonymous/static mode.')
    return { tokens: [{ user: 'anonymous', token: '' }] }
  }
  return { tokens }
}

// 8. 主压测执行流 (VU Loop)
export default function (data) {
  const tokenList = (data && data.tokens && data.tokens.length > 0) ? data.tokens : [{ token: STATIC_AUTH_TOKEN }]
  const tokenObj = tokenList[__VU % tokenList.length]
  const token = tokenObj.token

  const authHeaders = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      'X-Stress-Test': 'true',
      'X-API-Key': `stress_vu_${__VU}`,
    },
  }

  // 场景权重分配 (模拟真实用户行为)
  const roll = Math.random()

  if (roll < 0.50) {
    // 场景 A: 标讯中心高频检索与详情流 (50% 流量)
    scenarioTenderJourney(authHeaders)
  } else if (roll < 0.75) {
    // 场景 B: 投标项目管理与协同 (25% 流量)
    scenarioProjectJourney(authHeaders)
  } else if (roll < 0.90) {
    // 场景 C: 知识库与数据分析大屏 (15% 流量)
    scenarioKnowledgeAndDashboard(authHeaders)
  } else {
    // 场景 D: 受控业务写入 (10% 流量)
    if (ENABLE_WRITES) {
      scenarioWriteOperations(authHeaders, tokenObj.user)
    } else {
      scenarioTenderJourney(authHeaders)
    }
  }

  // 模拟真实用户思考停顿 (0.3s ~ 0.8s)
  sleep(Math.random() * 0.5 + 0.3)
}

// ============================================================================
// 场景实现函数
// ============================================================================

function scenarioTenderJourney(authHeaders) {
  group('标讯中心检索链路', () => {
    // 1. 标讯列表 (混合多维筛选: 分页 + 状态 + 区域 + 关键词)
    const page = randomInt(0, 3)
    const keyword = randomItem(KEYWORDS)
    const region = randomItem(REGIONS)
    const status = randomItem(TENDER_STATUSES)

    const listUrl = `${BASE_URL}/api/tenders?page=${page}&size=10&keyword=${encodeURIComponent(keyword)}&region=${encodeURIComponent(region)}&status=${status}&sort=createdAt,desc`
    
    const startedList = Date.now()
    const listRes = http.get(listUrl, { ...authHeaders, tags: { name: 'tender_list_filtered' } })
    tenderListDuration.add(Date.now() - startedList)
    totalRequestsCounter.add(1)
    readOperationsCounter.add(1)

    const listOk = check(listRes, {
      'tender list status is 200': (r) => r.status === 200,
    })
    errorRate.add(!listOk)
    businessSuccessRate.add(listOk)

    sleep(0.3)

    // 2. 标讯详情查看 (从查询结果中获取有效 ID)
    let tenderId = null
    if (listOk) {
      const content = listRes.json('data.content')
      if (Array.isArray(content) && content.length > 0 && content[0].id) {
        tenderId = content[0].id
      }
    }

    if (tenderId) {
      const startedDetail = Date.now()
      const detailRes = http.get(`${BASE_URL}/api/tenders/${tenderId}`, { ...authHeaders, tags: { name: 'tender_detail' } })
      tenderDetailDuration.add(Date.now() - startedDetail)
      totalRequestsCounter.add(1)
      readOperationsCounter.add(1)

      const detailOk = check(detailRes, {
        'tender detail status is 200': (r) => r.status === 200,
      })
      errorRate.add(!detailOk)
      businessSuccessRate.add(detailOk)
    }
  })
}

function scenarioProjectJourney(authHeaders) {
  group('项目管理全链路', () => {
    // 1. 项目列表查询
    const startedList = Date.now()
    const listRes = http.get(`${BASE_URL}/api/projects?page=0&size=10&sort=createdAt,desc`, {
      ...authHeaders,
      tags: { name: 'project_list' },
    })
    projectListDuration.add(Date.now() - startedList)
    totalRequestsCounter.add(1)
    readOperationsCounter.add(1)

    const listOk = check(listRes, {
      'project list status is 200': (r) => r.status === 200,
    })
    errorRate.add(!listOk)
    businessSuccessRate.add(listOk)

    sleep(0.3)

    // 2. 项目详情及子模块协同
    let projectId = null
    if (listOk) {
      const data = listRes.json('data')
      const items = Array.isArray(data) ? data : (listRes.json('data.content') || [])
      if (items.length > 0 && items[0].id) {
        projectId = items[0].id
      }
    }

    if (projectId) {
      const startedDetail = Date.now()
      const detailRes = http.get(`${BASE_URL}/api/projects/${projectId}`, {
        ...authHeaders,
        tags: { name: 'project_detail' },
      })
      projectDetailDuration.add(Date.now() - startedDetail)
      totalRequestsCounter.add(1)
      readOperationsCounter.add(1)

      const detailOk = check(detailRes, {
        'project detail status is 200': (r) => r.status === 200,
      })
      errorRate.add(!detailOk)
      businessSuccessRate.add(detailOk)

      // 3. 项目任务与文档关联查询
      http.get(`${BASE_URL}/api/projects/${projectId}/tasks`, { ...authHeaders, tags: { name: 'project_tasks' } })
      http.get(`${BASE_URL}/api/projects/${projectId}/documents`, { ...authHeaders, tags: { name: 'project_docs' } })
      totalRequestsCounter.add(2)
      readOperationsCounter.add(2)
    }
  })
}

function scenarioKnowledgeAndDashboard(authHeaders) {
  group('知识库与分析大屏', () => {
    // 1. 知识库案例检索
    const kw = randomItem(KEYWORDS)
    const startedKb = Date.now()
    const kbRes = http.get(`${BASE_URL}/api/knowledge/cases?page=0&size=10&keyword=${encodeURIComponent(kw)}`, {
      ...authHeaders,
      tags: { name: 'knowledge_search' },
    })
    knowledgeSearchDuration.add(Date.now() - startedKb)
    totalRequestsCounter.add(1)
    readOperationsCounter.add(1)

    check(kbRes, { 'knowledge search ok': (r) => r.status === 200 })

    sleep(0.3)

    // 2. 数据分析大屏综合看板 (重 SQL 聚合接口)
    const startedDash = Date.now()
    const dashRes = http.get(`${BASE_URL}/api/analytics/overview`, {
      ...authHeaders,
      tags: { name: 'analytics_overview' },
    })
    dashboardDuration.add(Date.now() - startedDash)
    totalRequestsCounter.add(1)
    readOperationsCounter.add(1)

    const dashOk = check(dashRes, {
      'analytics overview status is 200': (r) => r.status === 200,
    })
    errorRate.add(!dashOk)
    businessSuccessRate.add(dashOk)

    // 3. 产品线统计
    const prodRes = http.get(`${BASE_URL}/api/analytics/product-lines`, { ...authHeaders, tags: { name: 'analytics_product_lines' } })
    totalRequestsCounter.add(1)
    readOperationsCounter.add(1)
    check(prodRes, { 'product-lines ok': (r) => r.status === 200 })
  })
}

const HQ_REGIONS = ['上海市上海市', '北京市北京市', '广东省深圳市', '广东省广州市', '四川省成都市', '江苏省南京市', '浙江省杭州市', '湖北省武汉市']

function scenarioWriteOperations(authHeaders, user) {
  group('受控业务数据写入', () => {
    const region = randomItem(HQ_REGIONS)
    const industry = randomItem(INDUSTRIES)
    const randomTag = `${__VU}_${Date.now()}_${randomInt(100, 999)}`
    
    const payload = JSON.stringify({
      title: `[压测动态] ${industry}采购项目-${randomTag}`,
      budget: randomInt(10, 500) * 10000.0,
      region: region,
      industry: industry,
      purchaserName: `上海智能制造发展中心-${randomTag}`,
      source: '人工录入',
      status: 'PENDING_ASSIGNMENT',
      customerType: '国企',
      priority: 'B',
      deadline: '2026-11-30T17:00:00',
      description: 'k6 压力测试自动生成的受控测试标讯数据',
    })

    const started = Date.now()
    const res = http.post(`${BASE_URL}/api/tenders`, payload, {
      ...authHeaders,
      tags: { name: 'tender_create' },
    })
    tenderCreateDuration.add(Date.now() - started)
    totalRequestsCounter.add(1)
    writeOperationsCounter.add(1)

    const ok = check(res, {
      'tender create accepted (200/201)': (r) => r.status === 200 || r.status === 201,
    })
    errorRate.add(!ok)
    businessSuccessRate.add(ok)
  })
}
