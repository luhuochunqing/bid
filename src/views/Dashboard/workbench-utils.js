// Input: hour (optional integer 0-23)
// Output: dashboard normalization helpers and getTimeGreeting
// Pos: src/views/Dashboard/ - Dashboard view utilities
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

const PROJECT_STATUS_META = {
  PENDING_INITIATION: { status: '待立项', progress: 5 },
  INITIATED: { status: '已立项', progress: 10 },
  BIDDING: { status: '投标中', progress: 95 },
  EVALUATING: { status: '评标中', progress: 75 },
  WON: { status: '已中标', progress: 100 },
  LOST: { status: '未中标', progress: 100 },
  FAILED: { status: '已流标', progress: 100 },
  ABANDONED: { status: '已放弃', progress: 100 },
}

const CALENDAR_EVENT_TYPES = {
  DEADLINE: 'deadline',
  SUBMISSION: 'bid',
  REVIEW: 'review',
  MEETING: 'review',
  MILESTONE: 'milestone',
  REMINDER: 'reminder',
}

const ALERT_PRIORITY_MAP = {
  CRITICAL: 'urgent',
  HIGH: 'high',
  MEDIUM: 'medium',
  LOW: 'low',
}

function toDateOnly(value) {
  return typeof value === 'string' ? value.slice(0, 10) : undefined
}

function resolveProjectPriority(endDate) {
  if (!endDate) return 'low'
  const target = new Date(endDate)
  if (Number.isNaN(target.getTime())) return 'low'
  const diffMs = target.getTime() - Date.now()
  const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24))
  if (diffDays <= 7) return 'high'
  if (diffDays <= 30) return 'medium'
  return 'low'
}

export function normalizeProjectForWorkbench(project) {
  if (!project) {
    return {
      id: undefined,
      name: '',
      status: '',
      progress: 0,
      deadline: undefined,
      manager: '',
      priority: 'low',
    }
  }

  const meta = PROJECT_STATUS_META[project.status] || { status: project.status || '', progress: 0 }
  return {
    id: project.id,
    name: project.name || '',
    status: meta.status,
    progress: meta.progress,
    deadline: project.endDate,
    manager: project.managerName || project.customerManager || (project.managerId ? `负责人#${project.managerId}` : ''),
    priority: resolveProjectPriority(project.endDate),
  }
}

export function normalizeCalendarEvent(event) {
  if (!event) {
    return {
      id: undefined,
      projectId: null,
      date: undefined,
      eventType: 'REMINDER',
      type: 'reminder',
      title: '',
      project: '',
      urgent: false,
      description: '',
    }
  }

  return {
    id: event.id,
    projectId: event.projectId || null,
    date: event.eventDate,
    eventType: event.eventType || 'REMINDER',
    type: CALENDAR_EVENT_TYPES[event.eventType] || 'reminder',
    title: event.title || '',
    project: event.title || '',
    urgent: Boolean(event.isUrgent),
    description: event.description || '',
  }
}

export function normalizeAlertForTodo(alert) {
  const normalizedLevel = alert?.severity || alert?.level
  const normalizedStatus = String(alert?.status || '').toUpperCase()
  return {
    id: `alert-${alert?.id}`,
    sourceId: alert?.id,
    title: alert?.message || '',
    priority: ALERT_PRIORITY_MAP[normalizedLevel] || 'low',
    type: 'warning',
    done: normalizedStatus === 'RESOLVED' || Boolean(alert?.resolved),
    deadline: toDateOnly(alert?.createdAt),
    sourceType: 'alert',
  }
}

export function extractCustomersFromProjects(projects) {
  if (!Array.isArray(projects) || projects.length === 0) {
    return []
  }

  const grouped = new Map()
  projects.forEach((project) => {
    if (!project?.customerManager) return
    const key = project.customerManagerId
    const current = grouped.get(key) || {
      id: key,
      name: project.customerManager,
      company: '',
      projectCount: 0,
      statuses: [],
    }
    current.projectCount += 1
    current.statuses.push(project.status)
    grouped.set(key, current)
  })

  return Array.from(grouped.values()).map((customer) => {
    const statuses = customer.statuses
    const hasFollowing = statuses.some((status) => status === 'BIDDING' || status === 'EVALUATING')
    const allTerminal = statuses.length > 0 && statuses.every((status) => ['WON', 'LOST', 'FAILED', 'ABANDONED'].includes(status))
    return {
      id: customer.id,
      name: customer.name,
      company: customer.company,
      projectCount: customer.projectCount,
      status: hasFollowing ? '跟进中' : allTerminal ? '已完成' : '新客户',
      statusType: hasFollowing ? 'warning' : allTerminal ? 'success' : 'info',
    }
  })
}

export function getTimeGreeting(hour) {
  if (hour === undefined || hour === null) hour = new Date().getHours()
  if (hour >= 5 && hour <= 11) return '上午好'
  if (hour >= 12 && hour <= 17) return '下午好'
  return '晚上好'
}
