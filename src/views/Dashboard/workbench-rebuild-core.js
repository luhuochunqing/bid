// Input: schedule events / taskTodos / tenderTodos / projectTodos / resourceTodos / role / userId / deadlineStats
// Output: 纯核心派生函数（工作台改造）—— 截止时间分类、倒计时、待办分类卡片、欢迎横幅统计
// Pos: src/views/Dashboard/ - Dashboard 纯核心 helpers（可单测、不依赖框架）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import {
  isBidAdminOrLeadRole, isBidTeamRole, isSalesRole,
} from '@/constants/roleCodes.js'

const ONE_DAY_MS = 24 * 60 * 60 * 1000

/**
 * 按标题关键词把日历事件分类到报名截止 / 开标 / 保证金截止。
 * 用于截止时间区块的 3 列分组（schedule-overview events 无 3 分类字段，靠 title 派生）。
 * @returns {'signup'|'opening'|'deposit'|null}
 */
export function classifyDeadlineEvent(event) {
  if (!event || typeof event.title !== 'string') return null
  const title = event.title
  if (title.includes('保证金')) return 'deposit'
  if (title.includes('开标')) return 'opening'
  if (title.includes('报名')) return 'signup'
  return null
}

/**
 * 把日期字符串（YYYY-MM-DD 或带时间）解析为 Date，无效返回 null。
 */
function parseDate(value) {
  if (!value) return null
  const date = new Date(typeof value === 'string' ? value.slice(0, 10) : value)
  return Number.isNaN(date.getTime()) ? null : date
}

/**
 * 计算倒计时文本与样式类。
 * @param {string|Date} targetDate - 目标日期
 * @param {Date} today - 当前日期基准（便于测试）
 * @returns {{text: string, cls: 'urgent'|'warn'|'ok'}}
 */
export function formatCountdown(targetDate, today = new Date()) {
  const target = parseDate(targetDate)
  if (!target) return { text: '--', cls: 'ok' }

  const base = new Date(today)
  base.setHours(0, 0, 0, 0)
  const targetBase = new Date(target)
  targetBase.setHours(0, 0, 0, 0)

  const diffDays = Math.round((targetBase.getTime() - base.getTime()) / ONE_DAY_MS)

  if (diffDays < 0) return { text: '已过期', cls: 'ok' }
  if (diffDays === 0) return { text: '今天', cls: 'urgent' }
  if (diffDays === 1) return { text: '明天', cls: 'urgent' }
  if (diffDays === 2) return { text: '后天', cls: 'urgent' }
  if (diffDays <= 7) return { text: `${diffDays} 天后`, cls: 'warn' }
  return { text: `${diffDays} 天后`, cls: 'ok' }
}

function formatDateShort(value) {
  const date = parseDate(value)
  if (!date) return '--'
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${m}-${d}`
}

function withinPeriod(eventDate, period, today) {
  const target = parseDate(eventDate)
  if (!target) return false
  const base = new Date(today)
  base.setHours(0, 0, 0, 0)
  const targetBase = new Date(target)
  targetBase.setHours(0, 0, 0, 0)
  const diffDays = Math.round((targetBase.getTime() - base.getTime()) / ONE_DAY_MS)
  if (diffDays < 0) return false
  if (period === 'today') return diffDays === 0
  if (period === 'week') return diffDays <= 7
  if (period === 'month') return diffDays <= 30
  return false
}

/**
 * 从日历事件派生截止时间三列数据（报名截止 / 开标时间 / 保证金截止）。
 * 按 period（today/week/month）过滤，按 title 关键词分类。
 * @returns {{signup: Array, opening: Array, deposit: Array}}
 */
export function buildDeadlinePanels(events, period = 'week', today = new Date()) {
  const safeEvents = Array.isArray(events) ? events : []
  const buckets = { signup: [], opening: [], deposit: [] }

  safeEvents.forEach((event) => {
    if (!withinPeriod(event.date, period, today)) return
    const category = classifyDeadlineEvent(event)
    if (!category) return
    const countdown = formatCountdown(event.date, today)
    buckets[category].push({
      name: event.title || '',
      date: formatDateShort(event.date),
      countdown: countdown.text,
      countdownCls: countdown.cls,
      projectId: event.projectId ?? null,
    })
  })

  return buckets
}

const MAX_CARD_ITEMS = 4

/**
 * 构建任务待办卡片条目（保留 projectId 用于跳转项目详情标书制作阶段）。
 */
function buildTodoItems(todos) {
  const safe = Array.isArray(todos) ? todos.filter((t) => !t?.done) : []
  return safe.slice(0, MAX_CARD_ITEMS).map((todo) => ({
    id: todo.id,
    name: todo.title || todo.name || '',
    rightText: formatDateShort(todo.deadline) || '',
    projectId: todo.projectId ?? null,
  }))
}

/**
 * 构建标讯待办卡片条目（保留 projectId 用于关联项目跳转）。
 */
function buildTenderItems(tenders) {
  const safe = Array.isArray(tenders) ? tenders : []
  return safe.slice(0, MAX_CARD_ITEMS).map((tender) => ({
    id: tender.id,
    name: tender.title || tender.name || '',
    rightText: formatDateShort(tender.registrationDeadline) || '报名',
    projectId: tender.projectId ?? null,
  }))
}

/**
 * 构建项目待办卡片条目（保留 stage 用于阶段定位）。
 */
function buildProjectItems(projects) {
  const safe = Array.isArray(projects) ? projects : []
  return safe.slice(0, MAX_CARD_ITEMS).map((project) => ({
    id: project.id,
    name: project.name || '',
    rightText: project.status || project.stage || '',
    projectId: project.id,
    stage: project.stage || null,
  }))
}

/**
 * 构建资源待办卡片条目（适配 ResourcePendingApprovalDTO）。
 * DTO 字段：applicationType("ACCOUNT"/"CA")、applicationId、resourceLabel、
 *           applicantName、purpose、projectId、projectName、createdAt。
 */
function buildApprovalItems(approvals) {
  const safe = Array.isArray(approvals) ? approvals : []
  return safe.slice(0, MAX_CARD_ITEMS).map((approval) => ({
    id: approval.applicationId ?? approval.id,
    name: approval.resourceLabel || approval.purpose || approval.projectName || '',
    rightText: approval.applicantName || approval.applicationType || '',
    applicationType: approval.applicationType || null,
    projectId: approval.projectId ?? null,
  }))
}

/**
 * 从四类数据源按角色构建待办模块卡片。
 * - task 卡片：所有角色都显示
 * - tender 卡片：admin_lead（投标管理员/组长）+ sales（项目负责人）显示
 * - project 卡片：admin_lead + bid_team（投标专员）+ sales 显示
 * - resource 卡片：所有角色都显示
 *
 * @param {object} opts
 * @param {string} opts.role 当前用户角色码
 * @param {*} opts.userId 当前用户 ID（预留）
 * @param {Array} opts.taskTodos 标书制作阶段任务
 * @param {Array} opts.tenderTodos 按角色过滤的标讯
 * @param {Array} opts.projectTodos 按角色过滤的项目
 * @param {Array} opts.resourceTodos 待审批申请
 * @returns {Array<{key, title, count, accent, items}>}
 */
export function buildTodoCategoryCards({
  role = '',
  userId,
  taskTodos = [],
  tenderTodos = [],
  projectTodos = [],
  resourceTodos = [],
} = {}) {
  const showTender = isBidAdminOrLeadRole(role) || isSalesRole(role)
  const showProject = isBidAdminOrLeadRole(role) || isBidTeamRole(role) || isSalesRole(role)

  const cards = []

  cards.push({
    key: 'task',
    title: '任务·待办',
    accent: 'primary',
    count: (Array.isArray(taskTodos) ? taskTodos.filter((t) => !t?.done) : []).length,
    items: buildTodoItems(taskTodos),
  })

  if (showTender) {
    cards.push({
      key: 'tender',
      title: '标讯·待办',
      accent: 'warning',
      count: Array.isArray(tenderTodos) ? tenderTodos.length : 0,
      items: buildTenderItems(tenderTodos),
    })
  }

  if (showProject) {
    cards.push({
      key: 'project',
      title: '项目·待办',
      accent: 'success',
      count: Array.isArray(projectTodos) ? projectTodos.length : 0,
      items: buildProjectItems(projectTodos),
    })
  }

  cards.push({
    key: 'resource',
    title: '资源·待办',
    accent: 'info',
    count: Array.isArray(resourceTodos) ? resourceTodos.length : 0,
    items: buildApprovalItems(resourceTodos),
  })

  return cards
}

/**
 * 构建欢迎横幅右侧 4 个统计数字。
 * @returns {Array<{label, value}>}
 */
export function buildWelcomeStats({
  pendingCount = 0,
  myProjectCount = 0,
  deadlineStats = null,
} = {}) {
  const safeStats = deadlineStats || {}
  const reg = safeStats.registrationDeadline || {}
  const opening = safeStats.bidOpening || {}
  return [
    { label: '待办任务', value: Number(pendingCount) || 0 },
    { label: '待办项目', value: Number(myProjectCount) || 0 },
    { label: '报名截止', value: Number(reg.todayCount) || 0 },
    { label: '今日开标', value: Number(opening.todayCount) || 0 },
  ]
}
