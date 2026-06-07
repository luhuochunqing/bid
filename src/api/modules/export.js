// Input: Element Plus messaging and export task payloads from business modules
// Output: exportApi and export enums for frontend export workflows
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ElMessage } from 'element-plus'

export const ExportType = {
  TENDERS: 'tenders',
  QUALIFICATIONS: 'qualifications',
  DASHBOARD_OVERVIEW: 'dashboard_overview',
  DASHBOARD_DRILLDOWN: 'dashboard_drilldown',
}

export const ExportFormat = {
  XLSX: 'xlsx',
  CSV: 'csv',
}

export const ExportStatus = {
  PENDING: 'pending',
  SUCCESS: 'success',
  FAILED: 'failed',
}

export function triggerDownload(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

function normalizeRows(rows = []) {
  if (!Array.isArray(rows)) return []
  return rows.map((row) => {
    if (row && typeof row === 'object' && !Array.isArray(row)) {
      return row
    }
    return { value: row }
  })
}

function toCsv(rows = []) {
  const normalized = normalizeRows(rows)
  if (normalized.length === 0) {
    return '暂无可导出数据\n'
  }

  const headers = Array.from(new Set(normalized.flatMap((row) => Object.keys(row))))
  const escapeCell = (value) => {
    const text = value == null ? '' : String(value)
    if (/[",\n]/.test(text)) {
      return `"${text.replace(/"/g, '""')}"`
    }
    return text
  }

  const lines = [headers.join(',')]
  normalized.forEach((row) => {
    lines.push(headers.map((header) => escapeCell(row[header])).join(','))
  })
  return `${lines.join('\n')}\n`
}

export const exportApi = {
  async exportExcel(type, rows = [], filename = `${type}-${Date.now()}.csv`) {
    const csv = toCsv(rows)
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    triggerDownload(blob, filename)
    return { success: true, status: ExportStatus.SUCCESS }
  },
}

export function notifyExportSuccess(message = '导出成功') {
  ElMessage.success(message)
}

export default exportApi
