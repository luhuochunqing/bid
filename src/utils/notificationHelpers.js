// Input: notification item type / createdAt string / sourceEntity
// Output: shared icon map, route map, formatter, and safe navigation target
// Pos: src/utils/ - Shared notification UI helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import {
  Bell,
  Warning,
  Document,
  ChatDotRound,
  InfoFilled
} from '@element-plus/icons-vue'

export const NOTIFICATION_ICON_BY_TYPE = {
  DEADLINE: Warning,
  DOCUMENT_CHANGE: Document,
  MENTION: ChatDotRound,
  SYSTEM: InfoFilled,
  DEFAULT: Bell
}

export const NOTIFICATION_ENTITY_ROUTE_MAP = {
  PROJECT: '/project/',
  BIDDING: '/bidding/',
  TENDER: '/bidding/',
  DOCUMENT: '/document/editor/'
}

export const NOTIFICATION_TYPE_LABELS = {
  INFO: '通知',
  SYSTEM: '系统',
  MENTION: '提及',
  APPROVAL: '审批',
  DEADLINE: '截止',
  TASK_UPDATE: '任务',
  DOCUMENT_CHANGE: '文档'
}

export const getNotificationIcon = (type) =>
  NOTIFICATION_ICON_BY_TYPE[type] || NOTIFICATION_ICON_BY_TYPE.DEFAULT

export const getNotificationTypeLabel = (type) =>
  NOTIFICATION_TYPE_LABELS[type] || type

export const formatNotificationTime = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return ''
  const diffMs = Date.now() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin}分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour}小时前`
  const diffDay = Math.floor(diffHour / 24)
  if (diffDay === 1) return '昨天'
  if (diffDay < 7) return `${diffDay}天前`
  return date.toLocaleDateString('zh-CN')
}

export const resolveNotificationRoute = (item) => {
  if (item?.sourceEntityType === 'TASK') {
    const payload = parseNotificationPayload(item.payloadJson)
    const projectId = Number(payload.projectId)
    const taskId = Number(item.sourceEntityId)
    if (!Number.isFinite(projectId) || projectId <= 0) return null
    if (!Number.isFinite(taskId) || taskId <= 0) return null
    return `/project/${projectId}?taskId=${taskId}`
  }
  const prefix = NOTIFICATION_ENTITY_ROUTE_MAP[item?.sourceEntityType]
  if (!prefix || item?.sourceEntityId == null) return null
  const safeId = Number(item.sourceEntityId)
  if (!Number.isFinite(safeId) || safeId <= 0) return null
  return `${prefix}${safeId}`
}

export const parseNotificationPayload = (payloadJson) => {
  if (!payloadJson) return {}
  if (typeof payloadJson === 'object') return payloadJson
  try {
    return JSON.parse(payloadJson)
  } catch {
    return {}
  }
}

export const extractChanges = (notification) => {
  const payload = parseNotificationPayload(notification?.payloadJson)
  return Array.isArray(payload.changes) ? payload.changes : []
}

export const hasChangeDiff = (notification) =>
  extractChanges(notification).length > 0

const MENTION_TOKEN_REGEX = /@\[([^\]]+)\]\((\d+)\)/g
const MAX_PARSED_MENTIONS = 20

export const parseMentionContent = (raw) => {
  if (!raw || typeof raw !== 'string') {
    return { plainText: '', mentionedUserIds: [] }
  }
  const ids = []
  let match
  MENTION_TOKEN_REGEX.lastIndex = 0
  while ((match = MENTION_TOKEN_REGEX.exec(raw)) !== null) {
    const id = Number(match[2])
    if (Number.isFinite(id) && !ids.includes(id)) {
      ids.push(id)
      if (ids.length >= MAX_PARSED_MENTIONS) break
    }
  }
  const plainText = raw.replace(MENTION_TOKEN_REGEX, '@$1')
  return { plainText, mentionedUserIds: ids }
}
