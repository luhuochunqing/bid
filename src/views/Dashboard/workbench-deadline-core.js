// Input: WorkbenchDeadlineStatsDTO from API, user menuPermissions
// Output: pure deadline metric transforms, permission-driven metric selection
// Pos: src/views/Dashboard/ - Dashboard pure core helpers

import { hasAnyPermission } from '@/utils/permission'
import { ROLE_CODES } from '@/constants/roleCodes'

/**
 * Normalize raw API deadline stats response into clean object.
 */
export function normalizeDeadlineStats(raw = {}) {
  const reg = raw.registrationDeadline || {}
  const opening = raw.bidOpening || {}
  const deposit = raw.depositDeadline || {}
  return {
    registrationDeadline: {
      todayCount: Number(reg.todayCount) || 0,
      weekCount: Number(reg.weekCount) || 0,
      monthCount: Number(reg.monthCount) || 0,
    },
    bidOpening: {
      todayCount: Number(opening.todayCount) || 0,
      weekCount: Number(opening.weekCount) || 0,
      monthCount: Number(opening.monthCount) || 0,
    },
    depositDeadline: {
      todayCount: Number(deposit.todayCount) || 0,
      weekCount: Number(deposit.weekCount) || 0,
      monthCount: Number(deposit.monthCount) || 0,
    },
  }
}

// Permission-driven metric definitions
// key 统一使用角色码（含连字符的用方括号访问）
const DEADLINE_METRIC_DEFS = {
  // analytics permission → admin-level: 4 cards
  admin: [
    { key: 'reg_today', label: '今日报名截止', deadlineType: 'registrationDeadline', period: 'todayCount', icon: 'Document', variant: 'red' },
    { key: 'opening_week', label: '本周开标', deadlineType: 'bidOpening', period: 'weekCount', icon: 'Flag', variant: 'amber' },
    { key: 'deposit_month', label: '本月保证金截止', deadlineType: 'depositDeadline', period: 'monthCount', icon: 'TrendCharts', variant: 'blue' },
    { key: 'reg_month', label: '本月报名截止', deadlineType: 'registrationDeadline', period: 'monthCount', icon: 'Briefcase', variant: 'green' },
  ],
  // project permission → team-level: 3 cards
  manager: [
    { key: 'reg_week', label: '本周报名截止', deadlineType: 'registrationDeadline', period: 'weekCount', icon: 'Document', variant: 'red' },
    { key: 'opening_today', label: '今日开标', deadlineType: 'bidOpening', period: 'todayCount', icon: 'Flag', variant: 'amber' },
    { key: 'deposit_week', label: '本周保证金截止', deadlineType: 'depositDeadline', period: 'weekCount', icon: 'TrendCharts', variant: 'blue' },
  ],
  // default → personal: 3 cards
  [ROLE_CODES.BID_SPECIALIST]: [
    { key: 'reg_today', label: '今日报名截止', deadlineType: 'registrationDeadline', period: 'todayCount', icon: 'Document', variant: 'red' },
    { key: 'opening_week', label: '本周开标', deadlineType: 'bidOpening', period: 'weekCount', icon: 'Flag', variant: 'amber' },
    { key: 'deposit_month', label: '本月保证金截止', deadlineType: 'depositDeadline', period: 'monthCount', icon: 'TrendCharts', variant: 'blue' },
  ],
}

const METRIC_STYLE = {
  green: { iconBg: 'linear-gradient(135deg, #D1FAE5 0%, #A7F3D0 100%)', iconColor: '#059669' },
  amber: { iconBg: 'linear-gradient(135deg, #FEF3C7 0%, #FDE68A 100%)', iconColor: '#D97706' },
  blue: { iconBg: 'linear-gradient(135deg, #DBEAFE 0%, #BFDBFE 100%)', iconColor: '#1E40AF' },
  red: { iconBg: 'linear-gradient(135deg, #FEE2E2 0%, #FECACA 100%)', iconColor: '#DC2626' },
}

/**
 * Select deadline metrics based on user's menuPermissions.
 * analytics → admin-level (4 cards), project → team-level (3 cards), default → personal (3 cards)
 *
 * Pure-core defensive: deadlineStats may be null/undefined/{}; we never throw.
 */
export function selectDeadlineMetrics(menuPermissions, deadlineStats) {
  const safeStats = deadlineStats || {}
  if (hasAnyAnalyticsAccess(menuPermissions)) {
    return buildMetrics(DEADLINE_METRIC_DEFS.admin, safeStats)
  }
  if (hasAnyProjectAccess(menuPermissions)) {
    return buildMetrics(DEADLINE_METRIC_DEFS.manager, safeStats)
  }
  return buildMetrics(DEADLINE_METRIC_DEFS[ROLE_CODES.BID_SPECIALIST], safeStats)
}

function hasAnyAnalyticsAccess(perms) {
  return hasAnyPermission(perms, ['analytics'])
}

function hasAnyProjectAccess(perms) {
  return hasAnyPermission(perms, ['project'])
}

function buildMetrics(defs, deadlineStats) {
  return defs.map((def) => {
    const typeStats = deadlineStats[def.deadlineType] || {}
    return {
      key: def.key,
      label: def.label,
      value: String(typeStats[def.period] || 0),
      icon: def.icon,
      variant: def.variant,
      change: '--',
      changeClass: 'neutral',
      deadlineType: def.deadlineType,
      period: def.period,
      ...METRIC_STYLE[def.variant],
    }
  })
}

// ==================== CO-593: Deadline items (list data) ====================

/**
 * Normalize raw API deadline items response into clean object.
 *
 * 后端 DTO: WorkbenchDeadlineItemsDTO { registrationDeadline, bidOpening, depositDeadline }
 * 每个条目: { id, name, date(yyyy-MM-dd), targetId, targetType('tender'|'project') }
 */
export function normalizeDeadlineItems(raw = {}) {
  return {
    // 报名截止/开标来源 Tender 表（存在重复记录风险），做防御性去重
    registrationDeadline: normalizeDedupList(raw.registrationDeadline),
    bidOpening: normalizeDedupList(raw.bidOpening),
    // 保证金来源 Fee 表（非 Tender），后端不参与去重，前端保持原样避免不对称；
    // 且按项目名去重可能误并"同一项目同日期的多笔保证金"，故不去重
    depositDeadline: normalizeItemList(raw.depositDeadline),
  }
}

function normalizeItemList(list) {
  if (!Array.isArray(list)) return []
  return list.map((item) => ({
    id: item?.id ?? null,
    name: String(item?.name ?? ''),
    date: String(item?.date ?? ''),
    targetId: item?.targetId ?? null,
    targetType: item?.targetType === 'tender' ? 'tender' : 'project',
  }))
}

/**
 * 双重保险去重：按 (date + name) 业务键去重，保留首次出现的条目。
 *
 * 注意：业务键刻意不含 id —— 重复 Tender 是"同一标讯被推两遍"产生的不同 id 行，
 * 若键含 id 则完全无法去重。副作用是同标题同日期的不同标讯可能被误并，
 * 这是展示层防御的取舍，根治走数据清理 + 去重策略加固（见 lessons-learned §109 follow-up）。
 */
function normalizeDedupList(list) {
  if (!Array.isArray(list)) return []
  const items = normalizeItemList(list)
  const dedupMap = new Map()
  for (const item of items) {
    const key = `${item.date}|${item.name.trim()}`
    if (!dedupMap.has(key)) {
      dedupMap.set(key, item)
    }
  }
  return Array.from(dedupMap.values())
}

/**
 * Build deadline panels object for DeadlinePanels.vue from normalized API items.
 *
 * 后端字段 → UI 列 key 映射：
 * - registrationDeadline → signup（报名截止，红色 dot）
 * - bidOpening → opening（开标时间，绿色 dot）
 * - depositDeadline → deposit（保证金截止，黄色 dot）
 *
 * @param {object} items normalizeDeadlineItems 返回值
 * @returns {{signup: Array, opening: Array, deposit: Array}}
 */
export function buildDeadlinePanelsFromItems(items = {}) {
  const safe = items || {}
  return {
    signup: Array.isArray(safe.registrationDeadline) ? safe.registrationDeadline : [],
    opening: Array.isArray(safe.bidOpening) ? safe.bidOpening : [],
    deposit: Array.isArray(safe.depositDeadline) ? safe.depositDeadline : [],
  }
}
