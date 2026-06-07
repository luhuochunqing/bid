// Input: primitive display values
// Output: pure Workbench display formatters and UI token mappings
// Pos: src/views/Dashboard/ - Dashboard pure core helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

export function formatCurrency(value) {
  if (!value && value !== 0) return '--'
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return '--'
  const wan = numeric / 10000
  return wan >= 1 ? `¥${wan.toFixed(0)}万` : `¥${numeric.toFixed(0)}`
}

export function formatPercent(value) {
  if (value === undefined || value === null) return '--'
  const numeric = Number(value)
  return Number.isFinite(numeric) ? `${numeric.toFixed(1)}%` : '--'
}

export function formatCount(value, suffix = '') {
  return value !== undefined && value !== null ? `${value}${suffix}` : '--'
}

export function getTimeGreeting(hour = new Date().getHours()) {
  if (hour >= 5 && hour <= 11) return '上午好'
  if (hour >= 12 && hour <= 17) return '下午好'
  return '晚上好'
}

export function formatCurrentDate(date = new Date()) {
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })
}

export function formatTodoDeadline(dueDate) {
  if (!dueDate) return '待排期'
  return new Date(dueDate).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

export function formatRelativeTime(time, now = new Date()) {
  const date = new Date(time)
  const diff = now - date
  if (!Number.isFinite(diff)) return ''
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return `${Math.floor(diff / 86400000)}天前`
}

export function getProgressColor(progress) {
  if (progress >= 80) return '#059669'
  if (progress >= 50) return '#3B82F6'
  if (progress >= 20) return '#F59E0B'
  return '#EF4444'
}

export function getProjectStatusType(status) {
  return { '编制中': 'warning', '评审中': 'primary', '即将开标': 'danger' }[status] || ''
}

export function getPriorityType(priority) {
  return { high: 'danger', medium: 'warning', low: 'info' }[priority] || ''
}

export function getPriorityLabel(priority) {
  return { high: '高', medium: '中', low: '低' }[priority] || priority
}
