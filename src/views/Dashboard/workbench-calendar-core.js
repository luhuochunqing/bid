// Input: normalized calendar events, dates, and calendar filter state
// Output: pure Workbench calendar decorations and derivations
// Pos: src/views/Dashboard/ - Dashboard pure core helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

export const calendarFilters = [
  { label: '全部', value: 'all' },
  { label: '截止', value: 'deadline' },
  { label: '投标', value: 'bid' },
  { label: '开标', value: 'opening' },
  { label: '评审', value: 'review' },
  { label: '高风险', value: 'urgent' },
]

export const parseDate = (dateStr) => new Date(`${dateStr}T00:00:00`)

export const formatDateKey = (date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const getDaysUntil = (dateStr, today = new Date()) => {
  const oneDay = 24 * 60 * 60 * 1000
  return Math.round((parseDate(dateStr) - parseDate(formatDateKey(today))) / oneDay)
}

const eventMeta = {
  deadline: ['商务专员', '资料收口', '待确认最终材料', '去补材料'],
  bid: ['投标经理', '递交前确认', '待核对报价与盖章', '去检查递交'],
  opening: ['销售经理', '现场准备', '待确认授权与签到', '看开标准备'],
  review: ['技术负责人', '评审跟进', '待准备答疑材料', '看评审清单'],
}

export function decorateCalendarEvent(event, today = new Date()) {
  const diffDays = getDaysUntil(event.date, today)
  const isExpired = diffDays < 0
  const isCritical = event.urgent || diffDays <= 1
  const isWarning = !isCritical && diffDays <= 3
  const [owner = '项目负责人', stage = '节点处理中', blocker = '待补充信息', actionLabel = '查看详情'] = eventMeta[event.type] || []

  return {
    ...event,
    diffDays,
    countdownLabel: isExpired ? '已逾期' : diffDays === 0 ? '今天' : `D-${diffDays}`,
    riskLabel: isExpired ? '已逾期' : isCritical ? '高风险' : isWarning ? '需关注' : '常规',
    riskTagType: isExpired ? 'danger' : isCritical ? 'info' : isWarning ? 'warning' : 'info',
    priorityLevel: isExpired || isCritical ? 'priority-critical' : isWarning ? 'priority-warning' : 'priority-normal',
    actionLabel,
    dayLabel: event.date.slice(5),
    weekdayLabel: parseDate(event.date).toLocaleDateString('zh-CN', { weekday: 'short' }).replace('周', ''),
    fieldSummary: { owner: `负责人 ${owner}`, stage: `阶段 ${stage}`, blocker: `阻塞 ${blocker}` },
  }
}

export function filterCalendarEvents(events, activeFilter) {
  return events.filter((event) => {
    if (activeFilter === 'all') return true
    if (activeFilter === 'urgent') return event.urgent || event.priorityLevel === 'priority-critical'
    return event.type === activeFilter
  })
}

export function getCalendarEventsForDate(events, date) {
  const dateStr = formatDateKey(date)
  return events.filter((event) => event.date === dateStr)
}

export function resolveCalendarCellClass(events, { date, viewType }) {
  if (viewType !== 'month') return ''
  const dayEvents = getCalendarEventsForDate(events, date)
  if (dayEvents.length === 0) return ''
  if (dayEvents.some((event) => event.priorityLevel === 'priority-critical')) return 'calendar-day-urgent'
  if (dayEvents.length >= 3) return 'calendar-day-crowded'
  return 'calendar-day-has-event'
}

export function getEventTypeTag(type) {
  const map = {
    deadline: { type: 'danger', label: '截止' },
    bid: { type: 'primary', label: '投标' },
    opening: { type: 'success', label: '开标' },
    review: { type: 'warning', label: '评审' },
  }
  return map[type] || { type: 'info', label: '其他' }
}

export function formatSelectedDateLabel(dateKey, fallbackDate = new Date()) {
  return parseDate(dateKey || formatDateKey(fallbackDate)).toLocaleDateString('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  })
}

export function getMonthCalendarSummary(events, calendarDate) {
  const year = calendarDate.getFullYear()
  const month = calendarDate.getMonth()
  const monthEvents = events.filter((event) => {
    const eventDate = parseDate(event.date)
    return eventDate.getFullYear() === year && eventDate.getMonth() === month
  })
  const nextDeadline = monthEvents
    .filter((event) => event.type === 'deadline' && event.diffDays >= 0)
    .sort((a, b) => a.diffDays - b.diffDays)[0]

  return {
    total: monthEvents.length,
    urgent: monthEvents.filter((event) => event.urgent || event.priorityLevel === 'priority-critical').length,
    nextDeadlineLabel: nextDeadline ? nextDeadline.countdownLabel : '暂无',
  }
}

export function getUpcomingCalendarEvents(events) {
  return events
    .filter((event) => event.diffDays >= 0 && event.diffDays <= 7)
    .sort((a, b) => a.diffDays - b.diffDays || a.date.localeCompare(b.date))
}

export function getCalendarMonthKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}
