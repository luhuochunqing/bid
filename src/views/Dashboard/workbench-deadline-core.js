// Input: WorkbenchDeadlineStatsDTO from API, user menuPermissions
// Output: pure deadline metric transforms, permission-driven metric selection
// Pos: src/views/Dashboard/ - Dashboard pure core helpers

import { hasAnyPermission } from '@/utils/permission'

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
  staff: [
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
  return buildMetrics(DEADLINE_METRIC_DEFS.staff, safeStats)
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
