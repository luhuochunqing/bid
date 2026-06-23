// Input: Workbench role context and metric keys
// Output: pure role metrics, banner copy, actions, and route targets
// Pos: src/views/Dashboard/ - Dashboard pure core helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import {
  formatCount,
  formatCurrency,
  formatCurrentDate,
  formatPercent,
  getTimeGreeting,
} from '@/views/Dashboard/workbench-formatters.js'

const METRIC_STYLE = {
  green: { iconBg: 'linear-gradient(135deg, #D1FAE5 0%, #A7F3D0 100%)', iconColor: '#059669' },
  amber: { iconBg: 'linear-gradient(135deg, #FEF3C7 0%, #FDE68A 100%)', iconColor: '#D97706' },
  blue: { iconBg: 'linear-gradient(135deg, #DBEAFE 0%, #BFDBFE 100%)', iconColor: '#1E40AF' },
  red: { iconBg: 'linear-gradient(135deg, #FEE2E2 0%, #FECACA 100%)', iconColor: '#DC2626' },
}

const METRIC_ROUTE_TARGETS = {
  totalRevenue: { path: '/analytics/dashboard', query: { drilldown: 'revenue' } },
  winRate: { path: '/analytics/dashboard', query: { drilldown: 'win-rate' } },
  teamSize: { path: '/analytics/dashboard', query: { drilldown: 'team' } },
  activeProjects: { path: '/analytics/dashboard', query: { drilldown: 'projects', status: 'in_progress' } },
  newTenders: '/bidding',
  myOpportunities: '/bidding',
  customerVisits: '/project',
  pendingProposals: '/project/create',
  myProjects: '/project',
  urgentTasks: '/project',
  teamWorkload: '/project',
  resourceStatus: '/settings',
  myTasks: '/project',
  completedThisWeek: '/project',
  pendingReviews: '/project',
  workHours: '/project',
}

const roleMetricDefinitions = {
  admin: [
    ['totalRevenue', '年度中标金额', 'TrendCharts', 'green', (ctx) => (ctx.summaryStats ? formatCurrency(ctx.summaryStats.totalBudget) : '--')],
    ['winRate', '整体中标率', 'Flag', 'amber', (ctx) => (ctx.summaryStats ? formatPercent(ctx.summaryStats.successRate) : '--')],
    ['totalTenders', '总标讯数', 'Document', 'blue', (ctx) => (ctx.summaryStats ? formatCount(ctx.summaryStats.totalTenders, '条') : '--')],
    ['activeProjects', '进行中项目', 'Briefcase', 'red', (ctx) => (ctx.summaryStats ? formatCount(ctx.summaryStats.activeProjects, '个') : '--')],
  ],
  manager: [
    ['myProjects', '负责项目', 'Briefcase', 'blue', (ctx) => formatCount(ctx.myProjectCount, '个')],
    ['urgentTasks', '待处理任务', 'Flag', 'red', (ctx) => formatCount(ctx.pendingCount, '项')],
    ['pendingApprovals', '待审批', 'TrendCharts', 'amber', (ctx) => formatCount(ctx.pendingApprovalsTotalCount, '项')],
    ['activeProjects', '进行中项目', 'User', 'green', (ctx) => (ctx.summaryStats ? formatCount(ctx.summaryStats.activeProjects, '个') : '--')],
  ],
  'bid-Team': [
    ['myTasks', '我的任务', 'Document', 'blue', (ctx) => formatCount(ctx.pendingCount, '项')],
    ['completedThisWeek', '已完成', 'Check', 'green', (ctx) => formatCount(ctx.completedTodoCount, '项')],
    ['pendingReviews', '待审批', 'Flag', 'red', (ctx) => formatCount(ctx.pendingApprovalsTotalCount, '项')],
    ['activeProjects', '参与项目', 'Briefcase', 'amber', (ctx) => formatCount(ctx.myProjectCount, '个')],
  ],
}

function makeMetric([key, label, icon, variant, valueGetter], context) {
  return { key, label, value: valueGetter(context), icon, ...METRIC_STYLE[variant], change: '--', changeClass: 'neutral', variant }
}

export function getRoleMetrics(role, context = {}) {
  const definitions = roleMetricDefinitions[role] || roleMetricDefinitions['bid-Team']
  const safeContext = { summaryStats: null, myProjectCount: 0, pendingCount: 0, pendingApprovalsTotalCount: 0, completedTodoCount: 0, ...context }
  return definitions.map((definition) => makeMetric(definition, safeContext))
}

export function getBannerTitle(userName = '用户', hour) {
  return `${getTimeGreeting(hour)}，${userName || '用户'}`
}

export function getBannerSubtitle(role, context = {}) {
  const today = context.currentDate || formatCurrentDate()
  const summary = context.summaryStats
  if (role === 'admin') {
    const projects = summary ? summary.activeProjects : '--'
    return `今天是${today}，团队有${projects}个进行中的项目，${context.pendingApprovalsTotalCount || 0}个待审批事项`
  }
  if (role === 'manager') {
    return `今天是${today}，您负责${context.myProjectCount || 0}个项目，${context.pendingCount || 0}项待处理任务`
  }
  return `今天是${today}，您有${context.pendingCount || 0}项待处理任务`
}

export function getBannerActionConfig(role) {
  if (role === 'admin') {
    return [
      { key: 'report', label: '业绩报表', type: 'primary', icon: 'DataAnalysis', target: '/analytics/dashboard' },
      { key: 'team', label: '团队管理', type: 'default', icon: 'User', target: '/settings' },
    ]
  }
  if (role === 'manager') {
    return [
      { key: 'projects', label: '我的项目', type: 'primary', icon: 'Briefcase', target: '/project' },
      { key: 'tenders', label: '查看标讯', type: 'default', icon: 'Document', target: '/bidding' },
    ]
  }
  return [
    { key: 'tasks', label: '我的任务', type: 'primary', icon: 'Document', target: '/project' },
    { key: 'calendar', label: '日程', type: 'default', icon: 'Calendar', target: '/dashboard?tab=schedule' },
  ]
}

export function filterProjectsByRole(projects, { role = 'bid-Team', userName = '', limit = 3 } = {}) {
  const source = Array.isArray(projects) ? projects : []
  const activeProjects = source.filter(isActiveProject)
  const sorted = [...activeProjects].sort((left, right) => {
    const leftRank = getWorkbenchProjectRank(left, { role, userName })
    const rightRank = getWorkbenchProjectRank(right, { role, userName })
    return leftRank - rightRank
  })
  return sorted.slice(0, limit)
}

const TERMINAL_PROJECT_STATUSES = new Set(['已中标', '未中标', '已流标', '已放弃'])
const PRIORITY_RANK = { high: 0, medium: 1, low: 2, urgent: 3 }

function isActiveProject(project) {
  if (!project) return false
  return !TERMINAL_PROJECT_STATUSES.has(String(project.status || '').trim())
}

function getWorkbenchProjectRank(project, { role, userName }) {
  const ownerRank = role === 'manager' && project?.manager === userName ? 0 : 1
  const priorityRank = PRIORITY_RANK[project?.priority] ?? PRIORITY_RANK.low
  return ownerRank * 10 + priorityRank
}

export function getMetricRouteTarget(metricOrKey) {
  const key = typeof metricOrKey === 'string' ? metricOrKey : metricOrKey?.key
  return METRIC_ROUTE_TARGETS[key]
}

export const getWorkbenchMetrics = getRoleMetrics
export const getRoleBannerActions = getBannerActionConfig
