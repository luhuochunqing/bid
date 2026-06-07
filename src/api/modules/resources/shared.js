// Input: shared resource API dependencies and raw backend payloads
// Output: reusable HTTP/page/date/id helpers for resource submodules
// Pos: src/api/modules/resources/ - Shared module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '@/api/client'

export { httpClient }

export function isNumericId(id) {
  return /^\d+$/.test(String(id))
}

export function invalidIdMessage(entityName) {
  return {
    success: false,
    message: `Current backend only supports numeric ${entityName} IDs in API mode`
  }
}

export function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toISOString().split('T')[0]
}

export function formatDateTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false })
}

export function pageContent(response) {
  const page = response?.data
  const content = Array.isArray(page?.content)
    ? page.content
    : Array.isArray(response?.data)
      ? response.data
      : []

  return {
    page,
    content
  }
}
